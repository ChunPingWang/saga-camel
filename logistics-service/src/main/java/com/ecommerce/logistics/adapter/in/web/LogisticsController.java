package com.ecommerce.logistics.adapter.in.web;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.logistics.application.port.in.RollbackShipmentUseCase;
import com.ecommerce.logistics.application.port.in.ScheduleShipmentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for logistics operations.
 */
@RestController
@RequestMapping("/api/v1/logistics")
@Tag(name = "Logistics", description = "Shipment scheduling operations")
public class LogisticsController {

    private final ScheduleShipmentUseCase scheduleShipmentUseCase;
    private final RollbackShipmentUseCase rollbackShipmentUseCase;

    public LogisticsController(ScheduleShipmentUseCase scheduleShipmentUseCase,
                               RollbackShipmentUseCase rollbackShipmentUseCase) {
        this.scheduleShipmentUseCase = scheduleShipmentUseCase;
        this.rollbackShipmentUseCase = rollbackShipmentUseCase;
    }

    @PostMapping("/notify")
    @Operation(summary = "Schedule shipment", description = "Schedule a shipment for an order (idempotent)")
    public ResponseEntity<NotifyResponse> notify(@RequestBody NotifyRequest request) {
        NotifyResponse response = scheduleShipmentUseCase.scheduleShipment(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rollback")
    @Operation(summary = "Cancel shipment", description = "Cancel a scheduled shipment (idempotent)")
    public ResponseEntity<RollbackResponse> rollback(@RequestBody RollbackRequest request) {
        RollbackResponse response = rollbackShipmentUseCase.cancelShipment(request);
        return ResponseEntity.ok(response);
    }
}
