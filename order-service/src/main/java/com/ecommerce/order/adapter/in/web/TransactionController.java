package com.ecommerce.order.adapter.in.web;

import com.ecommerce.order.adapter.in.web.dto.TransactionStatusResponse;
import com.ecommerce.order.application.port.in.TransactionQueryUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionQueryUseCase transactionQueryUseCase;

    public TransactionController(TransactionQueryUseCase transactionQueryUseCase) {
        this.transactionQueryUseCase = transactionQueryUseCase;
    }

    @GetMapping("/{txId}")
    public ResponseEntity<TransactionStatusResponse> getTransactionStatus(@PathVariable String txId) {
        return transactionQueryUseCase.getTransactionStatus(txId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
