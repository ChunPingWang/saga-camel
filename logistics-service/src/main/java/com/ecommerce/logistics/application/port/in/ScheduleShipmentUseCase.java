package com.ecommerce.logistics.application.port.in;

import com.ecommerce.logistics.domain.model.Shipment;
import com.ecommerce.common.dto.NotifyRequest;

/**
 * Use case port for scheduling shipments.
 */
public interface ScheduleShipmentUseCase {

    /**
     * Schedule a shipment for the given notification request.
     *
     * @param request the notification request containing order details
     * @return the scheduled shipment
     */
    Shipment scheduleShipment(NotifyRequest request);
}
