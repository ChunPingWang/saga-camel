package com.ecommerce.order.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Order domain entity.
 * Represents a customer order being processed through the saga.
 * <p>
 * This is a pure domain entity with NO framework dependencies.
 */
public class Order {

    private final UUID orderId;
    private final String customerId;
    private final List<OrderItem> items;
    private final BigDecimal totalAmount;
    private final LocalDateTime createdAt;

    public Order(UUID orderId, String customerId, List<OrderItem> items, BigDecimal totalAmount) {
        this(orderId, customerId, items, totalAmount, LocalDateTime.now());
    }

    public Order(UUID orderId, String customerId, List<OrderItem> items, BigDecimal totalAmount, LocalDateTime createdAt) {
        validateOrderId(orderId);
        validateCustomerId(customerId);
        validateItems(items);
        validateTotalAmount(totalAmount);

        this.orderId = orderId;
        this.customerId = customerId;
        this.items = List.copyOf(items); // Defensive copy - immutable
        this.totalAmount = totalAmount;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    private void validateOrderId(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID is required");
        }
    }

    private void validateCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
    }

    private void validateItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
    }

    private void validateTotalAmount(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be positive");
        }
    }

    // Getters - no setters (immutable entity)

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return String.format("Order[orderId=%s, customerId=%s, items=%d, totalAmount=%s]",
                orderId, customerId, items.size(), totalAmount);
    }
}
