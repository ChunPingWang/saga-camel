package com.ecommerce.order.adapter.in.web;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.adapter.in.web.dto.*;
import com.ecommerce.order.application.service.SagaConfigService;
import com.ecommerce.order.domain.model.ServiceConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for saga configuration administration.
 * Provides endpoints for managing service order and timeouts.
 */
@RestController
@RequestMapping("/api/v1/admin/config")
@Tag(name = "Admin Configuration", description = "Saga configuration management APIs")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final SagaConfigService sagaConfigService;

    public AdminController(SagaConfigService sagaConfigService) {
        this.sagaConfigService = sagaConfigService;
    }

    @GetMapping("/active")
    @Operation(summary = "Get active configuration",
               description = "Returns the currently active saga configuration")
    @ApiResponse(responseCode = "200", description = "Active configuration returned")
    public ResponseEntity<ServiceConfigListResponse> getActiveConfig() {
        log.debug("Fetching active configuration");
        List<ServiceConfig> configs = sagaConfigService.getActiveConfig();
        return ResponseEntity.ok(ServiceConfigListResponse.fromDomain(configs));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending configuration",
               description = "Returns the pending saga configuration (not yet applied)")
    @ApiResponse(responseCode = "200", description = "Pending configuration returned")
    public ResponseEntity<ServiceConfigListResponse> getPendingConfig() {
        log.debug("Fetching pending configuration");
        List<ServiceConfig> configs = sagaConfigService.getPendingConfig();
        return ResponseEntity.ok(ServiceConfigListResponse.fromDomain(configs));
    }

    @PutMapping("/pending")
    @Operation(summary = "Update pending configuration",
               description = "Updates the pending configuration without applying it")
    @ApiResponse(responseCode = "200", description = "Pending configuration updated")
    @ApiResponse(responseCode = "400", description = "Invalid configuration")
    public ResponseEntity<MessageResponse> updatePendingConfig(
            @RequestBody ServiceConfigListRequest request) {
        log.info("Updating pending configuration with {} services", request.configs().size());
        try {
            List<ServiceConfig> configs = request.configs().stream()
                    .map(ServiceConfigDto::toDomain)
                    .toList();
            sagaConfigService.updatePendingConfig(configs);
            return ResponseEntity.ok(MessageResponse.of("Pending configuration updated"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid configuration: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(MessageResponse.of(e.getMessage()));
        }
    }

    @PostMapping("/apply")
    @Operation(summary = "Apply pending configuration",
               description = "Applies the pending configuration as the new active configuration")
    @ApiResponse(responseCode = "200", description = "Configuration applied")
    @ApiResponse(responseCode = "400", description = "No pending configuration exists")
    public ResponseEntity<MessageResponse> applyPendingConfig() {
        log.info("Applying pending configuration");
        try {
            sagaConfigService.applyPendingConfig();
            return ResponseEntity.ok(MessageResponse.of("Configuration applied"));
        } catch (IllegalStateException e) {
            log.warn("Cannot apply configuration: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(MessageResponse.of(e.getMessage()));
        }
    }

    @DeleteMapping("/pending")
    @Operation(summary = "Discard pending configuration",
               description = "Discards the pending configuration without applying it")
    @ApiResponse(responseCode = "200", description = "Pending configuration discarded")
    public ResponseEntity<MessageResponse> discardPendingConfig() {
        log.info("Discarding pending configuration");
        sagaConfigService.discardPendingConfig();
        return ResponseEntity.ok(MessageResponse.of("Pending configuration discarded"));
    }

    @GetMapping("/timeouts")
    @Operation(summary = "Get service timeouts",
               description = "Returns a map of service names to their timeout values")
    @ApiResponse(responseCode = "200", description = "Timeouts returned")
    public ResponseEntity<Map<ServiceName, Integer>> getTimeouts() {
        log.debug("Fetching service timeouts");
        return ResponseEntity.ok(sagaConfigService.getTimeouts());
    }

    @GetMapping("/order")
    @Operation(summary = "Get service order",
               description = "Returns the order in which services are called during saga execution")
    @ApiResponse(responseCode = "200", description = "Service order returned")
    public ResponseEntity<ServiceOrderResponse> getServiceOrder() {
        log.debug("Fetching service order");
        List<ServiceName> order = sagaConfigService.getServiceOrder();
        return ResponseEntity.ok(ServiceOrderResponse.fromServiceNames(order));
    }
}
