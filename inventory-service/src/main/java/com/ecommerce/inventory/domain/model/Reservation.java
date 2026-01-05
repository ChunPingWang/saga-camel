package com.ecommerce.inventory.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reservation domain model representing an inventory reservation.
 */
public class Reservation {

    private final UUID txId;
    private final UUID orderId;
    private final List<ReservedItem> items;
    private final ReservationStatus status;
    private final String referenceNumber;
    private final LocalDateTime reservedAt;

    public Reservation(UUID txId, UUID orderId, List<ReservedItem> items,
                       ReservationStatus status, String referenceNumber, LocalDateTime reservedAt) {
        this.txId = txId;
        this.orderId = orderId;
        this.items = items;
        this.status = status;
        this.referenceNumber = referenceNumber;
        this.reservedAt = reservedAt;
    }

    public static Reservation reserve(UUID txId, UUID orderId, List<Map<String, Object>> itemMaps) {
        List<ReservedItem> reservedItems = itemMaps.stream()
                .map(ReservedItem::fromMap)
                .toList();
        String referenceNumber = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new Reservation(txId, orderId, reservedItems,
                ReservationStatus.RESERVED, referenceNumber, LocalDateTime.now());
    }

    public UUID getTxId() { return txId; }
    public UUID getOrderId() { return orderId; }
    public List<ReservedItem> getItems() { return items; }
    public ReservationStatus getStatus() { return status; }
    public String getReferenceNumber() { return referenceNumber; }
    public LocalDateTime getReservedAt() { return reservedAt; }
    public boolean isReserved() { return status == ReservationStatus.RESERVED; }

    public enum ReservationStatus {
        PENDING, RESERVED, OUT_OF_STOCK, RELEASED
    }

    public record ReservedItem(String productId, String productName, int quantity) {
        public static ReservedItem fromMap(Map<String, Object> map) {
            String productId = map.getOrDefault("productId", "").toString();
            String productName = map.getOrDefault("productName", "").toString();
            int quantity = map.get("quantity") instanceof Number n ? n.intValue() : 1;
            return new ReservedItem(productId, productName, quantity);
        }
    }
}
