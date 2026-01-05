package com.ecommerce.creditcard.adapter.in.web;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.creditcard.application.port.in.ProcessPaymentUseCase;
import com.ecommerce.creditcard.application.port.in.RollbackPaymentUseCase;
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
    private final RollbackPaymentUseCase rollbackPaymentUseCase;

    public CreditCardController(ProcessPaymentUseCase processPaymentUseCase,
                                RollbackPaymentUseCase rollbackPaymentUseCase) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.rollbackPaymentUseCase = rollbackPaymentUseCase;
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

    /**
     * Rollback (refund) a payment from the saga orchestrator.
     *
     * @param request the rollback request containing transaction details
     * @return the rollback response with result
     */
    @PostMapping("/rollback")
    public ResponseEntity<RollbackResponse> rollback(@Valid @RequestBody RollbackRequest request) {
        log.info("Received payment rollback for txId={}", request.txId());

        try {
            RollbackResponse response = rollbackPaymentUseCase.rollbackPayment(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Payment rollback failed for txId={}: {}", request.txId(), e.getMessage(), e);
            return ResponseEntity.ok(RollbackResponse.failure(
                    request.txId(),
                    "Payment rollback error: " + e.getMessage()
            ));
        }
    }
}
