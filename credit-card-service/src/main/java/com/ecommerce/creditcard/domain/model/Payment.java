package com.ecommerce.creditcard.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment domain model for credit card processing.
 */
public class Payment {

    public enum PaymentStatus {
        PENDING, AUTHORIZED, CAPTURED, REFUNDED, FAILED
    }

    private final UUID txId;
    private final UUID orderId;
    private final String creditCardNumber;
    private final BigDecimal amount;
    private PaymentStatus status;
    private String authorizationCode;
    private final LocalDateTime createdAt;
    private LocalDateTime processedAt;

    private Payment(UUID txId, UUID orderId, String creditCardNumber, BigDecimal amount) {
        Objects.requireNonNull(txId, "txId is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(creditCardNumber, "creditCardNumber is required");
        Objects.requireNonNull(amount, "amount is required");

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        this.txId = txId;
        this.orderId = orderId;
        this.creditCardNumber = maskCardNumber(creditCardNumber);
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public static Payment create(UUID txId, UUID orderId, String creditCardNumber, BigDecimal amount) {
        return new Payment(txId, orderId, creditCardNumber, amount);
    }

    public void authorize(String authorizationCode) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot authorize payment in status: " + status);
        }
        this.authorizationCode = authorizationCode;
        this.status = PaymentStatus.AUTHORIZED;
        this.processedAt = LocalDateTime.now();
    }

    public void capture() {
        if (this.status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Cannot capture payment in status: " + status);
        }
        this.status = PaymentStatus.CAPTURED;
        this.processedAt = LocalDateTime.now();
    }

    public void refund() {
        if (this.status != PaymentStatus.CAPTURED && this.status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Cannot refund payment in status: " + status);
        }
        this.status = PaymentStatus.REFUNDED;
        this.processedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
        this.processedAt = LocalDateTime.now();
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "*".repeat(cardNumber.length() - 4) + cardNumber.substring(cardNumber.length() - 4);
    }

    public boolean isProcessed() {
        return status == PaymentStatus.CAPTURED || status == PaymentStatus.REFUNDED;
    }

    public boolean canRefund() {
        return status == PaymentStatus.CAPTURED || status == PaymentStatus.AUTHORIZED;
    }

    // Getters
    public UUID getTxId() { return txId; }
    public UUID getOrderId() { return orderId; }
    public String getCreditCardNumber() { return creditCardNumber; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getAuthorizationCode() { return authorizationCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
