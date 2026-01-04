package com.ecommerce.order.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for transaction_log table.
 */
public interface TransactionLogRepository extends JpaRepository<TransactionLogEntity, Long> {

    /**
     * Find all logs for a transaction ordered by creation time.
     */
    List<TransactionLogEntity> findByTxIdOrderByCreatedAtAsc(String txId);

    /**
     * Find the latest log entry for a specific service in a transaction.
     */
    @Query("SELECT t FROM TransactionLogEntity t WHERE t.txId = :txId AND t.serviceName = :serviceName ORDER BY t.createdAt DESC")
    List<TransactionLogEntity> findByTxIdAndServiceNameOrderByCreatedAtDesc(
            @Param("txId") String txId,
            @Param("serviceName") String serviceName
    );

    /**
     * Find successful services for a transaction (latest status is 'S').
     */
    @Query("""
        SELECT DISTINCT t.serviceName FROM TransactionLogEntity t
        WHERE t.txId = :txId AND t.status = 'S'
        AND t.createdAt = (
            SELECT MAX(t2.createdAt) FROM TransactionLogEntity t2
            WHERE t2.txId = t.txId AND t2.serviceName = t.serviceName
        )
    """)
    List<String> findSuccessfulServices(@Param("txId") String txId);

    /**
     * Find transaction IDs with uncommitted status older than the given time.
     */
    @Query("""
        SELECT DISTINCT t.txId FROM TransactionLogEntity t
        WHERE t.status = 'U' AND t.createdAt < :olderThan
    """)
    List<String> findTimedOutTransactions(@Param("olderThan") LocalDateTime olderThan);

    /**
     * Find unfinished transactions that still have at least one U (UNKNOWN) status.
     * Returns txId and orderId for recovery.
     */
    @Query("""
        SELECT DISTINCT t.txId, t.orderId FROM TransactionLogEntity t
        WHERE t.status = 'U'
        AND NOT EXISTS (
            SELECT 1 FROM TransactionLogEntity t2
            WHERE t2.txId = t.txId AND t2.status IN ('S', 'R', 'RF')
            AND t2.serviceName = t.serviceName
        )
    """)
    List<Object[]> findUnfinishedTransactionsWithOrderId();

    /**
     * Check if transaction has a terminal status.
     */
    @Query("""
        SELECT COUNT(t) > 0 FROM TransactionLogEntity t
        WHERE t.txId = :txId AND t.serviceName = 'SAGA'
        AND t.status IN ('S', 'D', 'RF')
    """)
    boolean hasTerminalStatus(@Param("txId") String txId);

    /**
     * Find all logs for an order ID ordered by creation time.
     */
    List<TransactionLogEntity> findByOrderIdOrderByCreatedAtDesc(String orderId);

    /**
     * Find distinct transaction IDs for a given order ID, ordered by earliest creation time.
     */
    @Query("""
        SELECT t.txId FROM TransactionLogEntity t
        WHERE t.orderId = :orderId
        GROUP BY t.txId
        ORDER BY MIN(t.createdAt) DESC
    """)
    List<String> findDistinctTxIdsByOrderId(@Param("orderId") String orderId);
}
