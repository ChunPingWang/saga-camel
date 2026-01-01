package com.ecommerce.order.integration;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmRequest;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmResponse;
import com.ecommerce.order.application.port.out.OutboxPort;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.application.service.OrderSagaService;
import com.ecommerce.order.infrastructure.checker.CheckerThreadManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Timeout Integration Tests")
@org.junit.jupiter.api.Disabled("Requires full saga execution - pending Camel route integration")
class TimeoutIntegrationTest {

    @Autowired
    private OrderSagaService orderSagaService;

    @Autowired
    private CheckerThreadManager checkerThreadManager;

    @MockBean
    private ServiceClientPort serviceClientPort;

    @MockBean
    private OutboxPort outboxPort;

    @MockBean
    private WebSocketPort webSocketPort;

    @BeforeEach
    void setUp() {
        // Set very short check interval for testing (100ms)
        checkerThreadManager.setCheckIntervalMs(100);
    }

    @Test
    @DisplayName("should trigger rollback when service times out")
    void shouldTriggerRollbackWhenServiceTimesOut() throws Exception {
        // Given - Configure short timeout for testing
        OrderConfirmRequest request = new OrderConfirmRequest(
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                "user-timeout",
                List.of(new OrderConfirmRequest.OrderItemDto("SKU-TIMEOUT", 1, new BigDecimal("50.00"))),
                new BigDecimal("50.00"),
                "4111111111111111"
        );

        // Mock credit card to succeed but simulate slow inventory (timeout)
        when(serviceClientPort.notify(eq(ServiceName.CREDIT_CARD), any(NotifyRequest.class)))
                .thenReturn(NotifyResponse.success(UUID.randomUUID(), "Payment processed", "CC-REF-123"));

        // Inventory never responds (simulating timeout)
        CountDownLatch inventoryCalled = new CountDownLatch(1);
        when(serviceClientPort.notify(eq(ServiceName.INVENTORY), any(NotifyRequest.class)))
                .thenAnswer(invocation -> {
                    inventoryCalled.countDown();
                    // Sleep longer than timeout threshold
                    Thread.sleep(5000);
                    return NotifyResponse.success(UUID.randomUUID(), "Never reached", "INV-REF-999");
                });

        // Rollback should be called
        CountDownLatch rollbackCalled = new CountDownLatch(1);
        when(serviceClientPort.rollback(any(ServiceName.class), any(RollbackRequest.class)))
                .thenAnswer(invocation -> {
                    rollbackCalled.countDown();
                    return RollbackResponse.success(UUID.randomUUID(), "Rolled back");
                });

        // When - Confirm order (this will start saga in background)
        OrderConfirmResponse response = orderSagaService.confirmOrder(request);

        // Then - Wait for rollback to be triggered
        boolean rollbackTriggered = rollbackCalled.await(10, TimeUnit.SECONDS);

        // Verify rollback was triggered due to timeout
        verify(serviceClientPort, atLeastOnce()).rollback(
                eq(ServiceName.CREDIT_CARD),
                any(RollbackRequest.class)
        );
    }

    @Test
    @DisplayName("should start checker thread after order confirmation")
    void shouldStartCheckerThreadAfterOrderConfirmation() throws Exception {
        // Given
        OrderConfirmRequest request = new OrderConfirmRequest(
                "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                "user-checker",
                List.of(new OrderConfirmRequest.OrderItemDto("SKU-CHECKER", 1, new BigDecimal("25.00"))),
                new BigDecimal("25.00"),
                "4222222222222222"
        );

        // Mock services to succeed quickly
        when(serviceClientPort.notify(any(ServiceName.class), any(NotifyRequest.class)))
                .thenReturn(NotifyResponse.success(UUID.randomUUID(), "Success", "REF-001"));

        // When
        OrderConfirmResponse response = orderSagaService.confirmOrder(request);
        UUID txId = UUID.fromString(response.txId());

        // Small delay to allow thread to start
        Thread.sleep(200);

        // Then - Checker thread should be active
        assertThat(checkerThreadManager.hasActiveThread(txId)).isTrue();

        // Cleanup
        checkerThreadManager.stopCheckerThread(txId);
    }

    @Test
    @DisplayName("should stop checker thread when saga completes successfully")
    void shouldStopCheckerThreadWhenSagaCompletesSuccessfully() throws Exception {
        // Given
        OrderConfirmRequest request = new OrderConfirmRequest(
                "c3d4e5f6-a7b8-9012-cdef-123456789abc",
                "user-complete",
                List.of(new OrderConfirmRequest.OrderItemDto("SKU-COMPLETE", 1, new BigDecimal("75.00"))),
                new BigDecimal("75.00"),
                "4333333333333333"
        );

        // Mock all services to succeed
        when(serviceClientPort.notify(eq(ServiceName.CREDIT_CARD), any(NotifyRequest.class)))
                .thenReturn(NotifyResponse.success(UUID.randomUUID(), "Payment processed", "CC-REF-001"));
        when(serviceClientPort.notify(eq(ServiceName.INVENTORY), any(NotifyRequest.class)))
                .thenReturn(NotifyResponse.success(UUID.randomUUID(), "Reserved", "INV-REF-001"));
        when(serviceClientPort.notify(eq(ServiceName.LOGISTICS), any(NotifyRequest.class)))
                .thenReturn(NotifyResponse.success(UUID.randomUUID(), "Scheduled", "LOG-REF-001"));

        // When
        OrderConfirmResponse response = orderSagaService.confirmOrder(request);
        UUID txId = UUID.fromString(response.txId());

        // Wait for saga to complete
        Thread.sleep(1000);

        // Then - Checker thread should have stopped
        assertThat(checkerThreadManager.hasActiveThread(txId)).isFalse();
    }

    @Test
    @DisplayName("should use different timeout thresholds per service")
    void shouldUseDifferentTimeoutThresholdsPerService() throws Exception {
        // Given - Payment has 30s timeout, Inventory has 60s
        OrderConfirmRequest request = new OrderConfirmRequest(
                "d4e5f6a7-b8c9-0123-efab-456789012345",
                "user-thresholds",
                List.of(new OrderConfirmRequest.OrderItemDto("SKU-THRESHOLDS", 1, new BigDecimal("100.00"))),
                new BigDecimal("100.00"),
                "4444444444444444"
        );

        // When/Then - Test validates that timeout configuration is loaded and used
        // Actual validation is in TransactionCheckerThreadTest
        OrderConfirmResponse response = orderSagaService.confirmOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.txId()).isNotNull();

        // Cleanup
        UUID txId = UUID.fromString(response.txId());
        checkerThreadManager.stopCheckerThread(txId);
    }
}
