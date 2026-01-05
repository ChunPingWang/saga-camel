package com.ecommerce.logistics.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shipment domain model representing a logistics shipment.
 */
public class Shipment {

    private final UUID txId;
    private final UUID orderId;
    private final String trackingNumber;
    private final ShipmentStatus status;
    private final LocalDateTime estimatedDelivery;
    private final LocalDateTime scheduledAt;

    public Shipment(UUID txId, UUID orderId, String trackingNumber,
                    ShipmentStatus status, LocalDateTime estimatedDelivery, LocalDateTime scheduledAt) {
        this.txId = txId;
        this.orderId = orderId;
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.estimatedDelivery = estimatedDelivery;
        this.scheduledAt = scheduledAt;
    }

    public static Shipment schedule(UUID txId, UUID orderId) {
        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime estimatedDelivery = LocalDateTime.now().plusDays(3);
        return new Shipment(txId, orderId, trackingNumber,
                ShipmentStatus.SCHEDULED, estimatedDelivery, LocalDateTime.now());
    }

    public static Shipment cancelled(UUID txId, UUID orderId) {
        return new Shipment(txId, orderId, null,
                ShipmentStatus.CANCELLED, null, LocalDateTime.now());
    }

    public UUID getTxId() { return txId; }
    public UUID getOrderId() { return orderId; }
    public String getTrackingNumber() { return trackingNumber; }
    public ShipmentStatus getStatus() { return status; }
    public LocalDateTime getEstimatedDelivery() { return estimatedDelivery; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public boolean isScheduled() { return status == ShipmentStatus.SCHEDULED; }

    public enum ShipmentStatus {
        PENDING, SCHEDULED, IN_TRANSIT, DELIVERED, CANCELLED
    }
}
