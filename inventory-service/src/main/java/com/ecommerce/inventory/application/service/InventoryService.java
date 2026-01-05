package com.ecommerce.inventory.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.inventory.application.port.in.ReserveInventoryUseCase;
import com.ecommerce.inventory.domain.model.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service implementation for inventory reservation.
 */
@Service
public class InventoryService implements ReserveInventoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    @Override
    public Reservation reserveInventory(NotifyRequest request) {
        log.info("Reserving inventory for txId={}, orderId={}, items={}",
                request.txId(), request.orderId(), request.items().size());

        Reservation reservation = Reservation.reserve(
                request.txId(),
                request.orderId(),
                request.items()
        );

        log.info("Inventory reserved successfully: referenceNumber={}, status={}",
                reservation.getReferenceNumber(), reservation.getStatus());

        return reservation;
    }
}
