package com.ecommerce.order.application.service;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.application.port.in.SagaConfigUseCase;
import com.ecommerce.order.application.port.out.SagaConfigPort;
import com.ecommerce.order.domain.model.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing saga configuration.
 * Supports active/pending configuration model for safe updates.
 */
@Service
public class SagaConfigService implements SagaConfigUseCase {

    private static final Logger log = LoggerFactory.getLogger(SagaConfigService.class);
    private static final int DEFAULT_TIMEOUT = 120;

    private final SagaConfigPort sagaConfigPort;

    public SagaConfigService(SagaConfigPort sagaConfigPort) {
        this.sagaConfigPort = sagaConfigPort;
    }

    @Override
    public List<ServiceConfig> getActiveConfig() {
        return sagaConfigPort.findActiveConfigs();
    }

    @Override
    public List<ServiceConfig> getPendingConfig() {
        return sagaConfigPort.findPendingConfigs();
    }

    @Override
    public void updatePendingConfig(List<ServiceConfig> configs) {
        validateConfig(configs);
        sagaConfigPort.savePendingConfigs(configs);
        log.info("Pending configuration updated with {} services", configs.size());
    }

    @Override
    public void applyPendingConfig() {
        List<ServiceConfig> pending = sagaConfigPort.findPendingConfigs();
        if (pending.isEmpty()) {
            throw new IllegalStateException("No pending configuration to apply");
        }

        sagaConfigPort.activatePendingConfigs();
        log.info("Pending configuration applied as active");
    }

    @Override
    public void discardPendingConfig() {
        sagaConfigPort.deletePendingConfigs();
        log.info("Pending configuration discarded");
    }

    @Override
    public int getServiceTimeout(ServiceName serviceName) {
        return sagaConfigPort.findActiveConfigs().stream()
                .filter(config -> config.name() == serviceName)
                .findFirst()
                .map(ServiceConfig::timeoutSeconds)
                .orElse(DEFAULT_TIMEOUT);
    }

    @Override
    public List<ServiceName> getServiceOrder() {
        return sagaConfigPort.findActiveConfigs().stream()
                .sorted(Comparator.comparingInt(ServiceConfig::order))
                .map(ServiceConfig::name)
                .collect(Collectors.toList());
    }

    @Override
    public Map<ServiceName, Integer> getTimeouts() {
        return sagaConfigPort.findActiveConfigs().stream()
                .collect(Collectors.toMap(
                        ServiceConfig::name,
                        ServiceConfig::timeoutSeconds
                ));
    }

    private void validateConfig(List<ServiceConfig> configs) {
        // Check for duplicate orders
        Set<Integer> orders = new HashSet<>();
        for (ServiceConfig config : configs) {
            if (!orders.add(config.order())) {
                throw new IllegalArgumentException(
                        "Duplicate service order: " + config.order());
            }
        }

        // Check for positive timeouts
        for (ServiceConfig config : configs) {
            if (config.timeoutSeconds() <= 0) {
                throw new IllegalArgumentException(
                        "Invalid timeout for service " + config.name() +
                        ": timeout must be positive");
            }
        }

        // Check for duplicate services
        Set<ServiceName> services = new HashSet<>();
        for (ServiceConfig config : configs) {
            if (!services.add(config.name())) {
                throw new IllegalArgumentException(
                        "Duplicate service: " + config.name());
            }
        }
    }
}
