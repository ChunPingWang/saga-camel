package com.ecommerce.order.acceptance;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.application.port.out.OutboxPort;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.infrastructure.checker.CheckerThreadManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDD-style acceptance tests for timeout detection scenarios.
 *
 * Feature: Timeout Detection and Automatic Compensation
 *   As an e-commerce system
 *   I want to detect when services take too long
 *   So that I can trigger automatic rollback and maintain system consistency
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Feature: Timeout Detection and Automatic Compensation")
class TimeoutAcceptanceTest {

    @Autowired
    private CheckerThreadManager checkerThreadManager;

    @MockBean
    private TransactionLogPort transactionLogPort;

    @MockBean
    private ServiceClientPort serviceClientPort;

    @MockBean
    private OutboxPort outboxPort;

    @MockBean
    private WebSocketPort webSocketPort;

    private final List<UUID> startedThreads = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        // Clean up any started threads
        for (UUID txId : startedThreads) {
            checkerThreadManager.stopCheckerThread(txId);
        }
        startedThreads.clear();
    }

    @Nested
    @DisplayName("Scenario: Checker thread lifecycle management")
    class CheckerThreadLifecycle {

        @Test
        @DisplayName("Given a new transaction, When checker thread is started, Then it should be tracked")
        void shouldTrackStartedCheckerThread() {
            // Given: A new transaction
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            Map<ServiceName, Integer> timeouts = Map.of(
                    ServiceName.CREDIT_CARD, 30,
                    ServiceName.INVENTORY, 60,
                    ServiceName.LOGISTICS, 120
            );

            // When: Checker thread is started
            checkerThreadManager.startCheckerThread(txId, orderId, timeouts);
            startedThreads.add(txId);

            // Then: Thread is tracked
            assertThat(checkerThreadManager.hasActiveThread(txId)).isTrue();
        }

        @Test
        @DisplayName("Given an active checker thread, When it is stopped, Then it should no longer be tracked")
        void shouldRemoveStoppedCheckerThread() {
            // Given: An active checker thread
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            Map<ServiceName, Integer> timeouts = Map.of(ServiceName.CREDIT_CARD, 30);

            checkerThreadManager.startCheckerThread(txId, orderId, timeouts);
            assertThat(checkerThreadManager.hasActiveThread(txId)).isTrue();

            // When: Checker thread is stopped
            checkerThreadManager.stopCheckerThread(txId);

            // Then: Thread is no longer tracked
            assertThat(checkerThreadManager.hasActiveThread(txId)).isFalse();
        }

        @Test
        @DisplayName("Given multiple transactions, When each starts a checker, Then all are tracked independently")
        void shouldTrackMultipleCheckersIndependently() {
            // Given: Multiple transactions
            UUID txId1 = UUID.randomUUID();
            UUID txId2 = UUID.randomUUID();
            UUID orderId1 = UUID.randomUUID();
            UUID orderId2 = UUID.randomUUID();
            Map<ServiceName, Integer> timeouts = Map.of(ServiceName.CREDIT_CARD, 30);

            // When: Both start checker threads
            checkerThreadManager.startCheckerThread(txId1, orderId1, timeouts);
            checkerThreadManager.startCheckerThread(txId2, orderId2, timeouts);
            startedThreads.add(txId1);
            startedThreads.add(txId2);

            // Then: Both are tracked
            assertThat(checkerThreadManager.hasActiveThread(txId1)).isTrue();
            assertThat(checkerThreadManager.hasActiveThread(txId2)).isTrue();
            assertThat(checkerThreadManager.getActiveThreadCount()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Given an active checker thread, When starting same thread again, Then it should not duplicate")
        void shouldNotDuplicateCheckerThread() {
            // Given: An active checker thread
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            Map<ServiceName, Integer> timeouts = Map.of(ServiceName.CREDIT_CARD, 30);

            checkerThreadManager.startCheckerThread(txId, orderId, timeouts);
            startedThreads.add(txId);
            int initialCount = checkerThreadManager.getActiveThreadCount();

            // When: Trying to start the same thread again
            checkerThreadManager.startCheckerThread(txId, orderId, timeouts);

            // Then: Count should remain the same (no duplicate)
            assertThat(checkerThreadManager.getActiveThreadCount()).isEqualTo(initialCount);
        }
    }

    @Nested
    @DisplayName("Scenario: Shutdown management")
    class ShutdownManagement {

        @Test
        @DisplayName("Given multiple active threads, When shutdown is called, Then all threads should be stopped")
        void shouldStopAllThreadsOnShutdown() {
            // Given: Multiple active threads
            UUID txId1 = UUID.randomUUID();
            UUID txId2 = UUID.randomUUID();
            UUID txId3 = UUID.randomUUID();
            Map<ServiceName, Integer> timeouts = Map.of(ServiceName.CREDIT_CARD, 30);

            checkerThreadManager.startCheckerThread(txId1, UUID.randomUUID(), timeouts);
            checkerThreadManager.startCheckerThread(txId2, UUID.randomUUID(), timeouts);
            checkerThreadManager.startCheckerThread(txId3, UUID.randomUUID(), timeouts);

            assertThat(checkerThreadManager.getActiveThreadCount()).isGreaterThanOrEqualTo(3);

            // When: Shutdown is called
            checkerThreadManager.shutdownAll();

            // Then: All threads should be stopped
            assertThat(checkerThreadManager.hasActiveThread(txId1)).isFalse();
            assertThat(checkerThreadManager.hasActiveThread(txId2)).isFalse();
            assertThat(checkerThreadManager.hasActiveThread(txId3)).isFalse();
            assertThat(checkerThreadManager.getActiveThreadCount()).isEqualTo(0);
        }
    }
}
