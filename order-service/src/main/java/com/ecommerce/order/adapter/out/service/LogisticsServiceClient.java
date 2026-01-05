package com.ecommerce.order.adapter.out.service;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for Logistics Service communication.
 */
@Component
public class LogisticsServiceClient implements ServiceClientPort {

    private static final Logger log = LoggerFactory.getLogger(LogisticsServiceClient.class);

    private final RestTemplate restTemplate;

    @Value("${services.logistics.base-url:http://localhost:8083}")
    private String baseUrl;

    public LogisticsServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ServiceName getServiceName() {
        return ServiceName.LOGISTICS;
    }

    @Override
    public NotifyResponse notify(ServiceName serviceName, NotifyRequest request) {
        if (serviceName != ServiceName.LOGISTICS) {
            throw new IllegalArgumentException("This client only handles LOGISTICS service");
        }
        return notify(baseUrl + "/api/v1/logistics/notify", request);
    }

    @Override
    public RollbackResponse rollback(ServiceName serviceName, RollbackRequest request) {
        if (serviceName != ServiceName.LOGISTICS) {
            throw new IllegalArgumentException("This client only handles LOGISTICS service");
        }
        return rollback(baseUrl + "/api/v1/logistics/rollback", request);
    }

    @Override
    public NotifyResponse notify(String notifyUrl, NotifyRequest request) {
        log.info("Calling logistics notify: txId={}, url={}", request.txId(), notifyUrl);
        try {
            NotifyResponse response = restTemplate.postForObject(notifyUrl, request, NotifyResponse.class);
            log.info("Logistics notify response: txId={}, success={}", request.txId(), 
                    response != null && response.success());
            return response;
        } catch (Exception e) {
            log.error("Logistics notify failed: txId={}, error={}", request.txId(), e.getMessage(), e);
            return NotifyResponse.failure(request.txId(), "Service call failed: " + e.getMessage());
        }
    }

    @Override
    public RollbackResponse rollback(String rollbackUrl, RollbackRequest request) {
        log.info("Calling logistics rollback: txId={}, url={}", request.txId(), rollbackUrl);
        try {
            RollbackResponse response = restTemplate.postForObject(rollbackUrl, request, RollbackResponse.class);
            log.info("Logistics rollback response: txId={}, success={}", request.txId(), 
                    response != null && response.success());
            return response;
        } catch (Exception e) {
            log.error("Logistics rollback failed: txId={}, error={}", request.txId(), e.getMessage(), e);
            return RollbackResponse.failure(request.txId(), "Rollback call failed: " + e.getMessage());
        }
    }
}
