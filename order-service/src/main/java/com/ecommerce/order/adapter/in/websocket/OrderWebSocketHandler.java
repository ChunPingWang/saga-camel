package com.ecommerce.order.adapter.in.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket handler for order progress notifications.
 * Manages WebSocket sessions per transaction ID and sends real-time status updates.
 */
public class OrderWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, List<WebSocketSession>> sessionsByTxId = new ConcurrentHashMap<>();

    public OrderWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String txId = extractTxId(session);
        if (txId != null) {
            sessionsByTxId.computeIfAbsent(txId, k -> new CopyOnWriteArrayList<>()).add(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String txId = extractTxId(session);
        if (txId != null) {
            List<WebSocketSession> sessions = sessionsByTxId.get(txId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByTxId.remove(txId);
                }
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    public void sendNotification(String txId, WebSocketMessage message) {
        List<WebSocketSession> sessions = sessionsByTxId.get(txId);
        if (sessions == null) {
            return;
        }

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    String json = objectMapper.writeValueAsString(message);
                    session.sendMessage(new TextMessage(json));
                } catch (Exception e) {
                    // Log and continue to next session
                }
            }
        }
    }

    public boolean hasSession(String txId) {
        List<WebSocketSession> sessions = sessionsByTxId.get(txId);
        return sessions != null && !sessions.isEmpty();
    }

    public int getSessionCount(String txId) {
        List<WebSocketSession> sessions = sessionsByTxId.get(txId);
        return sessions != null ? sessions.size() : 0;
    }

    private String extractTxId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        // Expected format: /ws/orders/{txId}
        if (path != null && path.startsWith("/ws/orders/")) {
            return path.substring("/ws/orders/".length());
        }
        return null;
    }
}
