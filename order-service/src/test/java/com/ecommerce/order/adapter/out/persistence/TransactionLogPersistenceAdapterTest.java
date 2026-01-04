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

    @Test
    @DisplayName("should find all transaction logs by order ID")
    void shouldFindByOrderId() {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID txId1 = UUID.randomUUID();
        UUID txId2 = UUID.randomUUID();

        // First transaction
        adapter.recordStatus(txId1, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U);
        adapter.recordStatus(txId1, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S);
        adapter.recordStatus(txId1, orderId, ServiceName.INVENTORY, TransactionStatus.U);

        // Second transaction (same order, different txId - retry scenario)
        adapter.recordStatus(txId2, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U);
        adapter.recordStatus(txId2, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S);

        // When
        List<TransactionLog> logs = adapter.findByOrderId(orderId);

        // Then
        assertEquals(5, logs.size());
        // All logs should belong to the same order
        assertTrue(logs.stream().allMatch(log -> log.getOrderId().equals(orderId)));
    }

    @Test
    @DisplayName("should return empty list when no transactions for order")
    void shouldReturnEmptyListWhenNoTransactionsForOrder() {
        // Given
        UUID nonExistentOrderId = UUID.randomUUID();

        // When
        List<TransactionLog> logs = adapter.findByOrderId(nonExistentOrderId);

        // Then
        assertTrue(logs.isEmpty());
    }

    @Test
    @DisplayName("should find distinct transaction IDs by order ID")
    void shouldFindDistinctTxIdsByOrderId() {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID txId1 = UUID.randomUUID();
        UUID txId2 = UUID.randomUUID();
        UUID txId3 = UUID.randomUUID();

        // Create logs for multiple transactions under same order
        adapter.recordStatus(txId1, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U);
        adapter.recordStatus(txId1, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S);
        adapter.recordStatus(txId2, orderId, ServiceName.CREDIT_CARD, TransactionStatus.U);
        adapter.recordStatus(txId2, orderId, ServiceName.INVENTORY, TransactionStatus.F);
        adapter.recordStatus(txId3, orderId, ServiceName.CREDIT_CARD, TransactionStatus.S);

        // When
        List<UUID> txIds = adapter.findDistinctTxIdsByOrderId(orderId);

        // Then
        assertEquals(3, txIds.size());
        assertTrue(txIds.contains(txId1));
        assertTrue(txIds.contains(txId2));
        assertTrue(txIds.contains(txId3));
    }

    @Test
    @DisplayName("should return empty list when no distinct txIds for order")
    void shouldReturnEmptyListWhenNoDistinctTxIdsForOrder() {
        // Given
        UUID nonExistentOrderId = UUID.randomUUID();

        // When
        List<UUID> txIds = adapter.findDistinctTxIdsByOrderId(nonExistentOrderId);

        // Then
        assertTrue(txIds.isEmpty());
    }

    @Test
    @DisplayName("should not include transactions from other orders")
    void shouldNotIncludeTransactionsFromOtherOrders() {
        // Given
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        UUID txId1 = UUID.randomUUID();
        UUID txId2 = UUID.randomUUID();

        // Transactions for order 1
        adapter.recordStatus(txId1, orderId1, ServiceName.CREDIT_CARD, TransactionStatus.S);

        // Transactions for order 2
        adapter.recordStatus(txId2, orderId2, ServiceName.CREDIT_CARD, TransactionStatus.S);

        // When - query for order 1
        List<UUID> txIdsForOrder1 = adapter.findDistinctTxIdsByOrderId(orderId1);

        // Then - should only contain txId1
        assertEquals(1, txIdsForOrder1.size());
        assertTrue(txIdsForOrder1.contains(txId1));
        assertFalse(txIdsForOrder1.contains(txId2));
    }
}
