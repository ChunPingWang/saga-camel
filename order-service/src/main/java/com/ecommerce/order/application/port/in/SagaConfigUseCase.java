package com.ecommerce.order.application.port.in;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.domain.model.ServiceConfig;

import java.util.List;
import java.util.Map;

/**
 * Use case port for saga configuration management.
 * Provides methods for admin to manage service order and timeouts.
 */
public interface SagaConfigUseCase {

    /**
     * Get the currently active configuration.
     * @return list of active service configurations
     */
    List<ServiceConfig> getActiveConfig();

    /**
     * Get the pending configuration (not yet applied).
     * @return list of pending service configurations
     */
    List<ServiceConfig> getPendingConfig();

    /**
     * Update the pending configuration.
     * @param configs the new pending configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    void updatePendingConfig(List<ServiceConfig> configs);

    /**
     * Apply pending configuration as the new active configuration.
     * @throws IllegalStateException if no pending configuration exists
     */
    void applyPendingConfig();

    /**
     * Discard pending configuration without applying.
     */
    void discardPendingConfig();

    /**
     * Get the timeout for a specific service.
     * @param serviceName the service to get timeout for
     * @return timeout in seconds
     */
    int getServiceTimeout(ServiceName serviceName);

    /**
     * Get the configured service execution order.
     * @return list of services in execution order
     */
    List<ServiceName> getServiceOrder();

    /**
     * Get all service timeouts.
     * @return map of service name to timeout in seconds
     */
    Map<ServiceName, Integer> getTimeouts();
}
