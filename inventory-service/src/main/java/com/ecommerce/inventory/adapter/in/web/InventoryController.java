package com.ecommerce.inventory.adapter.in.web;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.inventory.application.port.in.ReserveInventoryUseCase;
import com.ecommerce.inventory.application.port.in.RollbackReservationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Inventory", description = "Inventory reservation operations")
public class InventoryController {

    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final RollbackReservationUseCase rollbackReservationUseCase;

    public InventoryController(ReserveInventoryUseCase reserveInventoryUseCase,
                               RollbackReservationUseCase rollbackReservationUseCase) {
        this.reserveInventoryUseCase = reserveInventoryUseCase;
        this.rollbackReservationUseCase = rollbackReservationUseCase;
    }

    @PostMapping("/notify")
    @Operation(summary = "Reserve inventory", description = "Reserve inventory for an order (idempotent)")
    public ResponseEntity<NotifyResponse> notify(@RequestBody NotifyRequest request) {
        NotifyResponse response = reserveInventoryUseCase.reserveInventory(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rollback")
    @Operation(summary = "Release inventory", description = "Release reserved inventory (idempotent)")
    public ResponseEntity<RollbackResponse> rollback(@RequestBody RollbackRequest request) {
        RollbackResponse response = rollbackReservationUseCase.releaseInventory(request);
        return ResponseEntity.ok(response);
    }
}
