package com.ecommerce.order.adapter.in.web.dto;

/**
 * Simple message response DTO.
 */
public record MessageResponse(
        String message
) {
    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
