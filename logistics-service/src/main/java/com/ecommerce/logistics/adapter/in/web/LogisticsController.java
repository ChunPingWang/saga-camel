package com.ecommerce.logistics.adapter.in.web;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.logistics.application.port.in.RollbackShipmentUseCase;
import com.ecommerce.logistics.application.port.in.ScheduleShipmentUseCase;
import com.ecommerce.logistics.domain.model.Shipment;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class LogisticsController {

    private static final Logger log = LoggerFactory.getLogger(LogisticsController.class);

    private final ScheduleShipmentUseCase scheduleShipmentUseCase;
    private final RollbackShipmentUseCase rollbackShipmentUseCase;

    public LogisticsController(ScheduleShipmentUseCase scheduleShipmentUseCase,
                               RollbackShipmentUseCase rollbackShipmentUseCase) {
        this.scheduleShipmentUseCase = scheduleShipmentUseCase;
        this.rollbackShipmentUseCase = rollbackShipmentUseCase;
    }

    /**
     * Schedule shipment notification from the saga orchestrator.
     *
     * @param request the notify request containing order details
     * @return the notify response with shipment result
     */
    @PostMapping("/notify")
    public ResponseEntity<NotifyResponse> notify(@Valid @RequestBody NotifyRequest request) {
        log.info("Received logistics notification for txId={}", request.txId());

        try {
            Shipment shipment = scheduleShipmentUseCase.scheduleShipment(request);

            if (shipment.isScheduled()) {
                return ResponseEntity.ok(NotifyResponse.success(
                        request.txId(),
                        "Shipment scheduled, tracking: " + shipment.getTrackingNumber(),
                        shipment.getTrackingNumber()
                ));
            } else {
                return ResponseEntity.ok(NotifyResponse.failure(
                        request.txId(),
                        "Carrier unavailable"
                ));
            }
        } catch (Exception e) {
            log.error("Shipment scheduling failed for txId={}: {}", request.txId(), e.getMessage(), e);
            return ResponseEntity.ok(NotifyResponse.failure(
                    request.txId(),
                    "Shipment scheduling error: " + e.getMessage()
            ));
        }
    }

    /**
     * Cancel shipment rollback from the saga orchestrator.
     *
     * @param request the rollback request containing transaction details
     * @return the rollback response with result
     */
    @PostMapping("/rollback")
    public ResponseEntity<RollbackResponse> rollback(@Valid @RequestBody RollbackRequest request) {
        log.info("Received logistics rollback for txId={}", request.txId());

        try {
            RollbackResponse response = rollbackShipmentUseCase.cancelShipment(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Shipment cancellation failed for txId={}: {}", request.txId(), e.getMessage(), e);
            return ResponseEntity.ok(RollbackResponse.failure(
                    request.txId(),
                    "Shipment cancellation error: " + e.getMessage()
            ));
        }
    }
}
