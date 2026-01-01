package com.ecommerce.order.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;

public record TransactionStatusResponse(
        String txId,
        String orderId,
        String overallStatus,
        List<ServiceStatusDto> services
) {
    public record ServiceStatusDto(
            String serviceName,
            String status,
            Instant completedAt
    ) {}
}
