package com.ecommerce.order.application.service;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.infrastructure.checker.CheckerThreadManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Service for recovering unfinished saga transactions on startup.
 * Scans for incomplete transactions and resumes monitoring.
 */
@Service
public class SagaRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryService.class);

    private final TransactionLogPort transactionLogPort;
    private final CheckerThreadManager checkerThreadManager;
    private final Map<ServiceName, Integer> timeouts;

    @Autowired
    public SagaRecoveryService(
            TransactionLogPort transactionLogPort,
            CheckerThreadManager checkerThreadManager) {
        this.transactionLogPort = transactionLogPort;
        this.checkerThreadManager = checkerThreadManager;
        this.timeouts = getDefaultTimeouts();
    }

    // Constructor with custom timeouts (for testing)
    public SagaRecoveryService(
            TransactionLogPort transactionLogPort,
            CheckerThreadManager checkerThreadManager,
            Map<ServiceName, Integer> timeouts) {
        this.transactionLogPort = transactionLogPort;
        this.checkerThreadManager = checkerThreadManager;
        this.timeouts = timeouts != null ? timeouts : getDefaultTimeouts();
    }

    /**
     * Recover all unfinished transactions by starting checker threads for them.
     *
     * @return number of transactions recovered
     */
    public int recoverUnfinishedTransactions() {
        log.info("Starting saga recovery - scanning for unfinished transactions...");

        var unfinished = transactionLogPort.findUnfinishedTransactions();
        log.info("Found {} unfinished transactions to recover", unfinished.size());

        int recovered = 0;

        for (var tx : unfinished) {
            try {
                if (checkerThreadManager.hasActiveThread(tx.txId())) {
                    log.debug("Skipping txId={} - already being monitored", tx.txId());
                    continue;
                }

                log.info("Recovering transaction txId={}, orderId={}", tx.txId(), tx.orderId());
                checkerThreadManager.startCheckerThread(tx.txId(), tx.orderId(), timeouts);
                recovered++;

            } catch (Exception e) {
                log.error("Failed to recover transaction txId={}: {}", tx.txId(), e.getMessage(), e);
            }
        }

        log.info("Saga recovery completed - recovered {} transactions", recovered);
        return recovered;
    }

    private static Map<ServiceName, Integer> getDefaultTimeouts() {
        return Map.of(
                ServiceName.CREDIT_CARD, 30,
                ServiceName.INVENTORY, 60,
                ServiceName.LOGISTICS, 120
        );
    }
}
