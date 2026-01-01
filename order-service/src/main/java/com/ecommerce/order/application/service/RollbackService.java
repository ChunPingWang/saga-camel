package com.ecommerce.order.application.service;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
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

    private final TransactionLogPort transactionLogPort;
    private final ServiceClientPort serviceClientPort;
    private final WebSocketPort webSocketPort;

    public RollbackService(TransactionLogPort transactionLogPort,
                           ServiceClientPort serviceClientPort,
                           WebSocketPort webSocketPort) {
        this.transactionLogPort = transactionLogPort;
        this.serviceClientPort = serviceClientPort;
        this.webSocketPort = webSocketPort;
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
}
