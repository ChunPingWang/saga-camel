package com.ecommerce.order.infrastructure.poller;

import com.ecommerce.order.application.port.out.OutboxPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbox poller that picks up unprocessed events and triggers Camel routes.
 * Implements the Outbox Pattern for reliable event delivery.
 */
@Component
@EnableScheduling
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxPort outboxPort;
    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPoller(OutboxPort outboxPort, ProducerTemplate producerTemplate, ObjectMapper objectMapper) {
        this.outboxPort = outboxPort;
        this.producerTemplate = producerTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void pollOutbox() {
        List<OutboxPort.OutboxEvent> events = outboxPort.findUnprocessed();

        for (OutboxPort.OutboxEvent event : events) {
            processEvent(event);
        }
    }

    private void processEvent(OutboxPort.OutboxEvent event) {
        String txId = event.txId().toString();
        MDC.put("txId", txId);

        try {
            log.info("Processing outbox event: eventId={}, txId={}, orderId={}, eventType={}",
                    event.id(), txId, event.orderId(), event.eventType());

            // Build the message to send to Camel
            Map<String, Object> message = new HashMap<>();
            message.put("txId", txId);
            message.put("orderId", event.orderId().toString());
            message.put("eventType", event.eventType());

            // Parse the payload and merge it into the message
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(event.payload(), Map.class);
                message.putAll(payload);
            } catch (Exception e) {
                log.warn("Could not parse payload as JSON, using raw string: {}", e.getMessage());
                message.put("payload", event.payload());
            }

            // Send to Camel route
            String messageJson = objectMapper.writeValueAsString(message);
            producerTemplate.sendBody("direct:order-saga", messageJson);

            // Mark as processed
            outboxPort.markProcessed(event.id());
            log.info("Successfully processed outbox event: eventId={}", event.id());

        } catch (Exception e) {
            log.error("Failed to process outbox event: eventId={}, error={}",
                    event.id(), e.getMessage(), e);
        } finally {
            MDC.remove("txId");
        }
    }
}
