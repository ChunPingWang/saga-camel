package com.ecommerce.creditcard.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.creditcard.application.port.in.ProcessPaymentUseCase;
import com.ecommerce.creditcard.application.port.in.RollbackPaymentUseCase;
import com.ecommerce.creditcard.domain.model.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service implementation for credit card payment processing.
 */
@Service
public class CreditCardService implements ProcessPaymentUseCase, RollbackPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreditCardService.class);
    private static final Random random = new Random();

    // In-memory store for idempotency
    private final Map<UUID, Payment> processedPayments = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> refundedPayments = new ConcurrentHashMap<>();

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
    public Payment processPayment(NotifyRequest request) {
        log.info("Processing payment for txId={}, orderId={}, amount={}",
                request.txId(), request.orderId(), request.totalAmount());

        // Idempotency check
        Payment existingPayment = processedPayments.get(request.txId());
        if (existingPayment != null) {
            log.info("Returning cached payment for txId={}", request.txId());
            return existingPayment;
        }

        // Simulate delay if enabled
        simulateDelay();

        // Check for simulated failure
        if (shouldSimulateFailure()) {
            log.warn("Simulating payment failure for txId={}", request.txId());
            Payment failedPayment = Payment.declined(
                    request.txId(),
                    request.orderId(),
                    request.totalAmount(),
                    request.creditCardNumber()
            );
            processedPayments.put(request.txId(), failedPayment);
            return failedPayment;
        }

        Payment payment = Payment.process(
                request.txId(),
                request.orderId(),
                request.totalAmount(),
                request.creditCardNumber()
        );

        processedPayments.put(request.txId(), payment);

        log.info("Payment processed successfully: referenceNumber={}, status={}",
                payment.getReferenceNumber(), payment.getStatus());

        return payment;
    }

    @Override
    public RollbackResponse rollbackPayment(RollbackRequest request) {
        log.info("Rolling back payment for txId={}", request.txId());

        // Check if already refunded (idempotency)
        if (refundedPayments.containsKey(request.txId())) {
            log.info("Payment already refunded for txId={}", request.txId());
            return RollbackResponse.success(request.txId(), "Payment already refunded");
        }

        // Check if payment exists
        Payment payment = processedPayments.get(request.txId());
        if (payment == null) {
            log.info("No payment found for txId={}", request.txId());
            return RollbackResponse.success(request.txId(), "No payment to rollback");
        }

        // Simulate delay if enabled
        simulateDelay();

        // Mark as refunded
        refundedPayments.put(request.txId(), true);
        log.info("Payment refunded successfully for txId={}", request.txId());

        return RollbackResponse.success(request.txId(), "Payment refunded successfully");
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
