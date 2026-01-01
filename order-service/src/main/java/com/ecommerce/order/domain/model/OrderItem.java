package com.ecommerce.order.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Order line item value object.
 * Immutable - represents a single product in an order.
 */
public record OrderItem(
        String productId,
        String productName,
        int quantity,
        BigDecimal price
) {
    public OrderItem {
        Objects.requireNonNull(productId, "Product ID is required");
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        Objects.requireNonNull(price, "Price is required");
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    /**
     * Calculate line total (quantity * price).
     */
    public BigDecimal getLineTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
