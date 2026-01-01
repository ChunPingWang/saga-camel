package com.ecommerce.order.adapter.in.websocket;

/**
 * WebSocket message DTO for real-time notifications to clients.
 */
public record WebSocketMessage(
        String txId,
        String serviceName,
        String status,
        String message
) {}
