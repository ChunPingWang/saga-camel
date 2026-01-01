package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO from notify endpoint.
 */
public record NotifyResponse(
        UUID txId,
        boolean success,
        String message,
        String serviceReference,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp
) {
    public static NotifyResponse success(UUID txId, String message, String serviceReference) {
        return new NotifyResponse(txId, true, message, serviceReference, LocalDateTime.now());
    }

    public static NotifyResponse failure(UUID txId, String message) {
        return new NotifyResponse(txId, false, message, null, LocalDateTime.now());
    }
}
