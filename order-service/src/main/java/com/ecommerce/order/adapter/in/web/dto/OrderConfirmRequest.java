package com.ecommerce.order.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record OrderConfirmRequest(
        @NotBlank(message = "orderId is required")
        String orderId,

        @NotBlank(message = "userId is required")
        String userId,

        @NotEmpty(message = "items cannot be empty")
        @Valid
        List<OrderItemDto> items,

        @PositiveOrZero(message = "totalAmount must be zero or positive")
        BigDecimal totalAmount,

        @NotBlank(message = "creditCardNumber is required")
        String creditCardNumber
) {
    public record OrderItemDto(
            @NotBlank(message = "sku is required")
            String sku,

            @Positive(message = "quantity must be positive")
            int quantity,

            @PositiveOrZero(message = "unitPrice must be zero or positive")
            BigDecimal unitPrice
    ) {}
}
