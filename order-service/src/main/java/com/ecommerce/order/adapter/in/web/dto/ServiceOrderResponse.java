package com.ecommerce.order.adapter.in.web.dto;

import com.ecommerce.common.domain.ServiceName;

import java.util.List;

/**
 * Response DTO for service order.
 */
public record ServiceOrderResponse(
        List<String> order
) {
    /**
     * Create from service name list.
     */
    public static ServiceOrderResponse fromServiceNames(List<ServiceName> serviceNames) {
        return new ServiceOrderResponse(
                serviceNames.stream()
                        .map(ServiceName::name)
                        .toList()
        );
    }
}
