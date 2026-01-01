package com.ecommerce.order.domain.model;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Transaction log domain entity.
 * Immutable record of a saga state transition (Event Sourcing pattern).
 * <p>
 * Each state change creates a new record - never update existing records.
 */
public class TransactionLog {

    private Long id;
    private final UUID txId;
    private final UUID orderId;
    private final ServiceName serviceName;
    private final TransactionStatus status;
    private final String errorMessage;
    private final int retryCount;
    private final LocalDateTime createdAt;
    private LocalDateTime notifiedAt;

    private TransactionLog(UUID txId, UUID orderId, ServiceName serviceName,
                           TransactionStatus status, String errorMessage, int retryCount) {
        validateTxId(txId);
        validateOrderId(orderId);
        Objects.requireNonNull(serviceName, "Service name is required");
        Objects.requireNonNull(status, "Status is required");

        this.txId = txId;
        this.orderId = orderId;
        this.serviceName = serviceName;
        this.status = status;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        this.createdAt = LocalDateTime.now();
    }

    // Factory methods

    public static TransactionLog create(UUID txId, UUID orderId, ServiceName serviceName, TransactionStatus status) {
        return new TransactionLog(txId, orderId, serviceName, status, null, 0);
    }

    public static TransactionLog create(String txId, String orderId, ServiceName serviceName, TransactionStatus status) {
        return new TransactionLog(UUID.fromString(txId), UUID.fromString(orderId), serviceName, status, null, 0);
    }

    public static TransactionLog createWithError(UUID txId, UUID orderId, ServiceName serviceName,
                                                  TransactionStatus status, String errorMessage) {
        return new TransactionLog(txId, orderId, serviceName, status, errorMessage, 0);
    }

    public static TransactionLog createWithRetry(UUID txId, UUID orderId, ServiceName serviceName,
                                                  TransactionStatus status, String errorMessage, int retryCount) {
        return new TransactionLog(txId, orderId, serviceName, status, errorMessage, retryCount);
    }

    // Validation methods

    private void validateTxId(UUID txId) {
        if (txId == null) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
    }

    private void validateOrderId(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID is required");
        }
    }

    // State query methods

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public boolean isSuccess() {
        return status.isSuccess();
    }

    public boolean isFailure() {
        return status.isFailure();
    }

    // Getters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getTxId() {
        return txId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ServiceName getServiceName() {
        return serviceName;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(LocalDateTime notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    @Override
    public String toString() {
        return String.format("TransactionLog[txId=%s, service=%s, status=%s]",
                txId, serviceName, status);
    }
}
