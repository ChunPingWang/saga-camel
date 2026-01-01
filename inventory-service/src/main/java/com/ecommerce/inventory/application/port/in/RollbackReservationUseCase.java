package com.ecommerce.inventory.application.port.in;

import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;

/**
 * Input port for rolling back inventory reservations.
 */
public interface RollbackReservationUseCase {

    /**
     * Release reserved inventory.
     * Idempotent - same txId will return same result.
     */
    RollbackResponse releaseInventory(RollbackRequest request);
}
