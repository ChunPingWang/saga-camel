package com.ecommerce.inventory.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Inventory reservation domain model.
 */
public class Reservation {

    public enum ReservationStatus {
        PENDING, RESERVED, RELEASED, FAILED
    }

    public record ReservedItem(String sku, int quantity) {}

    private final UUID txId;
    private final UUID orderId;
    private final List<ReservedItem> items;
    private ReservationStatus status;
    private String reservationReference;
    private final LocalDateTime createdAt;
    private LocalDateTime processedAt;

    private Reservation(UUID txId, UUID orderId, List<ReservedItem> items) {
        Objects.requireNonNull(txId, "txId is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(items, "items is required");

        if (items.isEmpty()) {
            throw new IllegalArgumentException("Items cannot be empty");
        }

        this.txId = txId;
        this.orderId = orderId;
        this.items = List.copyOf(items);
        this.status = ReservationStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public static Reservation create(UUID txId, UUID orderId, List<Map<String, Object>> itemsData) {
        List<ReservedItem> items = itemsData.stream()
                .map(data -> new ReservedItem(
                        (String) data.get("sku"),
                        ((Number) data.get("quantity")).intValue()
                ))
                .toList();
        return new Reservation(txId, orderId, items);
    }

    public void reserve(String reference) {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Cannot reserve in status: " + status);
        }
        this.reservationReference = reference;
        this.status = ReservationStatus.RESERVED;
        this.processedAt = LocalDateTime.now();
    }

    public void release() {
        if (this.status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Cannot release reservation in status: " + status);
        }
        this.status = ReservationStatus.RELEASED;
        this.processedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = ReservationStatus.FAILED;
        this.processedAt = LocalDateTime.now();
    }

    public boolean isReserved() {
        return status == ReservationStatus.RESERVED;
    }

    public boolean canRelease() {
        return status == ReservationStatus.RESERVED;
    }

    // Getters
    public UUID getTxId() { return txId; }
    public UUID getOrderId() { return orderId; }
    public List<ReservedItem> getItems() { return items; }
    public ReservationStatus getStatus() { return status; }
    public String getReservationReference() { return reservationReference; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
