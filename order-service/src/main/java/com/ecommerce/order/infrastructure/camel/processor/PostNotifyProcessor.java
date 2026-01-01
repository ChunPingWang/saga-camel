package com.ecommerce.order.infrastructure.camel.processor;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Processor that runs after calling a downstream service.
 * Records the success/failure status and sends WebSocket notification.
 */
@Component
public class PostNotifyProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(PostNotifyProcessor.class);

    private final TransactionLogPort transactionLogPort;
    private final WebSocketPort webSocketPort;

    public PostNotifyProcessor(TransactionLogPort transactionLogPort, WebSocketPort webSocketPort) {
        this.transactionLogPort = transactionLogPort;
        this.webSocketPort = webSocketPort;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        UUID txId = exchange.getProperty("txId", UUID.class);
        UUID orderId = exchange.getProperty("orderId", UUID.class);
        ServiceName currentService = exchange.getProperty("currentService", ServiceName.class);
        NotifyResponse response = exchange.getMessage().getBody(NotifyResponse.class);

        log.info("Post-notify: Received response from service {} for txId={}: success={}",
                currentService, txId, response.success());

        if (response.success()) {
            // Record success
            transactionLogPort.recordStatus(txId, orderId, currentService, TransactionStatus.S);
            webSocketPort.sendSuccess(txId, orderId, currentService);

            // Add to successful services list for potential rollback
            exchange.getProperty("successfulServices", java.util.List.class).add(currentService);
        } else {
            // Record failure
            transactionLogPort.recordStatusWithError(txId, orderId, currentService,
                    TransactionStatus.F, response.message());
            webSocketPort.sendFailure(txId, orderId, currentService, response.message());

            // Set failure flag to trigger rollback
            exchange.setProperty("sagaFailed", true);
            exchange.setProperty("failedService", currentService);
            exchange.setProperty("errorMessage", response.message());
        }
    }
}
