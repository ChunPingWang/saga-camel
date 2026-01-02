package com.ecommerce.order.application.service;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.application.port.out.CheckerPort;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.domain.model.TransactionLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SagaRecoveryService Tests")
class SagaRecoveryServiceTest {

    @Mock
    private TransactionLogPort transactionLogPort;

    @Mock
    private CheckerPort checkerPort;

    private SagaRecoveryService recoveryService;

    private static final Map<ServiceName, Integer> DEFAULT_TIMEOUTS = Map.of(
            ServiceName.CREDIT_CARD, 30,
            ServiceName.INVENTORY, 60,
            ServiceName.LOGISTICS, 120
    );

    @BeforeEach
    void setUp() {
        recoveryService = new SagaRecoveryService(transactionLogPort, checkerPort, DEFAULT_TIMEOUTS);
    }

    @Nested
    @DisplayName("recoverUnfinishedTransactions")
    class RecoverUnfinishedTransactions {

        @Test
        @DisplayName("should start checker threads for unfinished transactions")
        void shouldStartCheckerThreadsForUnfinishedTransactions() {
            // Given - Two unfinished transactions
            UUID txId1 = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            UUID txId2 = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789abc");
            UUID orderId1 = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
            UUID orderId2 = UUID.fromString("d4e5f6a7-b8c9-0123-efab-456789012345");

            List<TransactionLogPort.UnfinishedTransaction> unfinished = List.of(
                    new TransactionLogPort.UnfinishedTransaction(txId1, orderId1),
                    new TransactionLogPort.UnfinishedTransaction(txId2, orderId2)
            );

            when(transactionLogPort.findUnfinishedTransactions()).thenReturn(unfinished);

            // When
            int count = recoveryService.recoverUnfinishedTransactions();

            // Then
            assertThat(count).isEqualTo(2);
            verify(checkerPort).startCheckerThread(eq(txId1), eq(orderId1), any());
            verify(checkerPort).startCheckerThread(eq(txId2), eq(orderId2), any());
        }

        @Test
        @DisplayName("should return zero when no unfinished transactions")
        void shouldReturnZeroWhenNoUnfinishedTransactions() {
            // Given
            when(transactionLogPort.findUnfinishedTransactions()).thenReturn(List.of());

            // When
            int count = recoveryService.recoverUnfinishedTransactions();

            // Then
            assertThat(count).isEqualTo(0);
            verify(checkerPort, never()).startCheckerThread(any(), any(), any());
        }

        @Test
        @DisplayName("should skip transactions already being monitored")
        void shouldSkipTransactionsAlreadyBeingMonitored() {
            // Given
            UUID txId1 = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            UUID txId2 = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789abc");
            UUID orderId1 = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
            UUID orderId2 = UUID.fromString("d4e5f6a7-b8c9-0123-efab-456789012345");

            List<TransactionLogPort.UnfinishedTransaction> unfinished = List.of(
                    new TransactionLogPort.UnfinishedTransaction(txId1, orderId1),
                    new TransactionLogPort.UnfinishedTransaction(txId2, orderId2)
            );

            when(transactionLogPort.findUnfinishedTransactions()).thenReturn(unfinished);
            when(checkerPort.hasActiveThread(txId1)).thenReturn(true);  // Already monitored
            when(checkerPort.hasActiveThread(txId2)).thenReturn(false);

            // When
            int count = recoveryService.recoverUnfinishedTransactions();

            // Then
            assertThat(count).isEqualTo(1);  // Only txId2 was started
            verify(checkerPort, never()).startCheckerThread(eq(txId1), any(), any());
            verify(checkerPort).startCheckerThread(eq(txId2), eq(orderId2), any());
        }

        @Test
        @DisplayName("should continue recovery even if one transaction fails")
        void shouldContinueRecoveryEvenIfOneTransactionFails() {
            // Given
            UUID txId1 = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            UUID txId2 = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789abc");
            UUID orderId1 = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
            UUID orderId2 = UUID.fromString("d4e5f6a7-b8c9-0123-efab-456789012345");

            List<TransactionLogPort.UnfinishedTransaction> unfinished = List.of(
                    new TransactionLogPort.UnfinishedTransaction(txId1, orderId1),
                    new TransactionLogPort.UnfinishedTransaction(txId2, orderId2)
            );

            when(transactionLogPort.findUnfinishedTransactions()).thenReturn(unfinished);

            // First one throws, second should still be processed
            doThrow(new RuntimeException("Failed to start")).when(checkerPort)
                    .startCheckerThread(eq(txId1), any(), any());

            // When
            int count = recoveryService.recoverUnfinishedTransactions();

            // Then - txId2 should still have been attempted
            assertThat(count).isEqualTo(1);  // Only second one succeeded
            verify(checkerPort).startCheckerThread(eq(txId2), eq(orderId2), any());
        }
    }
}
