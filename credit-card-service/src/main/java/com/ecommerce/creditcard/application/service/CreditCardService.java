package com.ecommerce.creditcard.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.creditcard.application.port.in.ProcessPaymentUseCase;
import com.ecommerce.creditcard.domain.model.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service implementation for credit card payment processing.
 */
@Service
public class CreditCardService implements ProcessPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreditCardService.class);

    @Override
    public Payment processPayment(NotifyRequest request) {
        log.info("Processing payment for txId={}, orderId={}, amount={}",
                request.txId(), request.orderId(), request.totalAmount());

        Payment payment = Payment.process(
                request.txId(),
                request.orderId(),
                request.totalAmount(),
                request.creditCardNumber()
        );

        log.info("Payment processed successfully: referenceNumber={}, status={}",
                payment.getReferenceNumber(), payment.getStatus());

        return payment;
    }
}
