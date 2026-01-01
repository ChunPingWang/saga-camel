package com.ecommerce.order.application.port.out;

import com.ecommerce.order.domain.model.ServiceConfig;

import java.util.List;

/**
 * Port for saga configuration persistence operations.
 */
public interface SagaConfigPort {

    /**
     * Find all active service configurations.
     * @return list of active configurations sorted by order
     */
    List<ServiceConfig> findActiveConfigs();

    /**
     * Find all pending service configurations.
     * @return list of pending configurations sorted by order
     */
    List<ServiceConfig> findPendingConfigs();

    /**
     * Save pending configurations (replacing any existing pending configs).
     * @param configs the configurations to save as pending
     */
    void savePendingConfigs(List<ServiceConfig> configs);

    /**
     * Activate pending configurations by marking them as active
     * and deactivating current active configs.
     */
    void activatePendingConfigs();

    /**
     * Delete all pending configurations.
     */
    void deletePendingConfigs();
}
