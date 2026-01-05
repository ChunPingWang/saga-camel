package com.ecommerce.logistics.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.logistics.application.port.in.ScheduleShipmentUseCase;
import com.ecommerce.logistics.domain.model.Shipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service implementation for shipment scheduling.
 */
@Service
public class LogisticsService implements ScheduleShipmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(LogisticsService.class);

    @Override
    public Shipment scheduleShipment(NotifyRequest request) {
        log.info("Scheduling shipment for txId={}, orderId={}",
                request.txId(), request.orderId());

        Shipment shipment = Shipment.schedule(
                request.txId(),
                request.orderId()
        );

        log.info("Shipment scheduled successfully: trackingNumber={}, status={}",
                shipment.getTrackingNumber(), shipment.getStatus());

        return shipment;
    }
}
