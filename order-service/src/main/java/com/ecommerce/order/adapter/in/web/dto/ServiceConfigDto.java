package com.ecommerce.order.adapter.in.web.dto;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.domain.model.ServiceConfig;

/**
 * DTO for service configuration.
 */
public record ServiceConfigDto(
        String serviceName,
        int order,
        int timeoutSeconds
) {
    /**
     * Convert from domain model.
     */
    public static ServiceConfigDto fromDomain(ServiceConfig config) {
        return new ServiceConfigDto(
                config.name().name(),
                config.order(),
                config.timeoutSeconds()
        );
    }

    /**
     * Convert to domain model (as pending config).
     */
    public ServiceConfig toDomain() {
        return ServiceConfig.of(
                ServiceName.valueOf(serviceName),
                order,
                timeoutSeconds,
                false  // pending
        );
    }
}
