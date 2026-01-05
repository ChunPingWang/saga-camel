package com.ecommerce.logistics.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.logistics.application.port.in.RollbackShipmentUseCase;
import com.ecommerce.logistics.application.port.in.ScheduleShipmentUseCase;
import com.ecommerce.logistics.domain.model.Shipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service implementation for shipment scheduling.
 */
@Service
public class LogisticsService implements ScheduleShipmentUseCase, RollbackShipmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(LogisticsService.class);
    private static final Random random = new Random();

    // In-memory store for idempotency
    private final Map<UUID, Shipment> scheduledShipments = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> cancelledShipments = new ConcurrentHashMap<>();

    // Failure simulation settings
    @Value("${simulation.failure.enabled:false}")
    private boolean failureEnabled;

    @Value("${simulation.failure.rate:0.0}")
    private double failureRate;

    @Value("${simulation.delay.enabled:false}")
    private boolean delayEnabled;

    @Value("${simulation.delay.min-ms:0}")
    private int delayMinMs;

    @Value("${simulation.delay.max-ms:0}")
    private int delayMaxMs;

    @Override
    public Shipment scheduleShipment(NotifyRequest request) {
        log.info("Scheduling shipment for txId={}, orderId={}",
                request.txId(), request.orderId());

        // Idempotency check
        Shipment existingShipment = scheduledShipments.get(request.txId());
        if (existingShipment != null) {
            log.info("Returning cached shipment for txId={}", request.txId());
            return existingShipment;
        }

        // Simulate delay if enabled
        simulateDelay();

        // Check for simulated failure
        if (shouldSimulateFailure()) {
            log.warn("Simulating shipment failure for txId={}", request.txId());
            Shipment failedShipment = Shipment.cancelled(
                    request.txId(),
                    request.orderId()
            );
            scheduledShipments.put(request.txId(), failedShipment);
            return failedShipment;
        }

        Shipment shipment = Shipment.schedule(
                request.txId(),
                request.orderId()
        );

        scheduledShipments.put(request.txId(), shipment);

        log.info("Shipment scheduled successfully: trackingNumber={}, status={}",
                shipment.getTrackingNumber(), shipment.getStatus());

        return shipment;
    }

    @Override
    public RollbackResponse cancelShipment(RollbackRequest request) {
        log.info("Cancelling shipment for txId={}", request.txId());

        // Check if already cancelled (idempotency)
        if (cancelledShipments.containsKey(request.txId())) {
            log.info("Shipment already cancelled for txId={}", request.txId());
            return RollbackResponse.success(request.txId(), "Shipment already cancelled");
        }

        // Check if shipment exists
        Shipment shipment = scheduledShipments.get(request.txId());
        if (shipment == null) {
            log.info("No shipment found for txId={}", request.txId());
            return RollbackResponse.success(request.txId(), "No shipment to cancel");
        }

        // Simulate delay if enabled
        simulateDelay();

        // Mark as cancelled
        cancelledShipments.put(request.txId(), true);
        log.info("Shipment cancelled successfully for txId={}", request.txId());

        return RollbackResponse.success(request.txId(), "Shipment cancelled successfully");
    }

    private boolean shouldSimulateFailure() {
        return failureEnabled && random.nextDouble() < failureRate;
    }

    private void simulateDelay() {
        if (delayEnabled && delayMaxMs > 0) {
            int delay = delayMinMs + random.nextInt(Math.max(1, delayMaxMs - delayMinMs));
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
