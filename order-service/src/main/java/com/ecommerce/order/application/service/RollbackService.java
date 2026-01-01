package com.ecommerce.order.application.service;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.application.port.out.NotificationPort;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service for executing saga rollback (compensation).
 * Rolls back services in reverse order of their execution.
 */
@Service
public class RollbackService {

    private static final Logger log = LoggerFactory.getLogger(RollbackService.class);
    private static final int DEFAULT_MAX_RETRIES = 5;

    private final TransactionLogPort transactionLogPort;
    private final ServiceClientPort serviceClientPort;
    private final WebSocketPort webSocketPort;
    private final NotificationPort notificationPort;

    public RollbackService(TransactionLogPort transactionLogPort,
                           ServiceClientPort serviceClientPort,
                           WebSocketPort webSocketPort,
                           NotificationPort notificationPort) {
        this.transactionLogPort = transactionLogPort;
        this.serviceClientPort = serviceClientPort;
        this.webSocketPort = webSocketPort;
        this.notificationPort = notificationPort;
    }

    /**
     * Execute rollback for all successful services in reverse order.
     *
     * @param txId the transaction ID
     * @param orderId the order ID
     * @param successfulServices list of services that completed successfully (in execution order)
     */
    public void executeRollback(UUID txId, UUID orderId, List<ServiceName> successfulServices) {
        MDC.put("txId", txId.toString());
        try {
            log.info("Starting rollback for txId={}, services to rollback: {}", txId, successfulServices);

            if (successfulServices.isEmpty()) {
                log.info("No services to rollback for txId={}", txId);
                webSocketPort.sendRolledBack(txId, orderId);
                return;
            }

            // Reverse the order for compensation
            List<ServiceName> reversedServices = new ArrayList<>(successfulServices);
            Collections.reverse(reversedServices);

            boolean anyFailed = false;
            List<String> failureMessages = new ArrayList<>();

            for (ServiceName serviceName : reversedServices) {
                log.info("Rolling back service {} for txId={}", serviceName, txId);

                try {
                    RollbackRequest request = RollbackRequest.of(txId, orderId, null);
                    RollbackResponse response = serviceClientPort.rollback(serviceName, request);

                    if (response.success()) {
                        log.info("Rollback successful for service {} txId={}", serviceName, txId);
                        transactionLogPort.recordStatus(txId, orderId, serviceName, TransactionStatus.R);
                        webSocketPort.sendRollbackProgress(txId, orderId, serviceName);
                    } else {
                        log.error("Rollback failed for service {} txId={}: {}", serviceName, txId, response.message());
                        transactionLogPort.recordStatusWithError(txId, orderId, serviceName,
                                TransactionStatus.RF, response.message());
                        anyFailed = true;
                        failureMessages.add(serviceName.name() + ": " + response.message());
                    }
                } catch (Exception e) {
                    log.error("Exception during rollback of service {} txId={}", serviceName, txId, e);
                    transactionLogPort.recordStatusWithError(txId, orderId, serviceName,
                            TransactionStatus.RF, e.getMessage());
                    anyFailed = true;
                    failureMessages.add(serviceName.name() + ": " + e.getMessage());
                }
            }

            // Send final notification
            if (anyFailed) {
                String errorSummary = String.join("; ", failureMessages);
                log.error("Rollback completed with failures for txId={}: {}", txId, errorSummary);
                webSocketPort.sendRollbackFailed(txId, orderId, errorSummary);
            } else {
                log.info("Rollback completed successfully for txId={}", txId);
                webSocketPort.sendRolledBack(txId, orderId);
            }

        } finally {
            MDC.remove("txId");
        }
    }

    /**
     * Get list of services that completed successfully for a transaction.
     *
     * @param txId the transaction ID
     * @return list of successful service names in execution order
     */
    public List<ServiceName> getSuccessfulServices(UUID txId) {
        return transactionLogPort.findSuccessfulServices(txId);
    }

    /**
     * Execute rollback with retry logic and admin notification on exhausted retries.
     *
     * @param txId the transaction ID
     * @param orderId the order ID
     * @param successfulServices list of services that completed successfully
     * @param maxRetries maximum number of retry attempts per service
     */
    public void executeRollbackWithRetry(UUID txId, UUID orderId, List<ServiceName> successfulServices, int maxRetries) {
        MDC.put("txId", txId.toString());
        try {
            log.info("Starting rollback with retry for txId={}, maxRetries={}, services: {}",
                    txId, maxRetries, successfulServices);

            if (successfulServices.isEmpty()) {
                log.info("No services to rollback for txId={}", txId);
                webSocketPort.sendRolledBack(txId, orderId);
                return;
            }

            // Reverse the order for compensation
            List<ServiceName> reversedServices = new ArrayList<>(successfulServices);
            Collections.reverse(reversedServices);

            List<String> failureMessages = new ArrayList<>();

            for (ServiceName serviceName : reversedServices) {
                boolean success = rollbackServiceWithRetry(txId, orderId, serviceName, maxRetries);
                if (!success) {
                    failureMessages.add(serviceName.name() + ": Max retries exceeded");
                }
            }

            // Send final notification
            if (!failureMessages.isEmpty()) {
                String errorSummary = String.join("; ", failureMessages);
                log.error("Rollback completed with failures for txId={}: {}", txId, errorSummary);
                webSocketPort.sendRollbackFailed(txId, orderId, errorSummary);
            } else {
                log.info("Rollback completed successfully for txId={}", txId);
                webSocketPort.sendRolledBack(txId, orderId);
            }

        } finally {
            MDC.remove("txId");
        }
    }

    /**
     * Attempt to rollback a single service with retries.
     * Sends admin notification if all retries are exhausted.
     *
     * @return true if rollback succeeded, false if all retries exhausted
     */
    private boolean rollbackServiceWithRetry(UUID txId, UUID orderId, ServiceName serviceName, int maxRetries) {
        String lastErrorMessage = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("Rollback attempt {}/{} for service {} txId={}", attempt, maxRetries, serviceName, txId);

            try {
                RollbackRequest request = RollbackRequest.of(txId, orderId, null);
                RollbackResponse response = serviceClientPort.rollback(serviceName, request);

                if (response.success()) {
                    log.info("Rollback successful for service {} txId={} (attempt {})",
                            serviceName, txId, attempt);
                    transactionLogPort.recordStatus(txId, orderId, serviceName, TransactionStatus.R);
                    webSocketPort.sendRollbackProgress(txId, orderId, serviceName);
                    return true;
                } else {
                    lastErrorMessage = response.message();
                    log.warn("Rollback attempt {}/{} failed for service {} txId={}: {}",
                            attempt, maxRetries, serviceName, txId, lastErrorMessage);
                }
            } catch (Exception e) {
                lastErrorMessage = e.getMessage();
                log.warn("Rollback attempt {}/{} exception for service {} txId={}: {}",
                        attempt, maxRetries, serviceName, txId, e.getMessage());
            }

            // Wait before retry (exponential backoff)
            if (attempt < maxRetries) {
                try {
                    long waitMs = (long) Math.pow(2, attempt) * 100;
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // All retries exhausted - record failure and notify admin
        log.error("All {} rollback attempts exhausted for service {} txId={}",
                maxRetries, serviceName, txId);

        transactionLogPort.recordStatusWithError(txId, orderId, serviceName,
                TransactionStatus.RF, lastErrorMessage);

        // Send admin notification
        notificationPort.sendRollbackFailureAlert(txId, orderId, serviceName, lastErrorMessage, maxRetries);

        return false;
    }
}
