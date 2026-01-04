package com.ecommerce.order.adapter.out.http;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP adapter for downstream service communication.
 * Implements ServiceClientPort using RestTemplate with Circuit Breaker protection.
 */
@Component
public class ServiceClientAdapter implements ServiceClientPort {

    private static final Logger log = LoggerFactory.getLogger(ServiceClientAdapter.class);

    private final RestTemplate restTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ServiceClientAdapter(RestTemplate restTemplate, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.restTemplate = restTemplate;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public ServiceName getServiceName() {
        // This adapter handles all services
        return null;
    }

    @Override
    public NotifyResponse notify(ServiceName serviceName, NotifyRequest request) {
        String notifyUrl = serviceName.getDefaultNotifyUrl();
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName.name());

        try {
            return circuitBreaker.executeSupplier(() -> executeNotify(notifyUrl, request));
        } catch (CallNotPermittedException e) {
            log.warn("txId={} - Circuit breaker OPEN for service {}, skipping HTTP call",
                    request.txId(), serviceName);
            return NotifyResponse.failure(request.txId(),
                    "Circuit breaker is OPEN for " + serviceName.getDisplayName());
        } catch (RestClientException e) {
            log.error("txId={} - Notify failed for {}: {}", request.txId(), notifyUrl, e.getMessage());
            return NotifyResponse.failure(request.txId(), "Service call failed: " + e.getMessage());
        }
    }

    @Override
    public RollbackResponse rollback(ServiceName serviceName, RollbackRequest request) {
        String rollbackUrl = serviceName.getDefaultRollbackUrl();
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName.name());

        try {
            return circuitBreaker.executeSupplier(() -> executeRollback(rollbackUrl, request));
        } catch (CallNotPermittedException e) {
            log.warn("txId={} - Circuit breaker OPEN for service {} rollback, skipping HTTP call",
                    request.txId(), serviceName);
            return RollbackResponse.failure(request.txId(),
                    "Circuit breaker is OPEN for " + serviceName.getDisplayName() + " rollback");
        } catch (RestClientException e) {
            log.error("txId={} - Rollback failed for {}: {}", request.txId(), rollbackUrl, e.getMessage());
            return RollbackResponse.failure(request.txId(), "Service call failed: " + e.getMessage());
        }
    }

    /**
     * Execute notify HTTP call - throws exception for Circuit Breaker to track.
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
     * Execute rollback HTTP call - throws exception for Circuit Breaker to track.
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
        log.info("txId={} - Calling notify endpoint: {}", request.txId(), notifyUrl);
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
        log.info("txId={} - Calling rollback endpoint: {}", request.txId(), rollbackUrl);
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
