package com.ecommerce.creditcard.adapter.in.web;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.creditcard.application.port.in.ProcessPaymentUseCase;
import com.ecommerce.creditcard.application.port.in.RollbackPaymentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Credit Card", description = "Credit card payment operations")
public class CreditCardController {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final RollbackPaymentUseCase rollbackPaymentUseCase;

    public CreditCardController(ProcessPaymentUseCase processPaymentUseCase,
                                 RollbackPaymentUseCase rollbackPaymentUseCase) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.rollbackPaymentUseCase = rollbackPaymentUseCase;
    }

    @PostMapping("/notify")
    @Operation(summary = "Process payment", description = "Process credit card payment (idempotent)")
    public ResponseEntity<NotifyResponse> notify(@RequestBody NotifyRequest request) {
        NotifyResponse response = processPaymentUseCase.processPayment(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rollback")
    @Operation(summary = "Rollback payment", description = "Rollback/refund credit card payment (idempotent)")
    public ResponseEntity<RollbackResponse> rollback(@RequestBody RollbackRequest request) {
        RollbackResponse response = rollbackPaymentUseCase.rollbackPayment(request);
        return ResponseEntity.ok(response);
    }
}
