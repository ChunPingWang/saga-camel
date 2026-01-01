package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO from rollback endpoint.
 * <p>
 * Rollback operations are idempotent:
 * - Returns success if rollback succeeded
 * - Returns success if transaction never existed
 * - Returns success if already rolled back
 */
public record RollbackResponse(
        UUID txId,
        boolean success,
        String message,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp
) {
    public static RollbackResponse success(UUID txId, String message) {
        return new RollbackResponse(txId, true, message, LocalDateTime.now());
    }

    public static RollbackResponse rolledBack(UUID txId) {
        return new RollbackResponse(txId, true, "Rolled back", LocalDateTime.now());
    }

    public static RollbackResponse noActionNeeded(UUID txId) {
        return new RollbackResponse(txId, true, "No action needed", LocalDateTime.now());
    }

    public static RollbackResponse alreadyRolledBack(UUID txId) {
        return new RollbackResponse(txId, true, "Already rolled back", LocalDateTime.now());
    }

    public static RollbackResponse failure(UUID txId, String message) {
        return new RollbackResponse(txId, false, message, LocalDateTime.now());
    }
}
