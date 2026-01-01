package com.ecommerce.order.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for saga_config table.
 */
public interface SagaConfigRepository extends JpaRepository<SagaConfigEntity, Long> {

    /**
     * Find active configuration by type.
     */
    Optional<SagaConfigEntity> findByConfigTypeAndIsActiveTrue(String configType);

    /**
     * Find pending configuration by type.
     */
    Optional<SagaConfigEntity> findByConfigTypeAndIsPendingTrue(String configType);

    /**
     * Find all configurations by type.
     */
    List<SagaConfigEntity> findByConfigType(String configType);

    /**
     * Find all active service configurations.
     */
    @Query("SELECT c FROM SagaConfigEntity c WHERE c.configType = 'SERVICE_CONFIG' AND c.isActive = true ORDER BY c.configKey")
    List<SagaConfigEntity> findActiveServiceConfigs();

    /**
     * Find all pending service configurations.
     */
    @Query("SELECT c FROM SagaConfigEntity c WHERE c.configType = 'SERVICE_CONFIG' AND c.isPending = true ORDER BY c.configKey")
    List<SagaConfigEntity> findPendingServiceConfigs();

    /**
     * Delete all pending service configurations.
     */
    @Modifying
    @Query("DELETE FROM SagaConfigEntity c WHERE c.configType = 'SERVICE_CONFIG' AND c.isPending = true")
    void deletePendingServiceConfigs();

    /**
     * Deactivate all active service configurations.
     */
    @Modifying
    @Query("UPDATE SagaConfigEntity c SET c.isActive = false WHERE c.configType = 'SERVICE_CONFIG' AND c.isActive = true")
    void deactivateActiveServiceConfigs();

    /**
     * Activate all pending service configurations.
     */
    @Modifying
    @Query("UPDATE SagaConfigEntity c SET c.isActive = true, c.isPending = false WHERE c.configType = 'SERVICE_CONFIG' AND c.isPending = true")
    void activatePendingServiceConfigs();
}
