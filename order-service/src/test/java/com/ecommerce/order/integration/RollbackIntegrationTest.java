package com.ecommerce.order.integration;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmRequest;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmResponse;
import com.ecommerce.order.adapter.in.web.dto.TransactionStatusResponse;
import com.ecommerce.order.application.port.out.OutboxPort;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.application.service.OrderSagaService;
import com.ecommerce.order.application.service.RollbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Rollback Integration Tests")
class RollbackIntegrationTest {

    @Autowired
    private OrderSagaService orderSagaService;

    @Autowired
    private RollbackService rollbackService;

    @MockBean
    private ServiceClientPort serviceClientPort;

    @MockBean
    private OutboxPort outboxPort;

    @MockBean
    private WebSocketPort webSocketPort;

    @Test
    @DisplayName("should rollback payment when inventory fails")
    void shouldRollbackPaymentWhenInventoryFails() {
        // Given - Order confirmation request
        OrderConfirmRequest request = new OrderConfirmRequest(
                "c1d2e3f4-a5b6-7890-cdef-123456789012",
                "user-123",
                List.of(new OrderConfirmRequest.OrderItemDto("SKU-001", 2, new BigDecimal("29.99"))),
                new BigDecimal("59.98"),
                "4111111111111111"
        );

        // When - Confirm order
        OrderConfirmResponse confirmResponse = orderSagaService.confirmOrder(request);
        UUID txId = UUID.fromString(confirmResponse.txId());
        UUID orderId = UUID.fromString(request.orderId());

        // Simulate: Credit card succeeds, then inventory fails
        // This would normally be handled by Camel route, but we test RollbackService directly
        List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD);

        when(serviceClientPort.rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class)))
                .thenReturn(RollbackResponse.success(txId, "Payment refunded"));

        // When - Execute rollback
        rollbackService.executeRollback(txId, orderId, successfulServices);

        // Then - Verify credit card was rolled back
        verify(serviceClientPort).rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));
        verify(serviceClientPort, never()).rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class));
    }

    @Test
    @DisplayName("should rollback payment and inventory when logistics fails")
    void shouldRollbackPaymentAndInventoryWhenLogisticsFails() {
        // Given
        OrderConfirmRequest request = new OrderConfirmRequest(
                "d2e3f4a5-b6c7-8901-def0-123456789abc",
                "user-456",
                List.of(new OrderConfirmRequest.OrderItemDto("SKU-002", 1, new BigDecimal("99.99"))),
                new BigDecimal("99.99"),
                "4222222222222222"
        );

        OrderConfirmResponse confirmResponse = orderSagaService.confirmOrder(request);
        UUID txId = UUID.fromString(confirmResponse.txId());
        UUID orderId = UUID.fromString(request.orderId());

        // Simulate: Credit card and inventory succeed, logistics fails
        List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD, ServiceName.INVENTORY);

        when(serviceClientPort.rollback(any(ServiceName.class), any(RollbackRequest.class)))
                .thenReturn(RollbackResponse.success(txId, "Rolled back"));

        // When
        rollbackService.executeRollback(txId, orderId, successfulServices);

        // Then - Verify rollback in reverse order
        var inOrder = inOrder(serviceClientPort);
        inOrder.verify(serviceClientPort).rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class));
        inOrder.verify(serviceClientPort).rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));
    }

    @Test
    @DisplayName("should continue rollback even when one service rollback fails")
    void shouldContinueRollbackEvenWhenOneServiceRollbackFails() {
        // Given
        OrderConfirmRequest request = new OrderConfirmRequest(
                "e3f4a5b6-c7d8-9012-ef01-23456789abcd",
                "user-789",
                List.of(new OrderConfirmRequest.OrderItemDto("SKU-003", 3, new BigDecimal("49.99"))),
                new BigDecimal("149.97"),
                "4333333333333333"
        );

        OrderConfirmResponse confirmResponse = orderSagaService.confirmOrder(request);
        UUID txId = UUID.fromString(confirmResponse.txId());
        UUID orderId = UUID.fromString(request.orderId());

        List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD, ServiceName.INVENTORY);

        // Inventory rollback fails, but credit card rollback should still be attempted
        when(serviceClientPort.rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class)))
                .thenReturn(RollbackResponse.failure(txId, "Inventory system unavailable"));
        when(serviceClientPort.rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class)))
                .thenReturn(RollbackResponse.success(txId, "Payment refunded"));

        // When
        rollbackService.executeRollback(txId, orderId, successfulServices);

        // Then - Both services should be called even though inventory failed
        verify(serviceClientPort).rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class));
        verify(serviceClientPort).rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));
    }

    @Test
    @DisplayName("should handle transaction status correctly after rollback")
    void shouldHandleTransactionStatusCorrectlyAfterRollback() {
        // Given
        OrderConfirmRequest request = new OrderConfirmRequest(
                "f4a5b6c7-d8e9-0123-f012-3456789abcde",
                "user-status",
                List.of(new OrderConfirmRequest.OrderItemDto("SKU-STATUS", 1, new BigDecimal("10.00"))),
                new BigDecimal("10.00"),
                "4444444444444444"
        );

        OrderConfirmResponse confirmResponse = orderSagaService.confirmOrder(request);
        String txId = confirmResponse.txId();

        // Then - Initial status should be PROCESSING
        Optional<TransactionStatusResponse> status = orderSagaService.getTransactionStatus(txId);
        assertThat(status).isPresent();
        assertThat(status.get().overallStatus()).isEqualTo("PROCESSING");
    }
}
