package com.ecommerce.order.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA entity for outbox_event table.
 * Supports Debezium Outbox Event Router pattern with aggregatetype and aggregateid.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Aggregate type for Debezium routing (e.g., "saga", "order").
     * Used by Debezium Outbox Event Router to determine target topic.
     */
    @Column(name = "aggregatetype", nullable = false, length = 100)
    private String aggregateType;

    /**
     * Aggregate ID for Debezium routing (typically orderId).
     * Used as Kafka message key for ordering guarantee.
     */
    @Column(name = "aggregateid", nullable = false, length = 36)
    private String aggregateId;

    /**
     * Event type (e.g., "SAGA_STARTED", "CREDIT_CARD_COMMAND").
     */
    @Column(name = "type", nullable = false, length = 100)
    private String eventType;

    /**
     * JSON payload containing event data.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "tx_id", nullable = false, length = 36)
    private String txId;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public OutboxEventEntity() {
    }

    /**
     * Constructor for backward compatibility.
     */
    public OutboxEventEntity(String txId, String orderId, String eventType, String payload) {
        this(txId, orderId, eventType, payload, "saga", orderId);
    }

    /**
     * Full constructor with CDC routing fields.
     */
    public OutboxEventEntity(String txId, String orderId, String eventType, String payload,
                             String aggregateType, String aggregateId) {
        this.txId = txId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.payload = payload;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.createdAt = LocalDateTime.now();
        this.processed = false;
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getProcessed() {
        return processed;
    }

    public void setProcessed(Boolean processed) {
        this.processed = processed;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }
}
