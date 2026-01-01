package com.ecommerce.order.adapter.out.websocket;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.adapter.in.websocket.OrderWebSocketHandler;
import com.ecommerce.order.adapter.in.websocket.WebSocketMessage;
import com.ecommerce.order.application.port.out.WebSocketPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Output adapter for WebSocket notifications.
 * Implements WebSocketPort to send real-time updates to clients.
 */
@Component
public class WebSocketAdapter implements WebSocketPort {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAdapter.class);

    private final OrderWebSocketHandler webSocketHandler;

    public WebSocketAdapter(OrderWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void sendNotification(String txId, Object message) {
        log.debug("txId={} - Sending WebSocket notification: {}", txId, message);
        if (message instanceof WebSocketMessage wsMessage) {
            webSocketHandler.sendNotification(txId, wsMessage);
        } else {
            WebSocketMessage wsMessage = new WebSocketMessage(
                    txId,
                    null,
                    "INFO",
                    message != null ? message.toString() : ""
            );
            webSocketHandler.sendNotification(txId, wsMessage);
        }
    }

    @Override
    public void sendStatusUpdate(UUID txId, UUID orderId, TransactionStatus status,
                                  ServiceName currentStep, String message) {
        log.info("txId={} - Sending status update: status={}, step={}, message={}",
                txId, status, currentStep, message);

        String serviceName = currentStep != null ? currentStep.name() : null;
        WebSocketMessage wsMessage = new WebSocketMessage(
                txId.toString(),
                serviceName,
                status.name(),
                message
        );
        webSocketHandler.sendNotification(txId.toString(), wsMessage);
    }
}
