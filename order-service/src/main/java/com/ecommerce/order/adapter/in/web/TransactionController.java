package com.ecommerce.order.adapter.in.web;

import com.ecommerce.order.adapter.in.web.dto.OrderTransactionHistoryResponse;
import com.ecommerce.order.adapter.in.web.dto.TransactionStatusResponse;
import com.ecommerce.order.application.port.in.TransactionQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transaction", description = "Transaction status and history APIs")
public class TransactionController {

    private final TransactionQueryUseCase transactionQueryUseCase;

    public TransactionController(TransactionQueryUseCase transactionQueryUseCase) {
        this.transactionQueryUseCase = transactionQueryUseCase;
    }

    @GetMapping("/{txId}")
    @Operation(summary = "Get transaction status by Tx ID",
               description = "Retrieves the current status of a saga transaction")
    public ResponseEntity<TransactionStatusResponse> getTransactionStatus(
            @Parameter(description = "Transaction ID (UUID)") @PathVariable String txId) {
        return transactionQueryUseCase.getTransactionStatus(txId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/orders/{orderId}/history")
    @Operation(summary = "Get order transaction history",
               description = "Retrieves all saga transaction executions for a given order")
    public ResponseEntity<OrderTransactionHistoryResponse> getOrderTransactionHistory(
            @Parameter(description = "Order ID (UUID)") @PathVariable String orderId) {
        return transactionQueryUseCase.getOrderTransactionHistory(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
