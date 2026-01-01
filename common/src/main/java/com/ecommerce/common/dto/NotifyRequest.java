package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for notify endpoint called by saga orchestrator.
 */
public record NotifyRequest(
        @NotNull(message = "Transaction ID is required")
        UUID txId,

        @NotNull(message = "Order ID is required")
        UUID orderId,

        Map<String, Object> payload,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp
) {
    public NotifyRequest {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public static NotifyRequest of(UUID txId, UUID orderId, Map<String, Object> payload) {
        return new NotifyRequest(txId, orderId, payload, LocalDateTime.now());
    }

    // Convenience methods to extract common payload fields
    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> items() {
        Object items = payload != null ? payload.get("items") : null;
        return items != null ? (java.util.List<Map<String, Object>>) items : java.util.List.of();
    }

    public java.math.BigDecimal totalAmount() {
        Object amount = payload != null ? payload.get("totalAmount") : null;
        if (amount instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) amount;
        } else if (amount instanceof Number) {
            return java.math.BigDecimal.valueOf(((Number) amount).doubleValue());
        } else if (amount instanceof String) {
            return new java.math.BigDecimal((String) amount);
        }
        return java.math.BigDecimal.ZERO;
    }

    public String creditCardNumber() {
        Object cc = payload != null ? payload.get("creditCardNumber") : null;
        return cc != null ? cc.toString() : "";
    }

    public String userId() {
        Object user = payload != null ? payload.get("userId") : null;
        return user != null ? user.toString() : "";
    }
}
