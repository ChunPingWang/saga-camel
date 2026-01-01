package com.ecommerce.order.infrastructure.recovery;

import com.ecommerce.order.application.service.SagaRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Application runner that triggers saga recovery on startup.
 * Scans for unfinished transactions and resumes monitoring.
 */
@Component
@Order(1) // Run early in startup sequence
public class SagaRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryRunner.class);

    private final SagaRecoveryService recoveryService;

    public SagaRecoveryRunner(SagaRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Starting Saga Recovery on application startup ===");

        try {
            int recoveredCount = recoveryService.recoverUnfinishedTransactions();

            if (recoveredCount > 0) {
                log.info("=== Saga Recovery completed: {} transactions resumed ===", recoveredCount);
            } else {
                log.info("=== Saga Recovery completed: No unfinished transactions found ===");
            }

        } catch (Exception e) {
            log.error("=== Saga Recovery failed: {} ===", e.getMessage(), e);
            // Don't throw - allow application to continue even if recovery fails
        }
    }
}
