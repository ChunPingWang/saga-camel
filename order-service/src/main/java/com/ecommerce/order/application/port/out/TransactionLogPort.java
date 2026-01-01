package com.ecommerce.order.application.port.out;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.domain.model.TransactionLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port for transaction log persistence.
 * Follows Event Sourcing pattern - append only, no updates.
 */
public interface TransactionLogPort {

    /**
     * Save a transaction log entry (append-only).
     */
    TransactionLog save(TransactionLog transactionLog);

    /**
     * Find the latest status for each service in a transaction.
     */
    List<TransactionLog> findLatestByTxId(String txId);

    /**
     * Record a new transaction status (append-only).
     */
    TransactionLog recordStatus(UUID txId, UUID orderId, ServiceName serviceName, TransactionStatus status);

    /**
     * Record a new transaction status with error message.
     */
    TransactionLog recordStatusWithError(UUID txId, UUID orderId, ServiceName serviceName,
                                          TransactionStatus status, String errorMessage);

    /**
     * Record a new transaction status with retry count.
     */
    TransactionLog recordStatusWithRetry(UUID txId, UUID orderId, ServiceName serviceName,
                                          TransactionStatus status, String errorMessage, int retryCount);

    /**
     * Find all transaction logs for a given transaction ID.
     */
    List<TransactionLog> findByTxId(UUID txId);

    /**
     * Find services that completed successfully for a transaction.
     */
    List<ServiceName> findSuccessfulServices(UUID txId);

    /**
     * Get the latest status for each service in a transaction.
     */
    Map<ServiceName, TransactionStatus> getLatestStatuses(UUID txId);

    /**
     * Get the latest log entry for a specific service in a transaction.
     */
    Optional<TransactionLog> getLatestForService(UUID txId, ServiceName serviceName);

    /**
     * Find transactions with uncommitted status older than the given time.
     */
    List<UUID> findTimedOutTransactions(LocalDateTime olderThan);

    /**
     * Find all unfinished transactions (for recovery on restart).
     * Returns both txId and orderId for checker thread startup.
     */
    List<UnfinishedTransaction> findUnfinishedTransactions();

    /**
     * Record representing an unfinished transaction.
     */
    record UnfinishedTransaction(UUID txId, UUID orderId) {}

    /**
     * Record notification timestamp for admin alerts.
     */
    void recordNotifiedAt(UUID txId, ServiceName serviceName, LocalDateTime notifiedAt);

    /**
     * Check if all services completed successfully for a transaction.
     */
    boolean isTransactionComplete(UUID txId, List<ServiceName> expectedServices);
}
