package com.ecommerce.inventory.application.port.in;

import com.ecommerce.inventory.domain.model.Reservation;
import com.ecommerce.common.dto.NotifyRequest;

/**
 * Use case port for reserving inventory.
 */
public interface ReserveInventoryUseCase {

    /**
     * Reserve inventory for the given notification request.
     *
     * @param request the notification request containing order items
     * @return the reservation result
     */
    Reservation reserveInventory(NotifyRequest request);
}
