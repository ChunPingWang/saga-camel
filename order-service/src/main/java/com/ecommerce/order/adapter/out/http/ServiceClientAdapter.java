package com.ecommerce.order.adapter.out.http;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.application.port.out.ServiceClientPort;
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
 * Implements ServiceClientPort using RestTemplate.
 */
@Component
public class ServiceClientAdapter implements ServiceClientPort {

    private static final Logger log = LoggerFactory.getLogger(ServiceClientAdapter.class);

    private final RestTemplate restTemplate;

    public ServiceClientAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ServiceName getServiceName() {
        // This adapter handles all services
        return null;
    }

    @Override
    public NotifyResponse notify(ServiceName serviceName, NotifyRequest request) {
        String notifyUrl = serviceName.getDefaultNotifyUrl();
        return notify(notifyUrl, request);
    }

    @Override
    public RollbackResponse rollback(ServiceName serviceName, RollbackRequest request) {
        String rollbackUrl = serviceName.getDefaultRollbackUrl();
        return rollback(rollbackUrl, request);
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
