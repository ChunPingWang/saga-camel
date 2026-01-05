package com.ecommerce.creditcard.application.port.in;

import com.ecommerce.creditcard.domain.model.Payment;
import com.ecommerce.common.dto.NotifyRequest;

/**
 * Use case port for processing credit card payments.
 */
public interface ProcessPaymentUseCase {

    /**
     * Process a payment for the given notification request.
     *
     * @param request the notification request containing payment details
     * @return the processed payment
     */
    Payment processPayment(NotifyRequest request);
}
