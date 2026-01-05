package com.ecommerce.inventory.adapter.in.web;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.inventory.application.port.in.ReserveInventoryUseCase;
import com.ecommerce.inventory.domain.model.Reservation;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for inventory operations.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final ReserveInventoryUseCase reserveInventoryUseCase;

    public InventoryController(ReserveInventoryUseCase reserveInventoryUseCase) {
        this.reserveInventoryUseCase = reserveInventoryUseCase;
    }

    /**
     * Reserve inventory notification from the saga orchestrator.
     *
     * @param request the notify request containing order items
     * @return the notify response with reservation result
     */
    @PostMapping("/notify")
    public ResponseEntity<NotifyResponse> notify(@Valid @RequestBody NotifyRequest request) {
        log.info("Received inventory notification for txId={}", request.txId());

        try {
            Reservation reservation = reserveInventoryUseCase.reserveInventory(request);

            if (reservation.isReserved()) {
                return ResponseEntity.ok(NotifyResponse.success(
                        request.txId(),
                        "Inventory reserved",
                        reservation.getReferenceNumber()
                ));
            } else {
                return ResponseEntity.ok(NotifyResponse.failure(
                        request.txId(),
                        "Inventory reservation failed: " + reservation.getStatus()
                ));
            }
        } catch (Exception e) {
            log.error("Inventory reservation failed for txId={}: {}", request.txId(), e.getMessage(), e);
            return ResponseEntity.ok(NotifyResponse.failure(
                    request.txId(),
                    "Inventory reservation error: " + e.getMessage()
            ));
        }
    }
}
