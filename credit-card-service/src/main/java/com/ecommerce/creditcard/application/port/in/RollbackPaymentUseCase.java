package com.ecommerce.creditcard.application.port.in;

import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;

/**
 * Input port for rolling back credit card payments.
 */
public interface RollbackPaymentUseCase {

    /**
     * Rollback a credit card payment (refund).
     * Idempotent - same txId will return same result.
     */
    RollbackResponse rollbackPayment(RollbackRequest request);
}
