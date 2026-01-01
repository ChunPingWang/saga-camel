package com.ecommerce.logistics.application.port.in;

import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;

/**
 * Input port for rolling back shipment schedules.
 */
public interface RollbackShipmentUseCase {

    /**
     * Cancel a scheduled shipment.
     * Idempotent - same txId will return same result.
     */
    RollbackResponse cancelShipment(RollbackRequest request);
}
