package com.ecommerce.order.domain.event;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event representing a saga state change.
 * Published when transaction status changes.
 */
public record TransactionEvent(
        UUID eventId,
        UUID txId,
        UUID orderId,
        String eventType,
        ServiceName serviceName,
        TransactionStatus status,
        String message,
        LocalDateTime timestamp
) {
    // Event types
    public static final String SAGA_STARTED = "SAGA_STARTED";
    public static final String SERVICE_PROCESSING = "SERVICE_PROCESSING";
    public static final String SERVICE_SUCCESS = "SERVICE_SUCCESS";
    public static final String SERVICE_FAILED = "SERVICE_FAILED";
    public static final String ROLLBACK_STARTED = "ROLLBACK_STARTED";
    public static final String ROLLBACK_SUCCESS = "ROLLBACK_SUCCESS";
    public static final String ROLLBACK_FAILED = "ROLLBACK_FAILED";
    public static final String SAGA_COMPLETED = "SAGA_COMPLETED";
    public static final String SAGA_ROLLED_BACK = "SAGA_ROLLED_BACK";

    public static TransactionEvent sagaStarted(UUID txId, UUID orderId) {
        return new TransactionEvent(
                UUID.randomUUID(), txId, orderId,
                SAGA_STARTED, ServiceName.SAGA, TransactionStatus.U,
                "Saga started", LocalDateTime.now()
        );
    }

    public static TransactionEvent serviceProcessing(UUID txId, UUID orderId, ServiceName serviceName) {
        return new TransactionEvent(
                UUID.randomUUID(), txId, orderId,
                SERVICE_PROCESSING, serviceName, TransactionStatus.U,
                "Processing: " + serviceName.getDisplayName(), LocalDateTime.now()
        );
    }

    public static TransactionEvent serviceSuccess(UUID txId, UUID orderId, ServiceName serviceName) {
        return new TransactionEvent(
                UUID.randomUUID(), txId, orderId,
                SERVICE_SUCCESS, serviceName, TransactionStatus.S,
                serviceName.getDisplayName() + " completed successfully", LocalDateTime.now()
        );
    }

    public static TransactionEvent serviceFailed(UUID txId, UUID orderId, ServiceName serviceName, String error) {
        return new TransactionEvent(
                UUID.randomUUID(), txId, orderId,
                SERVICE_FAILED, serviceName, TransactionStatus.F,
                serviceName.getDisplayName() + " failed: " + error, LocalDateTime.now()
        );
    }

    public static TransactionEvent rollbackStarted(UUID txId, UUID orderId) {
        return new TransactionEvent(
                UUID.randomUUID(), txId, orderId,
                ROLLBACK_STARTED, ServiceName.SAGA, null,
                "Rollback started", LocalDateTime.now()
        );
    }

    public static TransactionEvent rollbackSuccess(UUID txId, UUID orderId, ServiceName serviceName) {
        return new TransactionEvent(
                UUID.randomUUID(), txId, orderId,
                ROLLBACK_SUCCESS, serviceName, TransactionStatus.R,
                serviceName.getDisplayName() + " rolled back", LocalDateTime.now()
        );
    }

    public static TransactionEvent rollbackFailed(UUID txId, UUID orderId, ServiceName serviceName, String error) {
        return new TransactionEvent(
                UUID.randomUUID(), txId, orderId,
                ROLLBACK_FAILED, serviceName, TransactionStatus.RF,
                serviceName.getDisplayName() + " rollback failed: " + error, LocalDateTime.now()
        );
    }

    public static TransactionEvent sagaCompleted(UUID txId, UUID orderId) {
        return new TransactionEvent(
                UUID.randomUUID(), txId, orderId,
                SAGA_COMPLETED, ServiceName.SAGA, TransactionStatus.S,
                "Order transaction completed", LocalDateTime.now()
        );
    }

    public static TransactionEvent sagaRolledBack(UUID txId, UUID orderId) {
        return new TransactionEvent(
                UUID.randomUUID(), txId, orderId,
                SAGA_ROLLED_BACK, ServiceName.SAGA, TransactionStatus.D,
                "Order transaction rolled back", LocalDateTime.now()
        );
    }
}
