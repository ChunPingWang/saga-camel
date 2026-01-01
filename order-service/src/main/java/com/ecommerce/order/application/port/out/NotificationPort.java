package com.ecommerce.order.application.port.out;

import com.ecommerce.common.domain.ServiceName;

import java.util.UUID;

/**
 * Output port for admin notifications (email alerts).
 */
public interface NotificationPort {

    /**
     * Send an alert to administrators about a rollback failure.
     *
     * @param txId        Transaction ID
     * @param orderId     Order ID
     * @param serviceName Service that failed to rollback
     * @param errorMessage Error details
     * @param retryCount  Number of retry attempts made
     */
    void sendRollbackFailureAlert(UUID txId, UUID orderId, ServiceName serviceName,
                                   String errorMessage, int retryCount);
}
