package com.ecommerce.order.application.port.in;

import com.ecommerce.order.adapter.in.web.dto.TransactionStatusResponse;

import java.util.Optional;

/**
 * Input port for querying transaction status.
 * Provides read-only access to saga transaction state.
 */
public interface TransactionQueryUseCase {

    /**
     * Retrieves the current status of a saga transaction.
     *
     * @param txId the transaction identifier
     * @return optional containing the transaction status if found
     */
    Optional<TransactionStatusResponse> getTransactionStatus(String txId);
}
