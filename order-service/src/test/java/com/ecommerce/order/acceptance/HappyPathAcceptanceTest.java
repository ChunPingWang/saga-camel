package com.ecommerce.order.acceptance;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmRequest;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmResponse;
import com.ecommerce.order.application.port.out.OutboxPort;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.application.service.OrderSagaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BDD-style acceptance tests for happy path scenarios.
 *
 * Feature: Order Confirmation with Saga Orchestration
 *   As an e-commerce customer
 *   I want to confirm my order
 *   So that my payment is processed, inventory is reserved, and shipping is scheduled
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Feature: Order Confirmation Happy Path")
class HappyPathAcceptanceTest {

    @Autowired
    private OrderSagaService orderSagaService;

    @MockBean
    private ServiceClientPort serviceClientPort;

    @MockBean
    private OutboxPort outboxPort;

    @MockBean
    private WebSocketPort webSocketPort;

    @Nested
    @DisplayName("Scenario: Successful order confirmation")
    class SuccessfulOrderConfirmation {

        @Test
        @DisplayName("Given a valid order request, When customer confirms order, Then transaction is created and processing starts")
        void shouldCreateTransactionAndStartProcessing() {
            // Given: A valid order request
            String orderId = UUID.randomUUID().toString();
            OrderConfirmRequest request = new OrderConfirmRequest(
                    orderId,
                    "customer-123",
                    List.of(new OrderConfirmRequest.OrderItemDto("SKU-001", 2, new BigDecimal("29.99"))),
                    new BigDecimal("59.98"),
                    "4111111111111111"
            );

            // When: Customer confirms order
            OrderConfirmResponse response = orderSagaService.confirmOrder(request);

            // Then: Transaction is created with unique txId
            assertThat(response).isNotNull();
            assertThat(response.txId()).isNotBlank();
            assertThat(response.status()).isEqualTo("PROCESSING");

            // And: txId is a valid UUID
            assertThat(UUID.fromString(response.txId())).isNotNull();

            // And: Outbox event is created for async processing
            verify(outboxPort).save(any());
        }
    }

    @Nested
    @DisplayName("Scenario: Transaction status inquiry")
    class TransactionStatusInquiry {

        @Test
        @DisplayName("Given an existing transaction, When customer queries status, Then current status is returned")
        void shouldReturnCurrentTransactionStatus() {
            // Given: An existing transaction
            String orderId = UUID.randomUUID().toString();
            OrderConfirmRequest request = new OrderConfirmRequest(
                    orderId,
                    "customer-789",
                    List.of(new OrderConfirmRequest.OrderItemDto("SKU-001", 1, new BigDecimal("19.99"))),
                    new BigDecimal("19.99"),
                    "4333333333333333"
            );

            OrderConfirmResponse confirmResponse = orderSagaService.confirmOrder(request);
            String txId = confirmResponse.txId();

            // When: Customer queries transaction status
            var statusResponse = orderSagaService.getTransactionStatus(txId);

            // Then: Current status is returned
            assertThat(statusResponse).isPresent();
            assertThat(statusResponse.get().txId()).isEqualTo(txId);
            assertThat(statusResponse.get().overallStatus()).isIn("PROCESSING", "COMPLETED", "FAILED");
        }

        @Test
        @DisplayName("Given a non-existent transaction, When customer queries status, Then empty result is returned")
        void shouldReturnEmptyForNonExistentTransaction() {
            // Given: A non-existent transaction ID
            String nonExistentTxId = UUID.randomUUID().toString();

            // When: Customer queries transaction status
            var statusResponse = orderSagaService.getTransactionStatus(nonExistentTxId);

            // Then: Empty result is returned
            assertThat(statusResponse).isEmpty();
        }
    }

    @Nested
    @DisplayName("Scenario: Multiple orders")
    class MultipleOrders {

        @Test
        @DisplayName("Given multiple orders, When each is confirmed, Then each gets unique transaction ID")
        void shouldAssignUniqueTxIdToEachOrder() {
            // Given: Multiple order requests
            OrderConfirmRequest order1 = new OrderConfirmRequest(
                    UUID.randomUUID().toString(),
                    "customer-001",
                    List.of(new OrderConfirmRequest.OrderItemDto("SKU-001", 1, new BigDecimal("10.00"))),
                    new BigDecimal("10.00"),
                    "4111111111111111"
            );

            OrderConfirmRequest order2 = new OrderConfirmRequest(
                    UUID.randomUUID().toString(),
                    "customer-001",
                    List.of(new OrderConfirmRequest.OrderItemDto("SKU-002", 2, new BigDecimal("20.00"))),
                    new BigDecimal("40.00"),
                    "4111111111111111"
            );

            // When: Both orders are confirmed
            OrderConfirmResponse response1 = orderSagaService.confirmOrder(order1);
            OrderConfirmResponse response2 = orderSagaService.confirmOrder(order2);

            // Then: Each order gets a unique transaction ID
            assertThat(response1.txId()).isNotEqualTo(response2.txId());

            // And: Both are in processing state
            assertThat(response1.status()).isEqualTo("PROCESSING");
            assertThat(response2.status()).isEqualTo("PROCESSING");
        }
    }
}
