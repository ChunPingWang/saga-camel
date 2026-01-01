package com.ecommerce.creditcard.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.creditcard.application.port.in.ProcessPaymentUseCase;
import com.ecommerce.creditcard.application.port.in.RollbackPaymentUseCase;
import com.ecommerce.creditcard.domain.model.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Credit card service implementation.
 * Simulates payment processing with configurable failure rate.
 */
@Service
public class CreditCardService implements ProcessPaymentUseCase, RollbackPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreditCardService.class);

    // In-memory store for idempotency (in real app, would be persistent)
    private final Map<UUID, Payment> processedPayments = new ConcurrentHashMap<>();

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
    public NotifyResponse processPayment(NotifyRequest request) {
        MDC.put("txId", request.txId().toString());
        try {
            log.info("Processing payment for txId={}, orderId={}, amount={}",
                    request.txId(), request.orderId(), request.totalAmount());

            // Idempotency check
            Payment existingPayment = processedPayments.get(request.txId());
            if (existingPayment != null) {
                log.info("Idempotent response for already processed payment txId={}", request.txId());
                return createSuccessResponse(request.txId(), existingPayment.getAuthorizationCode());
            }

            // Simulate delay
            simulateDelay();

            // Simulate failure
            if (shouldFail()) {
                log.warn("Simulated payment failure for txId={}", request.txId());
                return NotifyResponse.failure(request.txId(), "Payment declined: Insufficient funds (simulated)");
            }

            // Process payment
            Payment payment = Payment.create(
                    request.txId(),
                    request.orderId(),
                    request.creditCardNumber(),
                    request.totalAmount()
            );

            String authCode = generateAuthCode();
            payment.authorize(authCode);
            payment.capture();

            processedPayments.put(request.txId(), payment);

            log.info("Payment processed successfully for txId={}, authCode={}", request.txId(), authCode);
            return createSuccessResponse(request.txId(), authCode);

        } catch (Exception e) {
            log.error("Payment processing error for txId={}", request.txId(), e);
            return NotifyResponse.failure(request.txId(), "Payment processing error: " + e.getMessage());
        } finally {
            MDC.remove("txId");
        }
    }

    @Override
    public RollbackResponse rollbackPayment(RollbackRequest request) {
        MDC.put("txId", request.txId().toString());
        try {
            log.info("Rolling back payment for txId={}", request.txId());

            // Check if payment exists
            Payment payment = processedPayments.get(request.txId());
            if (payment == null) {
                log.info("No payment found for txId={}, rollback is no-op", request.txId());
                return RollbackResponse.success(request.txId(), "No payment to rollback");
            }

            // Idempotency: already refunded
            if (payment.getStatus() == Payment.PaymentStatus.REFUNDED) {
                log.info("Payment already refunded for txId={}", request.txId());
                return RollbackResponse.success(request.txId(), "Payment already refunded");
            }

            // Perform refund
            if (payment.canRefund()) {
                payment.refund();
                log.info("Payment refunded successfully for txId={}", request.txId());
                return RollbackResponse.success(request.txId(), "Payment refunded successfully");
            } else {
                log.warn("Cannot refund payment in status {} for txId={}", payment.getStatus(), request.txId());
                return RollbackResponse.failure(request.txId(), "Cannot refund payment in current status");
            }

        } catch (Exception e) {
            log.error("Payment rollback error for txId={}", request.txId(), e);
            return RollbackResponse.failure(request.txId(), "Rollback error: " + e.getMessage());
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

    private String generateAuthCode() {
        return "AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private NotifyResponse createSuccessResponse(UUID txId, String authCode) {
        return NotifyResponse.success(txId, "Payment captured", authCode);
    }
}
