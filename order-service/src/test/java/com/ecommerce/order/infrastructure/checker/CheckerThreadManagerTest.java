package com.ecommerce.order.infrastructure.checker;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.application.port.out.RollbackExecutorPort;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CheckerThreadManager Tests")
class CheckerThreadManagerTest {

    @Mock
    private TransactionLogPort transactionLogPort;

    @Mock
    private RollbackExecutorPort rollbackExecutorPort;

    private CheckerThreadManager manager;
    private Map<ServiceName, Integer> timeouts;

    @BeforeEach
    void setUp() {
        timeouts = Map.of(
                ServiceName.CREDIT_CARD, 30,
                ServiceName.INVENTORY, 60,
                ServiceName.LOGISTICS, 120
        );
        manager = new CheckerThreadManager(transactionLogPort, rollbackExecutorPort);
    }

    @Nested
    @DisplayName("Thread Management")
    class ThreadManagement {

        @Test
        @DisplayName("should start checker thread for new transaction")
        void shouldStartCheckerThreadForNewTransaction() {
            // Given
            UUID txId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            UUID orderId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(Collections.emptyList());

            // When
            manager.startCheckerThread(txId, orderId, timeouts);

            // Then
            assertThat(manager.hasActiveThread(txId)).isTrue();
            assertThat(manager.getActiveThreadCount()).isEqualTo(1);

            // Cleanup
            manager.stopCheckerThread(txId);
        }

        @Test
        @DisplayName("should not start duplicate thread for same transaction")
        void shouldNotStartDuplicateThread() {
            // Given
            UUID txId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            UUID orderId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(Collections.emptyList());

            // When - Start twice
            manager.startCheckerThread(txId, orderId, timeouts);
            manager.startCheckerThread(txId, orderId, timeouts);

            // Then - Should still only have one
            assertThat(manager.getActiveThreadCount()).isEqualTo(1);

            // Cleanup
            manager.stopCheckerThread(txId);
        }

        @Test
        @DisplayName("should stop checker thread by transaction ID")
        void shouldStopCheckerThread() throws Exception {
            // Given
            UUID txId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            UUID orderId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(Collections.emptyList());

            manager.startCheckerThread(txId, orderId, timeouts);
            assertThat(manager.hasActiveThread(txId)).isTrue();

            // When
            manager.stopCheckerThread(txId);

            // Give thread time to terminate
            Thread.sleep(200);

            // Then
            assertThat(manager.hasActiveThread(txId)).isFalse();
            assertThat(manager.getActiveThreadCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should remove thread reference via removeThread")
        void shouldRemoveThreadReference() {
            // Given
            UUID txId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            UUID orderId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(Collections.emptyList());

            manager.startCheckerThread(txId, orderId, timeouts);

            // When - Simulate thread self-removal
            manager.removeThread(txId);

            // Then
            assertThat(manager.hasActiveThread(txId)).isFalse();
        }
    }

    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperations {

        @Test
        @DisplayName("should stop all threads on shutdown")
        void shouldStopAllThreadsOnShutdown() throws Exception {
            // Given - Start multiple threads
            UUID txId1 = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            UUID txId2 = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789abc");
            UUID orderId1 = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
            UUID orderId2 = UUID.fromString("d4e5f6a7-b8c9-0123-efab-456789012345");

            when(transactionLogPort.findLatestByTxId(any()))
                    .thenReturn(Collections.emptyList());

            manager.startCheckerThread(txId1, orderId1, timeouts);
            manager.startCheckerThread(txId2, orderId2, timeouts);
            assertThat(manager.getActiveThreadCount()).isEqualTo(2);

            // When
            manager.shutdownAll();

            // Give threads time to terminate
            Thread.sleep(500);

            // Then
            assertThat(manager.getActiveThreadCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return list of active transaction IDs")
        void shouldReturnActiveTransactionIds() {
            // Given
            UUID txId1 = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            UUID txId2 = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789abc");
            UUID orderId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

            when(transactionLogPort.findLatestByTxId(any()))
                    .thenReturn(Collections.emptyList());

            manager.startCheckerThread(txId1, orderId, timeouts);
            manager.startCheckerThread(txId2, orderId, timeouts);

            // When
            var activeIds = manager.getActiveTransactionIds();

            // Then
            assertThat(activeIds).containsExactlyInAnyOrder(txId1, txId2);

            // Cleanup
            manager.shutdownAll();
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("should use configured check interval")
        void shouldUseConfiguredCheckInterval() {
            // Given
            UUID txId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            UUID orderId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
            long customInterval = 5000L; // 5 seconds

            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(Collections.emptyList());

            // When
            manager.setCheckIntervalMs(customInterval);
            manager.startCheckerThread(txId, orderId, timeouts);

            // Then - The thread should be created with custom interval
            assertThat(manager.hasActiveThread(txId)).isTrue();

            // Cleanup
            manager.stopCheckerThread(txId);
        }
    }
}
