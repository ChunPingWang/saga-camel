package com.ecommerce.order.infrastructure.observability;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection for saga orchestration.
 * Uses Micrometer for integration with various monitoring systems.
 */
@Component
public class SagaMetrics {

    private static final String METRIC_PREFIX = "saga.";

    private final MeterRegistry registry;

    // Counters
    private final Counter sagaStartedCounter;
    private final Counter sagaCompletedCounter;
    private final Counter sagaFailedCounter;
    private final Counter rollbackTriggeredCounter;
    private final Counter rollbackSuccessCounter;
    private final Counter rollbackFailedCounter;
    private final Counter timeoutDetectedCounter;
    private final Counter adminNotificationCounter;

    // Per-service counters
    private final Map<ServiceName, Counter> serviceCallCounters = new ConcurrentHashMap<>();
    private final Map<ServiceName, Counter> serviceSuccessCounters = new ConcurrentHashMap<>();
    private final Map<ServiceName, Counter> serviceFailureCounters = new ConcurrentHashMap<>();

    // Timers
    private final Timer sagaDurationTimer;
    private final Map<ServiceName, Timer> serviceCallTimers = new ConcurrentHashMap<>();
    private final Timer rollbackDurationTimer;

    // Availability metrics
    private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

    public SagaMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Initialize counters
        this.sagaStartedCounter = Counter.builder(METRIC_PREFIX + "transactions.started")
                .description("Number of saga transactions started")
                .register(registry);

        this.sagaCompletedCounter = Counter.builder(METRIC_PREFIX + "transactions.completed")
                .description("Number of saga transactions completed successfully")
                .register(registry);

        this.sagaFailedCounter = Counter.builder(METRIC_PREFIX + "transactions.failed")
                .description("Number of saga transactions that failed")
                .register(registry);

        this.rollbackTriggeredCounter = Counter.builder(METRIC_PREFIX + "rollback.triggered")
                .description("Number of rollback operations triggered")
                .register(registry);

        this.rollbackSuccessCounter = Counter.builder(METRIC_PREFIX + "rollback.success")
                .description("Number of successful rollback operations")
                .register(registry);

        this.rollbackFailedCounter = Counter.builder(METRIC_PREFIX + "rollback.failed")
                .description("Number of failed rollback operations")
                .register(registry);

        this.timeoutDetectedCounter = Counter.builder(METRIC_PREFIX + "timeout.detected")
                .description("Number of timeouts detected")
                .register(registry);

        this.adminNotificationCounter = Counter.builder(METRIC_PREFIX + "notification.admin")
                .description("Number of admin notifications sent")
                .register(registry);

        // Initialize timers
        this.sagaDurationTimer = Timer.builder(METRIC_PREFIX + "transactions.duration")
                .description("Duration of saga transactions")
                .register(registry);

        this.rollbackDurationTimer = Timer.builder(METRIC_PREFIX + "rollback.duration")
                .description("Duration of rollback operations")
                .register(registry);

        // Initialize per-service metrics for all services
        for (ServiceName service : ServiceName.values()) {
            if (service != ServiceName.SAGA) {
                initializeServiceMetrics(service);
            }
        }

        // Register availability gauge
        registry.gauge(METRIC_PREFIX + "uptime.seconds", this,
                metrics -> (System.currentTimeMillis() - metrics.startTime.get()) / 1000.0);
    }

    private void initializeServiceMetrics(ServiceName service) {
        String serviceName = service.name().toLowerCase();

        serviceCallCounters.put(service,
                Counter.builder(METRIC_PREFIX + "service.calls")
                        .tag("service", serviceName)
                        .description("Number of calls to service")
                        .register(registry));

        serviceSuccessCounters.put(service,
                Counter.builder(METRIC_PREFIX + "service.success")
                        .tag("service", serviceName)
                        .description("Number of successful service calls")
                        .register(registry));

        serviceFailureCounters.put(service,
                Counter.builder(METRIC_PREFIX + "service.failure")
                        .tag("service", serviceName)
                        .description("Number of failed service calls")
                        .register(registry));

        serviceCallTimers.put(service,
                Timer.builder(METRIC_PREFIX + "service.duration")
                        .tag("service", serviceName)
                        .description("Duration of service calls")
                        .register(registry));
    }

    // Transaction lifecycle metrics
    public void recordSagaStarted() {
        sagaStartedCounter.increment();
    }

    public void recordSagaCompleted() {
        sagaCompletedCounter.increment();
    }

    public void recordSagaFailed() {
        sagaFailedCounter.increment();
    }

    public void recordSagaDuration(Instant startTime) {
        sagaDurationTimer.record(Duration.between(startTime, Instant.now()));
    }

    public void recordSagaDuration(long durationMillis) {
        sagaDurationTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    // Service call metrics
    public void recordServiceCall(ServiceName service) {
        Counter counter = serviceCallCounters.get(service);
        if (counter != null) {
            counter.increment();
        }
    }

    public void recordServiceSuccess(ServiceName service) {
        Counter counter = serviceSuccessCounters.get(service);
        if (counter != null) {
            counter.increment();
        }
    }

    public void recordServiceFailure(ServiceName service) {
        Counter counter = serviceFailureCounters.get(service);
        if (counter != null) {
            counter.increment();
        }
    }

    public void recordServiceDuration(ServiceName service, Instant startTime) {
        Timer timer = serviceCallTimers.get(service);
        if (timer != null) {
            timer.record(Duration.between(startTime, Instant.now()));
        }
    }

    public void recordServiceDuration(ServiceName service, long durationMillis) {
        Timer timer = serviceCallTimers.get(service);
        if (timer != null) {
            timer.record(durationMillis, TimeUnit.MILLISECONDS);
        }
    }

    // Rollback metrics
    public void recordRollbackTriggered() {
        rollbackTriggeredCounter.increment();
    }

    public void recordRollbackSuccess() {
        rollbackSuccessCounter.increment();
    }

    public void recordRollbackFailed() {
        rollbackFailedCounter.increment();
    }

    public void recordRollbackDuration(Instant startTime) {
        rollbackDurationTimer.record(Duration.between(startTime, Instant.now()));
    }

    public void recordRollbackDuration(long durationMillis) {
        rollbackDurationTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    // Timeout and notification metrics
    public void recordTimeoutDetected() {
        timeoutDetectedCounter.increment();
    }

    public void recordAdminNotification() {
        adminNotificationCounter.increment();
    }

    // Convenience method for recording transaction status
    public void recordTransactionStatus(TransactionStatus status) {
        switch (status) {
            case S -> recordSagaCompleted();
            case F, R, RF -> recordSagaFailed();
            default -> {} // U, D are intermediate states
        }
    }

    // Get uptime in seconds
    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime.get()) / 1000;
    }
}
