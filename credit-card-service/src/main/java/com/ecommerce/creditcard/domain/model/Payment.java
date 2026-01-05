package com.ecommerce.creditcard.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment domain model representing a credit card payment.
 */
public class Payment {

    private final UUID txId;
    private final UUID orderId;
    private final BigDecimal amount;
    private final String creditCardNumber;
    private final PaymentStatus status;
    private final String referenceNumber;
    private final LocalDateTime processedAt;

    public Payment(UUID txId, UUID orderId, BigDecimal amount, String creditCardNumber,
                   PaymentStatus status, String referenceNumber, LocalDateTime processedAt) {
        this.txId = txId;
        this.orderId = orderId;
        this.amount = amount;
        this.creditCardNumber = maskCardNumber(creditCardNumber);
        this.status = status;
        this.referenceNumber = referenceNumber;
        this.processedAt = processedAt;
    }

    public static Payment process(UUID txId, UUID orderId, BigDecimal amount, String creditCardNumber) {
        String referenceNumber = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new Payment(txId, orderId, amount, creditCardNumber,
                PaymentStatus.APPROVED, referenceNumber, LocalDateTime.now());
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    public UUID getTxId() { return txId; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCreditCardNumber() { return creditCardNumber; }
    public PaymentStatus getStatus() { return status; }
    public String getReferenceNumber() { return referenceNumber; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public boolean isApproved() { return status == PaymentStatus.APPROVED; }

    public enum PaymentStatus {
        PENDING, APPROVED, DECLINED, REFUNDED
    }
}
