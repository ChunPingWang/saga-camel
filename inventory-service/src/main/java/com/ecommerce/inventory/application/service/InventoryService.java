package com.ecommerce.inventory.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.inventory.application.port.in.ReserveInventoryUseCase;
import com.ecommerce.inventory.application.port.in.RollbackReservationUseCase;
import com.ecommerce.inventory.domain.model.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service implementation for inventory reservation.
 */
@Service
public class InventoryService implements ReserveInventoryUseCase, RollbackReservationUseCase {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final Random random = new Random();

    // In-memory store for idempotency
    private final Map<UUID, Reservation> reservations = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> releasedReservations = new ConcurrentHashMap<>();

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
    public Reservation reserveInventory(NotifyRequest request) {
        log.info("Reserving inventory for txId={}, orderId={}, items={}",
                request.txId(), request.orderId(), request.items().size());

        // Idempotency check
        Reservation existingReservation = reservations.get(request.txId());
        if (existingReservation != null) {
            log.info("Returning cached reservation for txId={}", request.txId());
            return existingReservation;
        }

        // Simulate delay if enabled
        simulateDelay();

        // Check for simulated failure
        if (shouldSimulateFailure()) {
            log.warn("Simulating inventory failure for txId={}", request.txId());
            Reservation failedReservation = Reservation.outOfStock(
                    request.txId(),
                    request.orderId(),
                    request.items()
            );
            reservations.put(request.txId(), failedReservation);
            return failedReservation;
        }

        Reservation reservation = Reservation.reserve(
                request.txId(),
                request.orderId(),
                request.items()
        );

        reservations.put(request.txId(), reservation);

        log.info("Inventory reserved successfully: referenceNumber={}, status={}",
                reservation.getReferenceNumber(), reservation.getStatus());

        return reservation;
    }

    @Override
    public RollbackResponse releaseInventory(RollbackRequest request) {
        log.info("Releasing inventory for txId={}", request.txId());

        // Check if already released (idempotency)
        if (releasedReservations.containsKey(request.txId())) {
            log.info("Inventory already released for txId={}", request.txId());
            return RollbackResponse.success(request.txId(), "Inventory already released");
        }

        // Check if reservation exists
        Reservation reservation = reservations.get(request.txId());
        if (reservation == null) {
            log.info("No reservation found for txId={}", request.txId());
            return RollbackResponse.success(request.txId(), "No reservation to release");
        }

        // Simulate delay if enabled
        simulateDelay();

        // Mark as released
        releasedReservations.put(request.txId(), true);
        log.info("Inventory released successfully for txId={}", request.txId());

        return RollbackResponse.success(request.txId(), "Inventory released successfully");
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
