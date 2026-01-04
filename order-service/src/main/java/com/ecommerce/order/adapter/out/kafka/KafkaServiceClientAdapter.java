package com.ecommerce.order.adapter.out.kafka;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.adapter.in.kafka.SagaKafkaMessage;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Kafka-based adapter for downstream service communication.
 * Implements ServiceClientPort using Kafka messaging with Resilience4j protection.
 *
 * Note: In CDC mode, commands are typically sent via outbox table.
 * This adapter is used for direct Kafka messaging when needed.
 */
@Component
@Primary
@ConditionalOnProperty(name = "saga.messaging.type", havingValue = "kafka")
public class KafkaServiceClientAdapter implements ServiceClientPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaServiceClientAdapter.class);

    private final KafkaTemplate<String, SagaKafkaMessage> kafkaTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    @Value("${saga.kafka.topics.credit-card-commands:saga.credit-card.commands}")
    private String creditCardCommandsTopic;

    @Value("${saga.kafka.topics.inventory-commands:saga.inventory.commands}")
    private String inventoryCommandsTopic;

    @Value("${saga.kafka.topics.logistics-commands:saga.logistics.commands}")
    private String logisticsCommandsTopic;

    @Value("${saga.kafka.send-timeout-ms:5000}")
    private long sendTimeoutMs;

    public KafkaServiceClientAdapter(
            KafkaTemplate<String, SagaKafkaMessage> kafkaTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            BulkheadRegistry bulkheadRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
    }

    @Override
    public ServiceName getServiceName() {
        return null; // Handles all services
    }

    @Override
    public NotifyResponse notify(ServiceName serviceName, NotifyRequest request) {
        String topic = getCommandTopic(serviceName);
        String instanceName = serviceName.name() + "_KAFKA";

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        Retry retry = retryRegistry.retry(instanceName);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(instanceName);

        Supplier<NotifyResponse> decoratedSupplier = Decorators
                .ofSupplier(() -> executeNotify(topic, serviceName, request))
                .withBulkhead(bulkhead)
                .withRetry(retry)
                .withCircuitBreaker(circuitBreaker)
                .decorate();

        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException e) {
            log.warn("txId={} - Circuit breaker OPEN for Kafka service {}, skipping send",
                    request.txId(), serviceName);
            return NotifyResponse.failure(request.txId(),
                    "Circuit breaker is OPEN for " + serviceName.getDisplayName());
        } catch (BulkheadFullException e) {
            log.warn("txId={} - Bulkhead full for Kafka service {}, rejecting call",
                    request.txId(), serviceName);
            return NotifyResponse.failure(request.txId(),
                    "Service " + serviceName.getDisplayName() + " is overloaded");
        } catch (Exception e) {
            log.error("txId={} - Kafka send failed for {}: {}",
                    request.txId(), serviceName, e.getMessage());
            return NotifyResponse.failure(request.txId(), "Kafka send failed: " + e.getMessage());
        }
    }

    @Override
    public RollbackResponse rollback(ServiceName serviceName, RollbackRequest request) {
        String topic = getCommandTopic(serviceName);
        String instanceName = serviceName.name() + "_KAFKA";

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        Retry retry = retryRegistry.retry(instanceName);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(instanceName);

        Supplier<RollbackResponse> decoratedSupplier = Decorators
                .ofSupplier(() -> executeRollback(topic, serviceName, request))
                .withBulkhead(bulkhead)
                .withRetry(retry)
                .withCircuitBreaker(circuitBreaker)
                .decorate();

        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException e) {
            log.warn("txId={} - Circuit breaker OPEN for Kafka service {} rollback, skipping send",
                    request.txId(), serviceName);
            return RollbackResponse.failure(request.txId(),
                    "Circuit breaker is OPEN for " + serviceName.getDisplayName() + " rollback");
        } catch (BulkheadFullException e) {
            log.warn("txId={} - Bulkhead full for Kafka service {} rollback, rejecting call",
                    request.txId(), serviceName);
            return RollbackResponse.failure(request.txId(),
                    "Service " + serviceName.getDisplayName() + " is overloaded");
        } catch (Exception e) {
            log.error("txId={} - Kafka rollback send failed for {}: {}",
                    request.txId(), serviceName, e.getMessage());
            return RollbackResponse.failure(request.txId(), "Kafka send failed: " + e.getMessage());
        }
    }

    @Override
    public NotifyResponse notify(String notifyUrl, NotifyRequest request) {
        // For Kafka mode, URL is ignored - determine service from context
        log.warn("txId={} - notify(url) called in Kafka mode, URL ignored: {}", request.txId(), notifyUrl);
        return NotifyResponse.failure(request.txId(), "Direct URL notify not supported in Kafka mode");
    }

    @Override
    public RollbackResponse rollback(String rollbackUrl, RollbackRequest request) {
        // For Kafka mode, URL is ignored - determine service from context
        log.warn("txId={} - rollback(url) called in Kafka mode, URL ignored: {}", request.txId(), rollbackUrl);
        return RollbackResponse.failure(request.txId(), "Direct URL rollback not supported in Kafka mode");
    }

    /**
     * Send execute command via Kafka.
     */
    private NotifyResponse executeNotify(String topic, ServiceName serviceName, NotifyRequest request) {
        log.info("txId={} - Sending execute command to Kafka topic: {}", request.txId(), topic);

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", request.orderId().toString());
        payload.put("items", request.items());
        payload.put("totalAmount", request.totalAmount());
        payload.put("creditCardNumber", request.creditCardNumber());
        payload.put("userId", request.userId());

        SagaKafkaMessage message = SagaKafkaMessage.executeCommand(
                request.txId().toString(),
                request.orderId().toString(),
                serviceName,
                payload
        );

        try {
            CompletableFuture<SendResult<String, SagaKafkaMessage>> future =
                    kafkaTemplate.send(topic, request.orderId().toString(), message);

            // Wait for send acknowledgment
            SendResult<String, SagaKafkaMessage> result = future.get(sendTimeoutMs, TimeUnit.MILLISECONDS);

            log.info("txId={} - Command sent to Kafka topic={}, partition={}, offset={}",
                    request.txId(), topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

            // In async mode, we return success for the send operation
            // The actual service response will come via the response consumer
            return NotifyResponse.success(request.txId(),
                    "Command sent to " + serviceName.getDisplayName(),
                    "kafka:" + result.getRecordMetadata().offset());

        } catch (Exception e) {
            log.error("txId={} - Failed to send command to Kafka: {}", request.txId(), e.getMessage());
            throw new RuntimeException("Kafka send failed", e);
        }
    }

    /**
     * Send rollback command via Kafka.
     */
    private RollbackResponse executeRollback(String topic, ServiceName serviceName, RollbackRequest request) {
        log.info("txId={} - Sending rollback command to Kafka topic: {}", request.txId(), topic);

        SagaKafkaMessage message = SagaKafkaMessage.rollbackCommand(
                request.txId().toString(),
                request.orderId().toString(),
                serviceName,
                request.reason()  // Use reason as the service reference context
        );

        try {
            CompletableFuture<SendResult<String, SagaKafkaMessage>> future =
                    kafkaTemplate.send(topic, request.orderId().toString(), message);

            SendResult<String, SagaKafkaMessage> result = future.get(sendTimeoutMs, TimeUnit.MILLISECONDS);

            log.info("txId={} - Rollback command sent to Kafka topic={}, partition={}, offset={}",
                    request.txId(), topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

            return RollbackResponse.success(request.txId(),
                    "Rollback command sent to " + serviceName.getDisplayName());

        } catch (Exception e) {
            log.error("txId={} - Failed to send rollback command to Kafka: {}", request.txId(), e.getMessage());
            throw new RuntimeException("Kafka send failed", e);
        }
    }

    /**
     * Get the command topic for a service.
     */
    private String getCommandTopic(ServiceName serviceName) {
        return switch (serviceName) {
            case CREDIT_CARD -> creditCardCommandsTopic;
            case INVENTORY -> inventoryCommandsTopic;
            case LOGISTICS -> logisticsCommandsTopic;
            default -> throw new IllegalArgumentException("Unknown service: " + serviceName);
        };
    }
}
