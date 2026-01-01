package com.ecommerce.order.infrastructure.checker;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.service.RollbackService;
import com.ecommerce.order.domain.model.TransactionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread that monitors a single transaction for timeout or failure.
 * Periodically checks transaction status and triggers rollback if needed.
 */
public class TransactionCheckerThread implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TransactionCheckerThread.class);

    private final UUID txId;
    private final UUID orderId;
    private final Map<ServiceName, Integer> timeouts;
    private final long checkIntervalMs;
    private final TransactionLogPort transactionLogPort;
    private final RollbackService rollbackService;
    private final CheckerThreadManager manager;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public TransactionCheckerThread(
            UUID txId,
            UUID orderId,
            Map<ServiceName, Integer> timeouts,
            long checkIntervalMs,
            TransactionLogPort transactionLogPort,
            RollbackService rollbackService,
            CheckerThreadManager manager) {
        this.txId = txId;
        this.orderId = orderId;
        this.timeouts = timeouts;
        this.checkIntervalMs = checkIntervalMs;
        this.transactionLogPort = transactionLogPort;
        this.rollbackService = rollbackService;
        this.manager = manager;
    }

    @Override
    public void run() {
        log.info("Checker thread started for txId={}", txId);

        try {
            while (running.get()) {
                CheckResult result = checkTransactionStatus();

                switch (result.action) {
                    case CONTINUE:
                        // Keep monitoring
                        Thread.sleep(checkIntervalMs);
                        break;

                    case TRIGGER_ROLLBACK:
                        log.info("Checker thread triggering rollback for txId={}, reason={}",
                                txId, result.reason);
                        triggerRollback(result.successfulServices);
                        stop();
                        break;

                    case STOP:
                        log.info("Checker thread stopping for txId={}, reason={}",
                                txId, result.reason);
                        stop();
                        break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Checker thread interrupted for txId={}", txId);
        } catch (Exception e) {
            log.error("Error in checker thread for txId={}: {}", txId, e.getMessage(), e);
        } finally {
            manager.removeThread(txId);
            log.info("Checker thread exited for txId={}", txId);
        }
    }

    public void stop() {
        running.set(false);
    }

    private CheckResult checkTransactionStatus() {
        List<TransactionLog> logs = transactionLogPort.findLatestByTxId(txId.toString());

        if (logs.isEmpty()) {
            return new CheckResult(Action.CONTINUE, "No logs yet", List.of());
        }

        List<ServiceName> successfulServices = new ArrayList<>();
        boolean hasFailure = false;
        boolean hasRollbackComplete = true;
        boolean allServicesComplete = true;
        int completedCount = 0;

        for (TransactionLog logEntry : logs) {
            ServiceName service = logEntry.getServiceName();
            TransactionStatus status = logEntry.getStatus();

            switch (status) {
                case S -> {
                    successfulServices.add(service);
                    completedCount++;
                }
                case F -> {
                    hasFailure = true;
                    completedCount++;
                }
                case R -> {
                    // Rollback complete for this service
                    completedCount++;
                }
                case RF -> {
                    // Rollback failed - still counts as "attempted"
                    hasRollbackComplete = false;
                    completedCount++;
                }
                case U -> {
                    // Check for timeout
                    allServicesComplete = false;
                    if (isTimedOut(logEntry)) {
                        return new CheckResult(
                                Action.TRIGGER_ROLLBACK,
                                "Timeout detected for " + service,
                                new ArrayList<>(successfulServices)
                        );
                    }
                }
            }
        }

        // Check if all expected services have completed (success or rollback)
        if (allServicesComplete && completedCount == timeouts.size()) {
            return new CheckResult(Action.STOP, "All services completed", List.of());
        }

        // Check if we're in rollback state and all rollbacks are done
        if (hasFailure && !successfulServices.isEmpty()) {
            // There was a failure and we have successful services to rollback
            return new CheckResult(
                    Action.TRIGGER_ROLLBACK,
                    "Failure detected",
                    new ArrayList<>(successfulServices)
            );
        }

        // Check if all services that need rollback have been rolled back
        boolean allRolledBack = logs.stream()
                .allMatch(l -> l.getStatus() == TransactionStatus.R ||
                        l.getStatus() == TransactionStatus.RF ||
                        l.getStatus() == TransactionStatus.S);
        if (allRolledBack && logs.stream().anyMatch(l -> l.getStatus() == TransactionStatus.R)) {
            return new CheckResult(Action.STOP, "Rollback completed", List.of());
        }

        return new CheckResult(Action.CONTINUE, "Monitoring", successfulServices);
    }

    private boolean isTimedOut(TransactionLog logEntry) {
        ServiceName service = logEntry.getServiceName();
        Integer timeoutSeconds = timeouts.get(service);

        if (timeoutSeconds == null) {
            log.warn("No timeout configured for service {}, using default 60s", service);
            timeoutSeconds = 60;
        }

        LocalDateTime threshold = LocalDateTime.now().minusSeconds(timeoutSeconds);
        return logEntry.getCreatedAt().isBefore(threshold);
    }

    private void triggerRollback(List<ServiceName> successfulServices) {
        try {
            rollbackService.executeRollback(txId, orderId, successfulServices);
        } catch (Exception e) {
            log.error("Failed to execute rollback for txId={}: {}", txId, e.getMessage(), e);
        }
    }

    private enum Action {
        CONTINUE,
        TRIGGER_ROLLBACK,
        STOP
    }

    private record CheckResult(Action action, String reason, List<ServiceName> successfulServices) {
    }
}
