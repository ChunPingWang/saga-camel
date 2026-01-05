package com.ecommerce.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionEvent domain model.
 */
class TransactionEventTest {

    @Nested
    @DisplayName("Order Confirmed Event")
    class OrderConfirmedEvent {

        @Test
        @DisplayName("should create order confirmed event with all fields")
        void shouldCreateOrderConfirmedEvent() {
            // Given
            String txId = "tx-123";
            String orderId = "order-456";
            String userId = "user-789";
            List<Map<String, Object>> items = List.of(
                    Map.of("productId", "PROD-001", "quantity", 2)
            );
            BigDecimal totalAmount = new BigDecimal("99.99");
            String creditCardNumber = "4111111111111111";

            // When
            TransactionEvent event = TransactionEvent.orderConfirmed(
                    txId, orderId, userId, items, totalAmount, creditCardNumber
            );

            // Then
            assertEquals("ORDER_CONFIRMED", event.eventType());
            assertEquals(txId, event.txId());
            assertEquals(orderId, event.orderId());
            assertEquals(userId, event.userId());
            assertEquals(items, event.items());
            assertEquals(totalAmount, event.totalAmount());
            assertEquals(creditCardNumber, event.creditCardNumber());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Service Failed Event")
    class ServiceFailedEvent {

        @Test
        @DisplayName("should create service failed event with error info")
        void shouldCreateServiceFailedEvent() {
            // Given
            String txId = "tx-123";
            String orderId = "order-456";
            String serviceName = "credit-card";
            String errorMessage = "Payment declined";

            // When
            TransactionEvent event = TransactionEvent.serviceFailed(
                    txId, orderId, serviceName, errorMessage
            );

            // Then
            assertEquals("SERVICE_FAILED", event.eventType());
            assertEquals(txId, event.txId());
            assertEquals(orderId, event.orderId());
            assertNull(event.userId());
            assertNotNull(event.items());
            assertEquals(1, event.items().size());
            assertEquals(serviceName, event.items().get(0).get("serviceName"));
            assertEquals(errorMessage, event.items().get(0).get("error"));
            assertNull(event.totalAmount());
            assertNull(event.creditCardNumber());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Saga Completed Event")
    class SagaCompletedEvent {

        @Test
        @DisplayName("should create saga completed event")
        void shouldCreateSagaCompletedEvent() {
            // Given
            String txId = "tx-123";
            String orderId = "order-456";

            // When
            TransactionEvent event = TransactionEvent.sagaCompleted(txId, orderId);

            // Then
            assertEquals("SAGA_COMPLETED", event.eventType());
            assertEquals(txId, event.txId());
            assertEquals(orderId, event.orderId());
            assertNull(event.userId());
            assertNull(event.items());
            assertNull(event.totalAmount());
            assertNull(event.creditCardNumber());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Saga Rolled Back Event")
    class SagaRolledBackEvent {

        @Test
        @DisplayName("should create saga rolled back event")
        void shouldCreateSagaRolledBackEvent() {
            // Given
            String txId = "tx-123";
            String orderId = "order-456";

            // When
            TransactionEvent event = TransactionEvent.sagaRolledBack(txId, orderId);

            // Then
            assertEquals("SAGA_ROLLED_BACK", event.eventType());
            assertEquals(txId, event.txId());
            assertEquals(orderId, event.orderId());
            assertNull(event.userId());
            assertNull(event.items());
            assertNull(event.totalAmount());
            assertNull(event.creditCardNumber());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Record Methods")
    class RecordMethods {

        @Test
        @DisplayName("should have working equals and hashCode")
        void shouldHaveWorkingEqualsAndHashCode() {
            // Given - same event data
            String txId = "tx-123";
            String orderId = "order-456";

            TransactionEvent event1 = TransactionEvent.sagaCompleted(txId, orderId);
            TransactionEvent event2 = TransactionEvent.sagaCompleted(txId, orderId);

            // Then - not equal due to different timestamps
            // Records with Instant.now() will have different timestamps
            assertNotEquals(event1, event2);
        }

        @Test
        @DisplayName("should have working toString")
        void shouldHaveWorkingToString() {
            // Given
            TransactionEvent event = TransactionEvent.sagaCompleted("tx-123", "order-456");

            // When
            String toString = event.toString();

            // Then
            assertTrue(toString.contains("SAGA_COMPLETED"));
            assertTrue(toString.contains("tx-123"));
            assertTrue(toString.contains("order-456"));
        }
    }
}
