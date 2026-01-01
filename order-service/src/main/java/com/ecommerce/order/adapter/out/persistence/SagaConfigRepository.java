package com.ecommerce.order.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
