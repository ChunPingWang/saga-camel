package com.ecommerce.order.adapter.out.persistence;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.application.port.out.SagaConfigPort;
import com.ecommerce.order.domain.model.ServiceConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persistence adapter for saga configuration.
 * Stores service configurations as JSON in the saga_config table.
 */
@Component
public class SagaConfigPersistenceAdapter implements SagaConfigPort {

    private static final Logger log = LoggerFactory.getLogger(SagaConfigPersistenceAdapter.class);
    private static final String CONFIG_TYPE = "SERVICE_CONFIG";

    private final SagaConfigRepository repository;
    private final ObjectMapper objectMapper;

    public SagaConfigPersistenceAdapter(SagaConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceConfig> findActiveConfigs() {
        return repository.findActiveServiceConfigs().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceConfig> findPendingConfigs() {
        return repository.findPendingServiceConfigs().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void savePendingConfigs(List<ServiceConfig> configs) {
        // Delete existing pending configs
        repository.deletePendingServiceConfigs();

        // Save new pending configs
        for (ServiceConfig config : configs) {
            SagaConfigEntity entity = toEntity(config, false, true);
            repository.save(entity);
        }
        log.info("Saved {} pending service configurations", configs.size());
    }

    @Override
    @Transactional
    public void activatePendingConfigs() {
        // Deactivate current active configs
        repository.deactivateActiveServiceConfigs();

        // Activate pending configs
        repository.activatePendingServiceConfigs();

        log.info("Activated pending service configurations");
    }

    @Override
    @Transactional
    public void deletePendingConfigs() {
        repository.deletePendingServiceConfigs();
        log.info("Deleted pending service configurations");
    }

    private ServiceConfig toDomain(SagaConfigEntity entity) {
        try {
            ConfigValue value = objectMapper.readValue(entity.getConfigValue(), ConfigValue.class);
            return ServiceConfig.of(
                    ServiceName.valueOf(entity.getConfigKey()),
                    value.order(),
                    value.timeoutSeconds(),
                    entity.getIsActive()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse config value for " + entity.getConfigKey(), e);
        }
    }

    private SagaConfigEntity toEntity(ServiceConfig config, boolean isActive, boolean isPending) {
        try {
            ConfigValue value = new ConfigValue(config.order(), config.timeoutSeconds());
            String jsonValue = objectMapper.writeValueAsString(value);
            return new SagaConfigEntity(
                    CONFIG_TYPE,
                    config.name().name(),
                    jsonValue,
                    isActive,
                    isPending
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize config value for " + config.name(), e);
        }
    }

    /**
     * Value object for storing config data as JSON.
     */
    private record ConfigValue(int order, int timeoutSeconds) {}
}
