package com.ecommerce.order.adapter.out.http;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.function.Supplier;

/**
 * HTTP adapter for downstream service communication.
 * Implements ServiceClientPort using RestTemplate with Resilience4j protection.
 *
 * Decorator order: Bulkhead -> Retry -> CircuitBreaker -> HTTP Call
 */
@Component
@Profile("!kafka")
public class ServiceClientAdapter implements ServiceClientPort {

    private static final Logger log = LoggerFactory.getLogger(ServiceClientAdapter.class);

    private final RestTemplate restTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    public ServiceClientAdapter(
            RestTemplate restTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            BulkheadRegistry bulkheadRegistry) {
        this.restTemplate = restTemplate;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
    }

    @Override
    public ServiceName getServiceName() {
        // This adapter handles all services
        return null;
    }

    @Override
    public NotifyResponse notify(ServiceName serviceName, NotifyRequest request) {
        String notifyUrl = serviceName.getDefaultNotifyUrl();
        String instanceName = serviceName.name();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        Retry retry = retryRegistry.retry(instanceName);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(instanceName);

        Supplier<NotifyResponse> decoratedSupplier = Decorators
                .ofSupplier(() -> executeNotify(notifyUrl, request))
                .withBulkhead(bulkhead)
                .withRetry(retry)
                .withCircuitBreaker(circuitBreaker)
                .decorate();

        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException e) {
            log.warn("txId={} - Circuit breaker OPEN for service {}, skipping HTTP call",
                    request.txId(), serviceName);
            return NotifyResponse.failure(request.txId(),
                    "Circuit breaker is OPEN for " + serviceName.getDisplayName());
        } catch (BulkheadFullException e) {
            log.warn("txId={} - Bulkhead full for service {}, rejecting call",
                    request.txId(), serviceName);
            return NotifyResponse.failure(request.txId(),
                    "Service " + serviceName.getDisplayName() + " is overloaded");
        } catch (RestClientException e) {
            log.error("txId={} - Notify failed for {} after retries: {}",
                    request.txId(), notifyUrl, e.getMessage());
            return NotifyResponse.failure(request.txId(), "Service call failed: " + e.getMessage());
        }
    }

    @Override
    public RollbackResponse rollback(ServiceName serviceName, RollbackRequest request) {
        String rollbackUrl = serviceName.getDefaultRollbackUrl();
        String instanceName = serviceName.name();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        Retry retry = retryRegistry.retry(instanceName);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(instanceName);

        Supplier<RollbackResponse> decoratedSupplier = Decorators
                .ofSupplier(() -> executeRollback(rollbackUrl, request))
                .withBulkhead(bulkhead)
                .withRetry(retry)
                .withCircuitBreaker(circuitBreaker)
                .decorate();

        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException e) {
            log.warn("txId={} - Circuit breaker OPEN for service {} rollback, skipping HTTP call",
                    request.txId(), serviceName);
            return RollbackResponse.failure(request.txId(),
                    "Circuit breaker is OPEN for " + serviceName.getDisplayName() + " rollback");
        } catch (BulkheadFullException e) {
            log.warn("txId={} - Bulkhead full for service {} rollback, rejecting call",
                    request.txId(), serviceName);
            return RollbackResponse.failure(request.txId(),
                    "Service " + serviceName.getDisplayName() + " is overloaded");
        } catch (RestClientException e) {
            log.error("txId={} - Rollback failed for {} after retries: {}",
                    request.txId(), rollbackUrl, e.getMessage());
            return RollbackResponse.failure(request.txId(), "Service call failed: " + e.getMessage());
        }
    }

    /**
     * Execute notify HTTP call - throws exception for resilience decorators to handle.
     */
    private NotifyResponse executeNotify(String notifyUrl, NotifyRequest request) {
        log.info("txId={} - Calling notify endpoint: {}", request.txId(), notifyUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<NotifyRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<NotifyResponse> response = restTemplate.postForEntity(
                notifyUrl,
                entity,
                NotifyResponse.class
        );

        NotifyResponse body = response.getBody();
        if (body != null) {
            log.info("txId={} - Notify response: success={}, message={}",
                    request.txId(), body.success(), body.message());
            return body;
        }

        return NotifyResponse.failure(request.txId(), "Empty response from service");
    }

    /**
     * Execute rollback HTTP call - throws exception for resilience decorators to handle.
     */
    private RollbackResponse executeRollback(String rollbackUrl, RollbackRequest request) {
        log.info("txId={} - Calling rollback endpoint: {}", request.txId(), rollbackUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RollbackRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<RollbackResponse> response = restTemplate.postForEntity(
                rollbackUrl,
                entity,
                RollbackResponse.class
        );

        RollbackResponse body = response.getBody();
        if (body != null) {
            log.info("txId={} - Rollback response: success={}, message={}",
                    request.txId(), body.success(), body.message());
            return body;
        }

        return RollbackResponse.failure(request.txId(), "Empty response from service");
    }

    @Override
    public NotifyResponse notify(String notifyUrl, NotifyRequest request) {
        log.info("txId={} - Calling notify endpoint (direct): {}", request.txId(), notifyUrl);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<NotifyRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<NotifyResponse> response = restTemplate.postForEntity(
                    notifyUrl,
                    entity,
                    NotifyResponse.class
            );

            NotifyResponse body = response.getBody();
            if (body != null) {
                log.info("txId={} - Notify response: success={}, message={}",
                        request.txId(), body.success(), body.message());
                return body;
            }

            return NotifyResponse.failure(request.txId(), "Empty response from service");
        } catch (RestClientException e) {
            log.error("txId={} - Notify failed for {}: {}", request.txId(), notifyUrl, e.getMessage());
            return NotifyResponse.failure(request.txId(), "Service call failed: " + e.getMessage());
        }
    }

    @Override
    public RollbackResponse rollback(String rollbackUrl, RollbackRequest request) {
        log.info("txId={} - Calling rollback endpoint (direct): {}", request.txId(), rollbackUrl);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RollbackRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<RollbackResponse> response = restTemplate.postForEntity(
                    rollbackUrl,
                    entity,
                    RollbackResponse.class
            );

            RollbackResponse body = response.getBody();
            if (body != null) {
                log.info("txId={} - Rollback response: success={}, message={}",
                        request.txId(), body.success(), body.message());
                return body;
            }

            return RollbackResponse.failure(request.txId(), "Empty response from service");
        } catch (RestClientException e) {
            log.error("txId={} - Rollback failed for {}: {}", request.txId(), rollbackUrl, e.getMessage());
            return RollbackResponse.failure(request.txId(), "Service call failed: " + e.getMessage());
        }
    }
}
