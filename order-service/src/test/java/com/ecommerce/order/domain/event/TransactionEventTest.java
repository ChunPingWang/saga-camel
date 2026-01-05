package com.ecommerce.order.domain.event;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionEvent domain event.
 */
class TransactionEventTest {

    private static final UUID TX_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("Event Type Constants")
    class EventTypeConstants {

        @Test
        @DisplayName("should have correct event type constants")
        void shouldHaveCorrectEventTypeConstants() {
            assertEquals("SAGA_STARTED", TransactionEvent.SAGA_STARTED);
            assertEquals("SERVICE_PROCESSING", TransactionEvent.SERVICE_PROCESSING);
            assertEquals("SERVICE_SUCCESS", TransactionEvent.SERVICE_SUCCESS);
            assertEquals("SERVICE_FAILED", TransactionEvent.SERVICE_FAILED);
            assertEquals("ROLLBACK_STARTED", TransactionEvent.ROLLBACK_STARTED);
            assertEquals("ROLLBACK_SUCCESS", TransactionEvent.ROLLBACK_SUCCESS);
            assertEquals("ROLLBACK_FAILED", TransactionEvent.ROLLBACK_FAILED);
            assertEquals("SAGA_COMPLETED", TransactionEvent.SAGA_COMPLETED);
            assertEquals("SAGA_ROLLED_BACK", TransactionEvent.SAGA_ROLLED_BACK);
        }
    }

    @Nested
    @DisplayName("Saga Started Event")
    class SagaStartedEvent {

        @Test
        @DisplayName("should create saga started event")
        void shouldCreateSagaStartedEvent() {
            // When
            TransactionEvent event = TransactionEvent.sagaStarted(TX_ID, ORDER_ID);

            // Then
            assertNotNull(event.eventId());
            assertEquals(TX_ID, event.txId());
            assertEquals(ORDER_ID, event.orderId());
            assertEquals(TransactionEvent.SAGA_STARTED, event.eventType());
            assertEquals(ServiceName.SAGA, event.serviceName());
            assertEquals(TransactionStatus.U, event.status());
            assertEquals("Saga started", event.message());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Service Processing Event")
    class ServiceProcessingEvent {

        @Test
        @DisplayName("should create service processing event for credit card")
        void shouldCreateServiceProcessingEventForCreditCard() {
            // When
            TransactionEvent event = TransactionEvent.serviceProcessing(TX_ID, ORDER_ID, ServiceName.CREDIT_CARD);

            // Then
            assertNotNull(event.eventId());
            assertEquals(TX_ID, event.txId());
            assertEquals(ORDER_ID, event.orderId());
            assertEquals(TransactionEvent.SERVICE_PROCESSING, event.eventType());
            assertEquals(ServiceName.CREDIT_CARD, event.serviceName());
            assertEquals(TransactionStatus.U, event.status());
            assertEquals("Processing: Credit Card Service", event.message());
            assertNotNull(event.timestamp());
        }

        @Test
        @DisplayName("should create service processing event for inventory")
        void shouldCreateServiceProcessingEventForInventory() {
            // When
            TransactionEvent event = TransactionEvent.serviceProcessing(TX_ID, ORDER_ID, ServiceName.INVENTORY);

            // Then
            assertEquals(ServiceName.INVENTORY, event.serviceName());
            assertEquals("Processing: Inventory Service", event.message());
        }

        @Test
        @DisplayName("should create service processing event for logistics")
        void shouldCreateServiceProcessingEventForLogistics() {
            // When
            TransactionEvent event = TransactionEvent.serviceProcessing(TX_ID, ORDER_ID, ServiceName.LOGISTICS);

            // Then
            assertEquals(ServiceName.LOGISTICS, event.serviceName());
            assertEquals("Processing: Logistics Service", event.message());
        }
    }

    @Nested
    @DisplayName("Service Success Event")
    class ServiceSuccessEvent {

        @Test
        @DisplayName("should create service success event")
        void shouldCreateServiceSuccessEvent() {
            // When
            TransactionEvent event = TransactionEvent.serviceSuccess(TX_ID, ORDER_ID, ServiceName.CREDIT_CARD);

            // Then
            assertNotNull(event.eventId());
            assertEquals(TX_ID, event.txId());
            assertEquals(ORDER_ID, event.orderId());
            assertEquals(TransactionEvent.SERVICE_SUCCESS, event.eventType());
            assertEquals(ServiceName.CREDIT_CARD, event.serviceName());
            assertEquals(TransactionStatus.S, event.status());
            assertEquals("Credit Card Service completed successfully", event.message());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Service Failed Event")
    class ServiceFailedEvent {

        @Test
        @DisplayName("should create service failed event with error message")
        void shouldCreateServiceFailedEvent() {
            // Given
            String error = "Payment declined";

            // When
            TransactionEvent event = TransactionEvent.serviceFailed(TX_ID, ORDER_ID, ServiceName.CREDIT_CARD, error);

            // Then
            assertNotNull(event.eventId());
            assertEquals(TX_ID, event.txId());
            assertEquals(ORDER_ID, event.orderId());
            assertEquals(TransactionEvent.SERVICE_FAILED, event.eventType());
            assertEquals(ServiceName.CREDIT_CARD, event.serviceName());
            assertEquals(TransactionStatus.F, event.status());
            assertEquals("Credit Card Service failed: Payment declined", event.message());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Rollback Started Event")
    class RollbackStartedEvent {

        @Test
        @DisplayName("should create rollback started event")
        void shouldCreateRollbackStartedEvent() {
            // When
            TransactionEvent event = TransactionEvent.rollbackStarted(TX_ID, ORDER_ID);

            // Then
            assertNotNull(event.eventId());
            assertEquals(TX_ID, event.txId());
            assertEquals(ORDER_ID, event.orderId());
            assertEquals(TransactionEvent.ROLLBACK_STARTED, event.eventType());
            assertEquals(ServiceName.SAGA, event.serviceName());
            assertNull(event.status());
            assertEquals("Rollback started", event.message());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Rollback Success Event")
    class RollbackSuccessEvent {

        @Test
        @DisplayName("should create rollback success event")
        void shouldCreateRollbackSuccessEvent() {
            // When
            TransactionEvent event = TransactionEvent.rollbackSuccess(TX_ID, ORDER_ID, ServiceName.INVENTORY);

            // Then
            assertNotNull(event.eventId());
            assertEquals(TX_ID, event.txId());
            assertEquals(ORDER_ID, event.orderId());
            assertEquals(TransactionEvent.ROLLBACK_SUCCESS, event.eventType());
            assertEquals(ServiceName.INVENTORY, event.serviceName());
            assertEquals(TransactionStatus.R, event.status());
            assertEquals("Inventory Service rolled back", event.message());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Rollback Failed Event")
    class RollbackFailedEvent {

        @Test
        @DisplayName("should create rollback failed event with error message")
        void shouldCreateRollbackFailedEvent() {
            // Given
            String error = "Connection timeout";

            // When
            TransactionEvent event = TransactionEvent.rollbackFailed(TX_ID, ORDER_ID, ServiceName.LOGISTICS, error);

            // Then
            assertNotNull(event.eventId());
            assertEquals(TX_ID, event.txId());
            assertEquals(ORDER_ID, event.orderId());
            assertEquals(TransactionEvent.ROLLBACK_FAILED, event.eventType());
            assertEquals(ServiceName.LOGISTICS, event.serviceName());
            assertEquals(TransactionStatus.RF, event.status());
            assertEquals("Logistics Service rollback failed: Connection timeout", event.message());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Saga Completed Event")
    class SagaCompletedEvent {

        @Test
        @DisplayName("should create saga completed event")
        void shouldCreateSagaCompletedEvent() {
            // When
            TransactionEvent event = TransactionEvent.sagaCompleted(TX_ID, ORDER_ID);

            // Then
            assertNotNull(event.eventId());
            assertEquals(TX_ID, event.txId());
            assertEquals(ORDER_ID, event.orderId());
            assertEquals(TransactionEvent.SAGA_COMPLETED, event.eventType());
            assertEquals(ServiceName.SAGA, event.serviceName());
            assertEquals(TransactionStatus.S, event.status());
            assertEquals("Order transaction completed", event.message());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Saga Rolled Back Event")
    class SagaRolledBackEvent {

        @Test
        @DisplayName("should create saga rolled back event")
        void shouldCreateSagaRolledBackEvent() {
            // When
            TransactionEvent event = TransactionEvent.sagaRolledBack(TX_ID, ORDER_ID);

            // Then
            assertNotNull(event.eventId());
            assertEquals(TX_ID, event.txId());
            assertEquals(ORDER_ID, event.orderId());
            assertEquals(TransactionEvent.SAGA_ROLLED_BACK, event.eventType());
            assertEquals(ServiceName.SAGA, event.serviceName());
            assertEquals(TransactionStatus.D, event.status());
            assertEquals("Order transaction rolled back", event.message());
            assertNotNull(event.timestamp());
        }
    }

    @Nested
    @DisplayName("Record Methods")
    class RecordMethods {

        @Test
        @DisplayName("should have working toString")
        void shouldHaveWorkingToString() {
            // Given
            TransactionEvent event = TransactionEvent.sagaStarted(TX_ID, ORDER_ID);

            // When
            String toString = event.toString();

            // Then
            assertTrue(toString.contains("SAGA_STARTED"));
            assertTrue(toString.contains(TX_ID.toString()));
            assertTrue(toString.contains(ORDER_ID.toString()));
        }

        @Test
        @DisplayName("each event should have unique eventId")
        void eachEventShouldHaveUniqueEventId() {
            // When
            TransactionEvent event1 = TransactionEvent.sagaStarted(TX_ID, ORDER_ID);
            TransactionEvent event2 = TransactionEvent.sagaStarted(TX_ID, ORDER_ID);

            // Then
            assertNotEquals(event1.eventId(), event2.eventId());
        }
    }
}
