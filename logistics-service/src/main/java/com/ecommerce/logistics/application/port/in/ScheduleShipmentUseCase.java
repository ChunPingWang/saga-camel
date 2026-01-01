package com.ecommerce.logistics.application.port.in;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;

/**
 * Input port for scheduling shipments.
 */
public interface ScheduleShipmentUseCase {

    /**
     * Schedule a shipment for an order.
     * Idempotent - same txId will return same result.
     */
    NotifyResponse scheduleShipment(NotifyRequest request);
}
