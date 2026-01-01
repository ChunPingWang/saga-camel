package com.ecommerce.order.domain.model;

import com.ecommerce.common.domain.ServiceName;

import java.util.Objects;

/**
 * Service configuration value object.
 * Defines execution order and endpoints for a downstream service.
 */
public record ServiceConfig(
        int order,
        ServiceName name,
        String notifyUrl,
        String rollbackUrl
) {
    public ServiceConfig {
        if (order < 1) {
            throw new IllegalArgumentException("Order must be at least 1");
        }
        Objects.requireNonNull(name, "Service name is required");
        if (notifyUrl == null || notifyUrl.isBlank()) {
            throw new IllegalArgumentException("Notify URL is required");
        }
        if (rollbackUrl == null || rollbackUrl.isBlank()) {
            throw new IllegalArgumentException("Rollback URL is required");
        }
    }

    /**
     * Create a default configuration for the given service.
     */
    public static ServiceConfig defaultFor(ServiceName serviceName, int order) {
        return new ServiceConfig(
                order,
                serviceName,
                serviceName.getDefaultNotifyUrl(),
                serviceName.getDefaultRollbackUrl()
        );
    }
}
