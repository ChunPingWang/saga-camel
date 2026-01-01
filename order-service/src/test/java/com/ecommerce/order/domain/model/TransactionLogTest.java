package com.ecommerce.order.domain.model;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionLog domain entity.
 */
class TransactionLogTest {

    @Nested
    @DisplayName("TransactionLog Creation")
    class TransactionLogCreation {

        @Test
        @DisplayName("should create transaction log with valid data")
        void shouldCreateTransactionLogWithValidData() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            // When
            TransactionLog log = TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U);

            // Then
            assertEquals(txId, log.getTxId());
            assertEquals(orderId, log.getOrderId());
            assertEquals(ServiceName.CREDIT_CARD, log.getServiceName());
            assertEquals(TransactionStatus.U, log.getStatus());
            assertNull(log.getErrorMessage());
            assertEquals(0, log.getRetryCount());
            assertNotNull(log.getCreatedAt());
        }

        @Test
        @DisplayName("should create transaction log with error message")
        void shouldCreateTransactionLogWithErrorMessage() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            String errorMessage = "Payment declined";

            // When
            TransactionLog log = TransactionLog.createWithError(
                    txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.F, errorMessage
            );

            // Then
            assertEquals(TransactionStatus.F, log.getStatus());
            assertEquals(errorMessage, log.getErrorMessage());
        }

        @Test
        @DisplayName("should throw exception when txId is null")
        void shouldThrowExceptionWhenTxIdIsNull() {
            assertThrows(IllegalArgumentException.class, () ->
                    TransactionLog.create(null, UUID.randomUUID(), ServiceName.CREDIT_CARD, TransactionStatus.U)
            );
        }
    }

    @Nested
    @DisplayName("TransactionLog State")
    class TransactionLogState {

        @Test
        @DisplayName("should correctly identify terminal states")
        void shouldCorrectlyIdentifyTerminalStates() {
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            TransactionLog doneLog = TransactionLog.create(txId, orderId, ServiceName.SAGA, TransactionStatus.D);
            TransactionLog rfLog = TransactionLog.create(txId, orderId, ServiceName.SAGA, TransactionStatus.RF);
            TransactionLog successLog = TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S);

            assertTrue(doneLog.isTerminal());
            assertTrue(rfLog.isTerminal());
            assertFalse(successLog.isTerminal());
        }

        @Test
        @DisplayName("should correctly identify success states")
        void shouldCorrectlyIdentifySuccessStates() {
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            TransactionLog successLog = TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S);
            TransactionLog failedLog = TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.F);

            assertTrue(successLog.isSuccess());
            assertFalse(failedLog.isSuccess());
        }
    }
}
