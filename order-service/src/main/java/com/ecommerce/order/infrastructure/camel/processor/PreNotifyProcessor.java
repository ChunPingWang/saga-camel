package com.ecommerce.order.infrastructure.camel.processor;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Processor that runs before calling a downstream service.
 * Records the "processing" status and sends WebSocket notification.
 */
@Component
public class PreNotifyProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(PreNotifyProcessor.class);

    private final TransactionLogPort transactionLogPort;
    private final WebSocketPort webSocketPort;

    public PreNotifyProcessor(TransactionLogPort transactionLogPort, WebSocketPort webSocketPort) {
        this.transactionLogPort = transactionLogPort;
        this.webSocketPort = webSocketPort;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        UUID txId = exchange.getProperty("txId", UUID.class);
        UUID orderId = exchange.getProperty("orderId", UUID.class);
        ServiceName currentService = exchange.getProperty("currentService", ServiceName.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = exchange.getProperty("payload", Map.class);

        log.info("Pre-notify: Preparing to call service {} for txId={}", currentService, txId);

        // Record that we're about to call this service
        transactionLogPort.recordStatus(txId, orderId, currentService, TransactionStatus.U);

        // Send WebSocket notification that we're processing this service
        webSocketPort.sendProcessing(txId, orderId, currentService);

        // Create the notify request
        NotifyRequest request = NotifyRequest.of(txId, orderId, payload);
        exchange.getMessage().setBody(request);
    }
}
