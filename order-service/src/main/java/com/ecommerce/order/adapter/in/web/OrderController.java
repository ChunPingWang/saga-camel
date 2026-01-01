package com.ecommerce.order.adapter.in.web;

import com.ecommerce.order.adapter.in.web.dto.OrderConfirmRequest;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmResponse;
import com.ecommerce.order.application.port.in.OrderConfirmUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderConfirmUseCase orderConfirmUseCase;

    public OrderController(OrderConfirmUseCase orderConfirmUseCase) {
        this.orderConfirmUseCase = orderConfirmUseCase;
    }

    @PostMapping("/confirm")
    public ResponseEntity<OrderConfirmResponse> confirmOrder(
            @Valid @RequestBody OrderConfirmRequest request) {
        OrderConfirmResponse response = orderConfirmUseCase.confirmOrder(request);
        return ResponseEntity.accepted().body(response);
    }
}
