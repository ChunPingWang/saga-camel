package com.ecommerce.order.adapter.in.web.dto;

import com.ecommerce.order.domain.model.ServiceConfig;

import java.util.List;

/**
 * Response DTO for service configuration list.
 */
public record ServiceConfigListResponse(
        List<ServiceConfigDto> configs
) {
    /**
     * Create from domain model list.
     */
    public static ServiceConfigListResponse fromDomain(List<ServiceConfig> configs) {
        return new ServiceConfigListResponse(
                configs.stream()
                        .map(ServiceConfigDto::fromDomain)
                        .toList()
        );
    }
}
