package com.ecommerce.logistics.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.logistics.application.port.in.RollbackShipmentUseCase;
import com.ecommerce.logistics.application.port.in.ScheduleShipmentUseCase;
import com.ecommerce.logistics.domain.model.Shipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Logistics service implementation.
 * Simulates shipment scheduling with configurable failure rate.
 */
@Service
public class LogisticsService implements ScheduleShipmentUseCase, RollbackShipmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(LogisticsService.class);

    // In-memory store for idempotency (in real app, would be persistent)
    private final Map<UUID, Shipment> shipments = new ConcurrentHashMap<>();

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
    public NotifyResponse scheduleShipment(NotifyRequest request) {
        MDC.put("txId", request.txId().toString());
        try {
            log.info("Scheduling shipment for txId={}, orderId={}",
                    request.txId(), request.orderId());

            // Idempotency check
            Shipment existingShipment = shipments.get(request.txId());
            if (existingShipment != null && existingShipment.isScheduled()) {
                log.info("Idempotent response for already scheduled shipment txId={}", request.txId());
                return createSuccessResponse(request.txId(), existingShipment.getTrackingNumber());
            }

            // Simulate delay
            simulateDelay();

            // Simulate failure
            if (shouldFail()) {
                log.warn("Simulated logistics failure for txId={}", request.txId());
                return NotifyResponse.failure(request.txId(), "Carrier unavailable (simulated)");
            }

            // Create shipment
            Shipment shipment = Shipment.create(request.txId(), request.orderId());
            String trackingNumber = generateTrackingNumber();
            LocalDateTime estimatedDelivery = LocalDateTime.now().plusDays(3);
            shipment.schedule(trackingNumber, estimatedDelivery);

            shipments.put(request.txId(), shipment);

            log.info("Shipment scheduled successfully for txId={}, tracking={}", request.txId(), trackingNumber);
            return createSuccessResponse(request.txId(), trackingNumber);

        } catch (Exception e) {
            log.error("Shipment scheduling error for txId={}", request.txId(), e);
            return NotifyResponse.failure(request.txId(), "Scheduling error: " + e.getMessage());
        } finally {
            MDC.remove("txId");
        }
    }

    @Override
    public RollbackResponse cancelShipment(RollbackRequest request) {
        MDC.put("txId", request.txId().toString());
        try {
            log.info("Cancelling shipment for txId={}", request.txId());

            // Check if shipment exists
            Shipment shipment = shipments.get(request.txId());
            if (shipment == null) {
                log.info("No shipment found for txId={}, rollback is no-op", request.txId());
                return RollbackResponse.success(request.txId(), "No shipment to cancel");
            }

            // Idempotency: already cancelled
            if (shipment.getStatus() == Shipment.ShipmentStatus.CANCELLED) {
                log.info("Shipment already cancelled for txId={}", request.txId());
                return RollbackResponse.success(request.txId(), "Shipment already cancelled");
            }

            // Cancel shipment
            if (shipment.canCancel()) {
                shipment.cancel();
                log.info("Shipment cancelled successfully for txId={}", request.txId());
                return RollbackResponse.success(request.txId(), "Shipment cancelled successfully");
            } else {
                log.warn("Cannot cancel shipment in status {} for txId={}", shipment.getStatus(), request.txId());
                return RollbackResponse.failure(request.txId(), "Cannot cancel shipment in current status");
            }

        } catch (Exception e) {
            log.error("Shipment cancellation error for txId={}", request.txId(), e);
            return RollbackResponse.failure(request.txId(), "Cancellation error: " + e.getMessage());
        } finally {
            MDC.remove("txId");
        }
    }

    private boolean shouldFail() {
        if (!failureEnabled) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < failureRate;
    }

    private void simulateDelay() {
        if (delayEnabled && delayMaxMs > 0) {
            int delay = delayMinMs + ThreadLocalRandom.current().nextInt(delayMaxMs - delayMinMs + 1);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String generateTrackingNumber() {
        return "TRK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private NotifyResponse createSuccessResponse(UUID txId, String trackingNumber) {
        return NotifyResponse.success(txId, "Shipment scheduled", trackingNumber);
    }
}
