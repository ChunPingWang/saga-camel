package com.ecommerce.order.application.port.out;

import com.ecommerce.order.domain.model.Order;

import java.util.List;
import java.util.UUID;

/**
 * Output port for outbox pattern operations.
 * Ensures atomic write of business data and events.
 */
public interface OutboxPort {

    /**
     * Save an outbox event.
     */
    void save(OutboxEventData event);

    /**
     * Create a new saga event in the outbox.
     * This should be called within the same transaction as business data writes.
     */
    Long createSagaEvent(UUID txId, UUID orderId, Order order);

    /**
     * Find all unprocessed outbox events.
     */
    List<OutboxEvent> findUnprocessed();

    /**
     * Mark an event as processed.
     */
    void markProcessed(Long eventId);

    /**
     * Outbox event data for saving.
     */
    record OutboxEventData(
            String txId,
            String orderId,
            String eventType,
            String payload
    ) {
        public String getTxId() { return txId; }
        public String getOrderId() { return orderId; }
        public String getEventType() { return eventType; }
        public String getPayload() { return payload; }
    }

    /**
     * Outbox event record.
     */
    record OutboxEvent(
            Long id,
            UUID txId,
            UUID orderId,
            String eventType,
            String payload
    ) {}
}
