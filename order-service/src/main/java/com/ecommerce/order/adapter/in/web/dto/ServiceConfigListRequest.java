package com.ecommerce.order.adapter.in.web.dto;

import java.util.List;

/**
 * Request DTO for updating service configuration list.
 */
public record ServiceConfigListRequest(
        List<ServiceConfigDto> configs
) {
}
