package com.ecommerce.order.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA entity for transaction_log table.
 */
@Entity
@Table(name = "transaction_log")
public class TransactionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_id", nullable = false, length = 36)
    private String txId;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "service_name", nullable = false, length = 50)
    private String serviceName;

    @Column(name = "status", nullable = false, length = 2)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    public TransactionLogEntity() {
    }

    public TransactionLogEntity(String txId, String orderId, String serviceName, String status) {
        this.txId = txId;
        this.orderId = orderId;
        this.serviceName = serviceName;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(LocalDateTime notifiedAt) {
        this.notifiedAt = notifiedAt;
    }
}
