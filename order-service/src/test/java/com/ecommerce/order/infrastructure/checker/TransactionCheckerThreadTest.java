package com.ecommerce.order.infrastructure.checker;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.service.RollbackService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TransactionCheckerThread Tests")
class TransactionCheckerThreadTest {

    @Mock
    private TransactionLogPort transactionLogPort;

    @Mock
    private RollbackService rollbackService;

    @Mock
    private CheckerThreadManager checkerThreadManager;

    private UUID txId;
    private UUID orderId;
    private Map<ServiceName, Integer> timeouts;

    @BeforeEach
    void setUp() {
        txId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        orderId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
        timeouts = Map.of(
                ServiceName.CREDIT_CARD, 30,
                ServiceName.INVENTORY, 60,
                ServiceName.LOGISTICS, 120
        );
    }

    @Nested
    @DisplayName("Timeout Detection")
    class TimeoutDetection {

        @Test
        @DisplayName("should detect timeout when service exceeds threshold")
        void shouldDetectTimeoutWhenServiceExceedsThreshold() throws Exception {
            // Given - Service has been in U status longer than timeout
            TransactionLog timedOutLog = TransactionLog.create(
                    txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U
            );
            // Simulate old timestamp (31 seconds ago)
            TransactionLog oldLog = createLogWithOldTimestamp(timedOutLog, 31);

            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(List.of(oldLog));

            CountDownLatch rollbackCalled = new CountDownLatch(1);
            doAnswer(invocation -> {
                rollbackCalled.countDown();
                return null;
            }).when(rollbackService).executeRollback(any(), any(), any());

            TransactionCheckerThread checker = new TransactionCheckerThread(
                    txId, orderId, timeouts, 100, // 100ms check interval for fast test
                    transactionLogPort, rollbackService, checkerThreadManager
            );

            // When
            Thread thread = new Thread(checker);
            thread.start();

            // Then - Should trigger rollback within reasonable time
            boolean rollbackTriggered = rollbackCalled.await(2, TimeUnit.SECONDS);
            assertThat(rollbackTriggered).isTrue();

            verify(rollbackService).executeRollback(eq(txId), eq(orderId), any());

            checker.stop();
            thread.join(1000);
        }

        @Test
        @DisplayName("should not trigger rollback if within timeout threshold")
        void shouldNotTriggerRollbackIfWithinThreshold() throws Exception {
            // Given - Service is in U status but within timeout
            TransactionLog recentLog = TransactionLog.create(
                    txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U
            );

            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(List.of(recentLog));

            TransactionCheckerThread checker = new TransactionCheckerThread(
                    txId, orderId, timeouts, 100,
                    transactionLogPort, rollbackService, checkerThreadManager
            );

            // When - Run for a short time
            Thread thread = new Thread(checker);
            thread.start();
            Thread.sleep(300);
            checker.stop();
            thread.join(1000);

            // Then - Rollback should not have been triggered
            verify(rollbackService, never()).executeRollback(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Terminal State Detection")
    class TerminalStateDetection {

        @Test
        @DisplayName("should stop when all services succeed")
        void shouldStopWhenAllServicesSucceed() throws Exception {
            // Given - All services completed successfully
            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(List.of(
                            TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S),
                            TransactionLog.create(txId, orderId, ServiceName.INVENTORY, TransactionStatus.S),
                            TransactionLog.create(txId, orderId, ServiceName.LOGISTICS, TransactionStatus.S)
                    ));

            TransactionCheckerThread checker = new TransactionCheckerThread(
                    txId, orderId, timeouts, 100,
                    transactionLogPort, rollbackService, checkerThreadManager
            );

            // When
            Thread thread = new Thread(checker);
            thread.start();
            thread.join(1000);

            // Then - Should stop and notify manager
            verify(checkerThreadManager).removeThread(txId);
            assertThat(thread.isAlive()).isFalse();
        }

        @Test
        @DisplayName("should stop when rollback completed")
        void shouldStopWhenRollbackCompleted() throws Exception {
            // Given - All services have been rolled back
            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(List.of(
                            TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.R),
                            TransactionLog.create(txId, orderId, ServiceName.INVENTORY, TransactionStatus.R)
                    ));

            TransactionCheckerThread checker = new TransactionCheckerThread(
                    txId, orderId, timeouts, 100,
                    transactionLogPort, rollbackService, checkerThreadManager
            );

            // When
            Thread thread = new Thread(checker);
            thread.start();
            thread.join(1000);

            // Then - Should stop
            verify(checkerThreadManager).removeThread(txId);
            assertThat(thread.isAlive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Failure Detection")
    class FailureDetection {

        @Test
        @DisplayName("should trigger rollback when failure detected")
        void shouldTriggerRollbackWhenFailureDetected() throws Exception {
            // Given - One service failed
            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(List.of(
                            TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S),
                            TransactionLog.create(txId, orderId, ServiceName.INVENTORY, TransactionStatus.F)
                    ));

            CountDownLatch rollbackCalled = new CountDownLatch(1);
            doAnswer(invocation -> {
                rollbackCalled.countDown();
                return null;
            }).when(rollbackService).executeRollback(any(), any(), any());

            TransactionCheckerThread checker = new TransactionCheckerThread(
                    txId, orderId, timeouts, 100,
                    transactionLogPort, rollbackService, checkerThreadManager
            );

            // When
            Thread thread = new Thread(checker);
            thread.start();

            // Then
            boolean rollbackTriggered = rollbackCalled.await(2, TimeUnit.SECONDS);
            assertThat(rollbackTriggered).isTrue();

            verify(rollbackService).executeRollback(
                    eq(txId),
                    eq(orderId),
                    eq(List.of(ServiceName.CREDIT_CARD))
            );

            checker.stop();
            thread.join(1000);
        }
    }

    @Nested
    @DisplayName("Graceful Shutdown")
    class GracefulShutdown {

        @Test
        @DisplayName("should stop when stop() is called")
        void shouldStopWhenStopIsCalled() throws Exception {
            // Given
            when(transactionLogPort.findLatestByTxId(txId.toString()))
                    .thenReturn(List.of(
                            TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U)
                    ));

            TransactionCheckerThread checker = new TransactionCheckerThread(
                    txId, orderId, timeouts, 100,
                    transactionLogPort, rollbackService, checkerThreadManager
            );

            // When
            Thread thread = new Thread(checker);
            thread.start();
            Thread.sleep(200);
            checker.stop();
            thread.join(1000);

            // Then
            assertThat(thread.isAlive()).isFalse();
        }
    }

    // Helper to create log with old timestamp for timeout testing
    private TransactionLog createLogWithOldTimestamp(TransactionLog log, int secondsAgo) {
        // Use reflection or create a test-friendly constructor
        // For now, we'll use a mock approach
        TransactionLog oldLog = mock(TransactionLog.class);
        when(oldLog.getTxId()).thenReturn(log.getTxId());
        when(oldLog.getOrderId()).thenReturn(log.getOrderId());
        when(oldLog.getServiceName()).thenReturn(log.getServiceName());
        when(oldLog.getStatus()).thenReturn(log.getStatus());
        when(oldLog.getCreatedAt()).thenReturn(LocalDateTime.now().minusSeconds(secondsAgo));
        return oldLog;
    }
}
