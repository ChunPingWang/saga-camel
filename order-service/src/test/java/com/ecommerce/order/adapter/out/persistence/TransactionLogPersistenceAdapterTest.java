package com.ecommerce.order.adapter.out.persistence;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.domain.model.TransactionLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TransactionLogPersistenceAdapter.
 */
@DataJpaTest
@Import(TransactionLogPersistenceAdapter.class)
@ActiveProfiles("test")
class TransactionLogPersistenceAdapterTest {

    @Autowired
    private TransactionLogPersistenceAdapter adapter;

    @Autowired
    private TransactionLogRepository repository;

    @Test
    @DisplayName("should record transaction status")
    void shouldRecordTransactionStatus() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        // When
        TransactionLog log = adapter.recordStatus(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U);

        // Then
        assertNotNull(log);
        assertEquals(txId, log.getTxId());
        assertEquals(orderId, log.getOrderId());
        assertEquals(ServiceName.CREDIT_CARD, log.getServiceName());
        assertEquals(TransactionStatus.U, log.getStatus());

        // Verify persisted
        List<TransactionLogEntity> entities = repository.findByTxIdOrderByCreatedAtAsc(txId.toString());
        assertEquals(1, entities.size());
    }

    @Test
    @DisplayName("should record status with error message")
    void shouldRecordStatusWithErrorMessage() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String errorMessage = "Payment declined";

        // When
        TransactionLog log = adapter.recordStatusWithError(
                txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.F, errorMessage
        );

        // Then
        assertEquals(TransactionStatus.F, log.getStatus());
        assertEquals(errorMessage, log.getErrorMessage());
    }

    @Test
    @DisplayName("should find successful services")
    void shouldFindSuccessfulServices() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        adapter.recordStatus(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U);
        adapter.recordStatus(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S);
        adapter.recordStatus(txId, orderId, ServiceName.INVENTORY, TransactionStatus.U);
        adapter.recordStatus(txId, orderId, ServiceName.INVENTORY, TransactionStatus.S);

        // When
        List<ServiceName> successfulServices = adapter.findSuccessfulServices(txId);

        // Then
        assertEquals(2, successfulServices.size());
        assertTrue(successfulServices.contains(ServiceName.CREDIT_CARD));
        assertTrue(successfulServices.contains(ServiceName.INVENTORY));
    }

    @Test
    @DisplayName("should get latest statuses for all services")
    void shouldGetLatestStatuses() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        adapter.recordStatus(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U);
        adapter.recordStatus(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S);
        adapter.recordStatus(txId, orderId, ServiceName.INVENTORY, TransactionStatus.U);
        adapter.recordStatus(txId, orderId, ServiceName.INVENTORY, TransactionStatus.F);

        // When
        Map<ServiceName, TransactionStatus> latestStatuses = adapter.getLatestStatuses(txId);

        // Then
        assertEquals(2, latestStatuses.size());
        assertEquals(TransactionStatus.S, latestStatuses.get(ServiceName.CREDIT_CARD));
        assertEquals(TransactionStatus.F, latestStatuses.get(ServiceName.INVENTORY));
    }

    @Test
    @DisplayName("should check transaction completion")
    void shouldCheckTransactionCompletion() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        adapter.recordStatus(txId, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S);
        adapter.recordStatus(txId, orderId, ServiceName.INVENTORY, TransactionStatus.S);

        // When - all expected services completed
        boolean complete = adapter.isTransactionComplete(txId,
                List.of(ServiceName.CREDIT_CARD, ServiceName.INVENTORY));

        // Then
        assertTrue(complete);

        // When - missing a service
        boolean incomplete = adapter.isTransactionComplete(txId,
                List.of(ServiceName.CREDIT_CARD, ServiceName.INVENTORY, ServiceName.LOGISTICS));

        // Then
        assertFalse(incomplete);
    }
}
