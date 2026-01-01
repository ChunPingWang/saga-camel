package com.ecommerce.logistics.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Shipment domain model for logistics.
 */
public class Shipment {

    public enum ShipmentStatus {
        PENDING, SCHEDULED, CANCELLED, SHIPPED, DELIVERED
    }

    private final UUID txId;
    private final UUID orderId;
    private ShipmentStatus status;
    private String trackingNumber;
    private LocalDateTime estimatedDelivery;
    private final LocalDateTime createdAt;
    private LocalDateTime processedAt;

    private Shipment(UUID txId, UUID orderId) {
        Objects.requireNonNull(txId, "txId is required");
        Objects.requireNonNull(orderId, "orderId is required");

        this.txId = txId;
        this.orderId = orderId;
        this.status = ShipmentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public static Shipment create(UUID txId, UUID orderId) {
        return new Shipment(txId, orderId);
    }

    public void schedule(String trackingNumber, LocalDateTime estimatedDelivery) {
        if (this.status != ShipmentStatus.PENDING) {
            throw new IllegalStateException("Cannot schedule shipment in status: " + status);
        }
        this.trackingNumber = trackingNumber;
        this.estimatedDelivery = estimatedDelivery;
        this.status = ShipmentStatus.SCHEDULED;
        this.processedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == ShipmentStatus.SHIPPED || this.status == ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel shipment in status: " + status);
        }
        this.status = ShipmentStatus.CANCELLED;
        this.processedAt = LocalDateTime.now();
    }

    public void ship() {
        if (this.status != ShipmentStatus.SCHEDULED) {
            throw new IllegalStateException("Cannot ship in status: " + status);
        }
        this.status = ShipmentStatus.SHIPPED;
        this.processedAt = LocalDateTime.now();
    }

    public boolean isScheduled() {
        return status == ShipmentStatus.SCHEDULED;
    }

    public boolean canCancel() {
        return status == ShipmentStatus.PENDING || status == ShipmentStatus.SCHEDULED;
    }

    // Getters
    public UUID getTxId() { return txId; }
    public UUID getOrderId() { return orderId; }
    public ShipmentStatus getStatus() { return status; }
    public String getTrackingNumber() { return trackingNumber; }
    public LocalDateTime getEstimatedDelivery() { return estimatedDelivery; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
