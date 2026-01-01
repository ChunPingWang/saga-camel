package com.ecommerce.creditcard.application.port.in;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;

/**
 * Input port for processing credit card payments.
 */
public interface ProcessPaymentUseCase {

    /**
     * Process a credit card payment.
     * Idempotent - same txId will return same result.
     */
    NotifyResponse processPayment(NotifyRequest request);
}
