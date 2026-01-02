package com.ecommerce.order.application.port.out;

import com.ecommerce.common.domain.ServiceName;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Output port for managing transaction checker threads.
 * This abstraction allows the application layer to trigger checker operations
 * without depending on infrastructure layer implementations.
 */
public interface CheckerPort {

    /**
     * Starts a new checker thread for the given transaction.
     * If a thread already exists for this transaction, this method does nothing.
     *
     * @param txId the transaction ID
     * @param orderId the order ID
     * @param timeouts timeout thresholds per service
     */
    void startCheckerThread(UUID txId, UUID orderId, Map<ServiceName, Integer> timeouts);

    /**
     * Stops the checker thread for the given transaction.
     *
     * @param txId the transaction ID
     */
    void stopCheckerThread(UUID txId);

    /**
     * Checks if there is an active checker thread for the given transaction.
     *
     * @param txId the transaction ID
     * @return true if an active thread exists
     */
    boolean hasActiveThread(UUID txId);

    /**
     * Returns the number of active checker threads.
     *
     * @return the count of active threads
     */
    int getActiveThreadCount();

    /**
     * Returns the set of transaction IDs with active checker threads.
     *
     * @return set of active transaction IDs
     */
    Set<UUID> getActiveTransactionIds();
}
