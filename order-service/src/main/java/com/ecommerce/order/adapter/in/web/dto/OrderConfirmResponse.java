package com.ecommerce.order.adapter.in.web.dto;

public record OrderConfirmResponse(
        String txId,
        String status
) {}
