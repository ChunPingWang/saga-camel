package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for rollback endpoint called by saga orchestrator.
 */
public record RollbackRequest(
        @NotNull(message = "Transaction ID is required")
        UUID txId,

        @NotNull(message = "Order ID is required")
        UUID orderId,

        String reason,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp
) {
    public RollbackRequest {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public static RollbackRequest of(UUID txId, UUID orderId, String reason) {
        return new RollbackRequest(txId, orderId, reason, LocalDateTime.now());
    }
}
