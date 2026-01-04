package com.ecommerce.order.adapter.in.web.dto;

import java.util.List;

/**
 * Response DTO for order transaction history.
 * Contains all saga executions associated with an order.
 */
public record OrderTransactionHistoryResponse(
        String orderId,
        int totalTransactions,
        List<TransactionSummary> transactions
) {
    /**
     * Summary of a single saga transaction execution.
     */
    public record TransactionSummary(
            String txId,
            String overallStatus,
            String startedAt,
            List<TransactionStatusResponse.ServiceStatusDto> services
    ) {}
}
