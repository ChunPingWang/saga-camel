package com.ecommerce.inventory.application.port.in;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;

/**
 * Input port for reserving inventory.
 */
public interface ReserveInventoryUseCase {

    /**
     * Reserve inventory for an order.
     * Idempotent - same txId will return same result.
     */
    NotifyResponse reserveInventory(NotifyRequest request);
}
