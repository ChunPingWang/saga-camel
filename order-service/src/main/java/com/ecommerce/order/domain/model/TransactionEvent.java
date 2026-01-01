package com.ecommerce.order.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Domain event representing a transaction state change.
 * Used for event sourcing and outbox pattern.
 */
public record TransactionEvent(
        String eventType,
        String txId,
        String orderId,
        String userId,
        List<Map<String, Object>> items,
        BigDecimal totalAmount,
        String creditCardNumber,
        Instant timestamp
) {

    public static TransactionEvent orderConfirmed(
            String txId,
            String orderId,
            String userId,
            List<Map<String, Object>> items,
            BigDecimal totalAmount,
            String creditCardNumber
    ) {
        return new TransactionEvent(
                "ORDER_CONFIRMED",
                txId,
                orderId,
                userId,
                items,
                totalAmount,
                creditCardNumber,
                Instant.now()
        );
    }

    public static TransactionEvent serviceFailed(
            String txId,
            String orderId,
            String serviceName,
            String errorMessage
    ) {
        return new TransactionEvent(
                "SERVICE_FAILED",
                txId,
                orderId,
                null,
                List.of(Map.of("serviceName", serviceName, "error", errorMessage)),
                null,
                null,
                Instant.now()
        );
    }

    public static TransactionEvent sagaCompleted(String txId, String orderId) {
        return new TransactionEvent(
                "SAGA_COMPLETED",
                txId,
                orderId,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }

    public static TransactionEvent sagaRolledBack(String txId, String orderId) {
        return new TransactionEvent(
                "SAGA_ROLLED_BACK",
                txId,
                orderId,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }
}
