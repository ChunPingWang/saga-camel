package com.ecommerce.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Order domain entity.
 * Tests are written FIRST following TDD approach.
 */
class OrderTest {

    @Nested
    @DisplayName("Order Creation")
    class OrderCreation {

        @Test
        @DisplayName("should create order with valid data")
        void shouldCreateOrderWithValidData() {
            // Given
            UUID orderId = UUID.randomUUID();
            String customerId = "CUST-001";
            List<OrderItem> items = List.of(
                    new OrderItem("PROD-001", "Widget", 2, new BigDecimal("29.99"))
            );
            BigDecimal totalAmount = new BigDecimal("59.98");

            // When
            Order order = new Order(orderId, customerId, items, totalAmount);

            // Then
            assertEquals(orderId, order.getOrderId());
            assertEquals(customerId, order.getCustomerId());
            assertEquals(1, order.getItems().size());
            assertEquals(totalAmount, order.getTotalAmount());
            assertNotNull(order.getCreatedAt());
        }

        @Test
        @DisplayName("should throw exception when orderId is null")
        void shouldThrowExceptionWhenOrderIdIsNull() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Order(null, "CUST-001", List.of(), new BigDecimal("10.00"))
            );
        }

        @Test
        @DisplayName("should throw exception when customerId is blank")
        void shouldThrowExceptionWhenCustomerIdIsBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Order(UUID.randomUUID(), "", List.of(), new BigDecimal("10.00"))
            );
        }

        @Test
        @DisplayName("should throw exception when items list is empty")
        void shouldThrowExceptionWhenItemsListIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Order(UUID.randomUUID(), "CUST-001", List.of(), new BigDecimal("10.00"))
            );
        }

        @Test
        @DisplayName("should throw exception when totalAmount is zero or negative")
        void shouldThrowExceptionWhenTotalAmountIsZeroOrNegative() {
            List<OrderItem> items = List.of(
                    new OrderItem("PROD-001", "Widget", 1, new BigDecimal("10.00"))
            );

            assertThrows(IllegalArgumentException.class, () ->
                    new Order(UUID.randomUUID(), "CUST-001", items, BigDecimal.ZERO)
            );

            assertThrows(IllegalArgumentException.class, () ->
                    new Order(UUID.randomUUID(), "CUST-001", items, new BigDecimal("-1.00"))
            );
        }
    }

    @Nested
    @DisplayName("Order Immutability")
    class OrderImmutability {

        @Test
        @DisplayName("should return unmodifiable items list")
        void shouldReturnUnmodifiableItemsList() {
            // Given
            List<OrderItem> items = List.of(
                    new OrderItem("PROD-001", "Widget", 2, new BigDecimal("29.99"))
            );
            Order order = new Order(UUID.randomUUID(), "CUST-001", items, new BigDecimal("59.98"));

            // When & Then
            assertThrows(UnsupportedOperationException.class, () ->
                    order.getItems().add(new OrderItem("PROD-002", "Gadget", 1, new BigDecimal("10.00")))
            );
        }
    }

    @Nested
    @DisplayName("OrderItem Creation")
    class OrderItemCreation {

        @Test
        @DisplayName("should create order item with valid data")
        void shouldCreateOrderItemWithValidData() {
            // When
            OrderItem item = new OrderItem("PROD-001", "Widget", 2, new BigDecimal("29.99"));

            // Then
            assertEquals("PROD-001", item.productId());
            assertEquals("Widget", item.productName());
            assertEquals(2, item.quantity());
            assertEquals(new BigDecimal("29.99"), item.price());
        }

        @Test
        @DisplayName("should calculate line total correctly")
        void shouldCalculateLineTotalCorrectly() {
            // Given
            OrderItem item = new OrderItem("PROD-001", "Widget", 3, new BigDecimal("10.00"));

            // When
            BigDecimal lineTotal = item.getLineTotal();

            // Then
            assertEquals(new BigDecimal("30.00"), lineTotal);
        }
    }
}
