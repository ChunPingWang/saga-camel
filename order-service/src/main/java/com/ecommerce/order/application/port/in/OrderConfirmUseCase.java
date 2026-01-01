package com.ecommerce.order.application.port.in;

import com.ecommerce.order.adapter.in.web.dto.OrderConfirmRequest;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmResponse;

/**
 * Input port for order confirmation use case.
 * Initiates the saga orchestration for processing an order.
 */
public interface OrderConfirmUseCase {

    /**
     * Confirms an order and initiates the saga orchestration.
     *
     * @param request the order confirmation request containing order details
     * @return response containing the transaction ID and initial status
     */
    OrderConfirmResponse confirmOrder(OrderConfirmRequest request);
}
