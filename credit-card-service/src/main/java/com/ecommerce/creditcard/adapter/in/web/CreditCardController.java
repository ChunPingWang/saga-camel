package com.ecommerce.creditcard.adapter.in.web;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.creditcard.application.port.in.ProcessPaymentUseCase;
import com.ecommerce.creditcard.domain.model.Payment;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for credit card payment operations.
 */
@RestController
@RequestMapping("/api/v1/credit-card")
public class CreditCardController {

    private static final Logger log = LoggerFactory.getLogger(CreditCardController.class);

    private final ProcessPaymentUseCase processPaymentUseCase;

    public CreditCardController(ProcessPaymentUseCase processPaymentUseCase) {
        this.processPaymentUseCase = processPaymentUseCase;
    }

    /**
     * Process a payment notification from the saga orchestrator.
     *
     * @param request the notify request containing payment details
     * @return the notify response with payment result
     */
    @PostMapping("/notify")
    public ResponseEntity<NotifyResponse> notify(@Valid @RequestBody NotifyRequest request) {
        log.info("Received payment notification for txId={}", request.txId());

        try {
            Payment payment = processPaymentUseCase.processPayment(request);

            if (payment.isApproved()) {
                return ResponseEntity.ok(NotifyResponse.success(
                        request.txId(),
                        "Payment approved",
                        payment.getReferenceNumber()
                ));
            } else {
                return ResponseEntity.ok(NotifyResponse.failure(
                        request.txId(),
                        "Payment declined"
                ));
            }
        } catch (Exception e) {
            log.error("Payment processing failed for txId={}: {}", request.txId(), e.getMessage(), e);
            return ResponseEntity.ok(NotifyResponse.failure(
                    request.txId(),
                    "Payment processing error: " + e.getMessage()
            ));
        }
    }
}
