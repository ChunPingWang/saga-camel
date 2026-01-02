package com.ecommerce.order.application.port.out;

import com.ecommerce.common.domain.ServiceName;

import java.util.List;
import java.util.UUID;

/**
 * Output port for executing saga rollback operations.
 * This abstraction allows the infrastructure layer to trigger rollback
 * without directly depending on application layer services.
 */
public interface RollbackExecutorPort {

    /**
     * Execute rollback for all successful services in reverse order.
     *
     * @param txId the transaction ID
     * @param orderId the order ID
     * @param successfulServices list of services that completed successfully (in execution order)
     */
    void executeRollback(UUID txId, UUID orderId, List<ServiceName> successfulServices);

    /**
     * Get list of services that completed successfully for a transaction.
     *
     * @param txId the transaction ID
     * @return list of successful service names in execution order
     */
    List<ServiceName> getSuccessfulServices(UUID txId);

    /**
     * Execute rollback with retry logic and admin notification on exhausted retries.
     *
     * @param txId the transaction ID
     * @param orderId the order ID
     * @param successfulServices list of services that completed successfully
     * @param maxRetries maximum number of retry attempts per service
     */
    void executeRollbackWithRetry(UUID txId, UUID orderId, List<ServiceName> successfulServices, int maxRetries);
}
