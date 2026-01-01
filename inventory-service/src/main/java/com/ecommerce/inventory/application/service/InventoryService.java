package com.ecommerce.inventory.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.inventory.application.port.in.ReserveInventoryUseCase;
import com.ecommerce.inventory.application.port.in.RollbackReservationUseCase;
import com.ecommerce.inventory.domain.model.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Inventory service implementation.
 * Simulates inventory reservation with configurable failure rate.
 */
@Service
public class InventoryService implements ReserveInventoryUseCase, RollbackReservationUseCase {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    // In-memory store for idempotency (in real app, would be persistent)
    private final Map<UUID, Reservation> reservations = new ConcurrentHashMap<>();

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
    public NotifyResponse reserveInventory(NotifyRequest request) {
        MDC.put("txId", request.txId().toString());
        try {
            log.info("Reserving inventory for txId={}, orderId={}, items={}",
                    request.txId(), request.orderId(), request.items().size());

            // Idempotency check
            Reservation existingReservation = reservations.get(request.txId());
            if (existingReservation != null && existingReservation.isReserved()) {
                log.info("Idempotent response for already reserved inventory txId={}", request.txId());
                return createSuccessResponse(request.txId(), existingReservation.getReservationReference());
            }

            // Simulate delay
            simulateDelay();

            // Simulate failure
            if (shouldFail()) {
                log.warn("Simulated inventory failure for txId={}", request.txId());
                return NotifyResponse.failure(request.txId(), "Out of stock (simulated)");
            }

            // Create reservation
            Reservation reservation = Reservation.create(request.txId(), request.orderId(), request.items());
            String reservationRef = generateReservationRef();
            reservation.reserve(reservationRef);

            reservations.put(request.txId(), reservation);

            log.info("Inventory reserved successfully for txId={}, ref={}", request.txId(), reservationRef);
            return createSuccessResponse(request.txId(), reservationRef);

        } catch (Exception e) {
            log.error("Inventory reservation error for txId={}", request.txId(), e);
            return NotifyResponse.failure(request.txId(), "Reservation error: " + e.getMessage());
        } finally {
            MDC.remove("txId");
        }
    }

    @Override
    public RollbackResponse releaseInventory(RollbackRequest request) {
        MDC.put("txId", request.txId().toString());
        try {
            log.info("Releasing inventory for txId={}", request.txId());

            // Check if reservation exists
            Reservation reservation = reservations.get(request.txId());
            if (reservation == null) {
                log.info("No reservation found for txId={}, rollback is no-op", request.txId());
                return RollbackResponse.success(request.txId(), "No reservation to release");
            }

            // Idempotency: already released
            if (reservation.getStatus() == Reservation.ReservationStatus.RELEASED) {
                log.info("Inventory already released for txId={}", request.txId());
                return RollbackResponse.success(request.txId(), "Inventory already released");
            }

            // Release reservation
            if (reservation.canRelease()) {
                reservation.release();
                log.info("Inventory released successfully for txId={}", request.txId());
                return RollbackResponse.success(request.txId(), "Inventory released successfully");
            } else {
                log.warn("Cannot release reservation in status {} for txId={}", reservation.getStatus(), request.txId());
                return RollbackResponse.failure(request.txId(), "Cannot release reservation in current status");
            }

        } catch (Exception e) {
            log.error("Inventory release error for txId={}", request.txId(), e);
            return RollbackResponse.failure(request.txId(), "Release error: " + e.getMessage());
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

    private String generateReservationRef() {
        return "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private NotifyResponse createSuccessResponse(UUID txId, String reservationRef) {
        return NotifyResponse.success(txId, "Inventory reserved", reservationRef);
    }
}
