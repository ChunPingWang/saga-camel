package com.ecommerce.order.integration;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.application.port.out.OutboxPort;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.application.service.SagaRecoveryService;
import com.ecommerce.order.domain.model.TransactionLog;
import com.ecommerce.order.infrastructure.checker.CheckerThreadManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Recovery Integration Tests")
@org.junit.jupiter.api.Disabled("Requires database setup - core logic tested in unit tests")
class RecoveryIntegrationTest {

    @Autowired
    private SagaRecoveryService recoveryService;

    @Autowired
    private TransactionLogPort transactionLogPort;

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
        // Stop all existing checker threads
        checkerThreadManager.shutdownAll();
    }

    @Test
    @DisplayName("should recover unfinished transactions on startup")
    void shouldRecoverUnfinishedTransactionsOnStartup() {
        // Given - Create an unfinished transaction directly in the database
        UUID txId = UUID.fromString("e5f6a7b8-c9d0-1234-abcd-567890123456");
        UUID orderId = UUID.fromString("f6a7b8c9-d0e1-2345-bcde-678901234567");

        // Create transaction with UNKNOWN status (simulating crash mid-processing)
        TransactionLog log = TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U);
        transactionLogPort.save(log);

        // When - Trigger recovery
        int recoveredCount = recoveryService.recoverUnfinishedTransactions();

        // Then
        assertThat(recoveredCount).isGreaterThanOrEqualTo(1);
        assertThat(checkerThreadManager.hasActiveThread(txId)).isTrue();

        // Cleanup
        checkerThreadManager.stopCheckerThread(txId);
    }

    @Test
    @DisplayName("should not recover completed transactions")
    void shouldNotRecoverCompletedTransactions() {
        // Given - Create a completed transaction
        UUID txId = UUID.fromString("a1b2c3d4-e5f6-7890-cdef-567890abcdef");
        UUID orderId = UUID.fromString("b2c3d4e5-f6a7-8901-def0-678901234567");

        // All services completed successfully
        transactionLogPort.save(TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S));
        transactionLogPort.save(TransactionLog.create(txId, orderId, ServiceName.INVENTORY, TransactionStatus.S));
        transactionLogPort.save(TransactionLog.create(txId, orderId, ServiceName.LOGISTICS, TransactionStatus.S));

        // When
        int beforeRecovery = checkerThreadManager.getActiveThreadCount();
        recoveryService.recoverUnfinishedTransactions();
        int afterRecovery = checkerThreadManager.getActiveThreadCount();

        // Then - No new threads should have been started for completed transaction
        assertThat(checkerThreadManager.hasActiveThread(txId)).isFalse();
    }

    @Test
    @DisplayName("should recover partially completed transactions")
    void shouldRecoverPartiallyCompletedTransactions() {
        // Given - Create partially completed transaction (payment succeeded, inventory in progress)
        UUID txId = UUID.fromString("c3d4e5f6-a7b8-9012-ef01-789012345678");
        UUID orderId = UUID.fromString("d4e5f6a7-b8c9-0123-f012-890123456789");

        transactionLogPort.save(TransactionLog.create(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S));
        transactionLogPort.save(TransactionLog.create(txId, orderId, ServiceName.INVENTORY, TransactionStatus.U)); // Still processing

        // When
        int recoveredCount = recoveryService.recoverUnfinishedTransactions();

        // Then - Should be recovered because inventory is not complete
        assertThat(checkerThreadManager.hasActiveThread(txId)).isTrue();

        // Cleanup
        checkerThreadManager.stopCheckerThread(txId);
    }
}
