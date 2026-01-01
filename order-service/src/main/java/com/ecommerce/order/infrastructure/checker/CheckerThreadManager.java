package com.ecommerce.order.infrastructure.checker;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.service.RollbackService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages checker threads for all active transactions.
 * Maintains a map of transaction IDs to their corresponding checker threads.
 */
@Component
public class CheckerThreadManager {

    private static final Logger log = LoggerFactory.getLogger(CheckerThreadManager.class);
    private static final long DEFAULT_CHECK_INTERVAL_MS = 1000L; // 1 second default

    private final ConcurrentHashMap<UUID, ThreadEntry> activeThreads = new ConcurrentHashMap<>();
    private final TransactionLogPort transactionLogPort;
    private final RollbackService rollbackService;
    private long checkIntervalMs = DEFAULT_CHECK_INTERVAL_MS;

    public CheckerThreadManager(TransactionLogPort transactionLogPort, RollbackService rollbackService) {
        this.transactionLogPort = transactionLogPort;
        this.rollbackService = rollbackService;
    }

    /**
     * Starts a new checker thread for the given transaction.
     * If a thread already exists for this transaction, this method does nothing.
     */
    public void startCheckerThread(UUID txId, UUID orderId, Map<ServiceName, Integer> timeouts) {
        activeThreads.computeIfAbsent(txId, id -> {
            log.info("Starting checker thread for txId={}", txId);

            TransactionCheckerThread checker = new TransactionCheckerThread(
                    txId,
                    orderId,
                    timeouts,
                    checkIntervalMs,
                    transactionLogPort,
                    rollbackService,
                    this
            );

            Thread thread = new Thread(checker, "checker-" + txId.toString().substring(0, 8));
            thread.setDaemon(true);
            thread.start();

            return new ThreadEntry(thread, checker);
        });
    }

    /**
     * Stops the checker thread for the given transaction.
     */
    public void stopCheckerThread(UUID txId) {
        ThreadEntry entry = activeThreads.remove(txId);
        if (entry != null) {
            log.info("Stopping checker thread for txId={}", txId);
            entry.checker.stop();
            entry.thread.interrupt();
        }
    }

    /**
     * Removes the thread entry from the map.
     * Called by the checker thread when it completes normally.
     */
    public void removeThread(UUID txId) {
        ThreadEntry entry = activeThreads.remove(txId);
        if (entry != null) {
            log.debug("Removed checker thread reference for txId={}", txId);
        }
    }

    /**
     * Checks if there is an active checker thread for the given transaction.
     */
    public boolean hasActiveThread(UUID txId) {
        return activeThreads.containsKey(txId);
    }

    /**
     * Returns the number of active checker threads.
     */
    public int getActiveThreadCount() {
        return activeThreads.size();
    }

    /**
     * Returns the set of transaction IDs with active checker threads.
     */
    public Set<UUID> getActiveTransactionIds() {
        return Set.copyOf(activeThreads.keySet());
    }

    /**
     * Sets the check interval for new checker threads.
     */
    public void setCheckIntervalMs(long intervalMs) {
        this.checkIntervalMs = intervalMs;
    }

    /**
     * Stops all active checker threads.
     * Called during application shutdown.
     */
    @PreDestroy
    public void shutdownAll() {
        log.info("Shutting down all checker threads, count={}", activeThreads.size());

        for (Map.Entry<UUID, ThreadEntry> entry : activeThreads.entrySet()) {
            log.debug("Stopping checker thread for txId={}", entry.getKey());
            entry.getValue().checker.stop();
            entry.getValue().thread.interrupt();
        }

        activeThreads.clear();
        log.info("All checker threads shut down");
    }

    private record ThreadEntry(Thread thread, TransactionCheckerThread checker) {
    }
}
