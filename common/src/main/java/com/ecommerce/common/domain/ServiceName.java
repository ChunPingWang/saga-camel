package com.ecommerce.common.domain;

/**
 * Downstream service identifiers for saga orchestration.
 */
public enum ServiceName {

    /**
     * Credit Card / Payment processing service.
     */
    CREDIT_CARD("Credit Card Service", "http://localhost:8081"),

    /**
     * Inventory reservation service.
     */
    INVENTORY("Inventory Service", "http://localhost:8082"),

    /**
     * Logistics / Shipping scheduling service.
     */
    LOGISTICS("Logistics Service", "http://localhost:8083"),

    /**
     * Saga orchestrator marker (used for overall saga status).
     */
    SAGA("Saga Orchestrator", "http://localhost:8080");

    private final String displayName;
    private final String defaultBaseUrl;

    ServiceName(String displayName, String defaultBaseUrl) {
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    /**
     * Get the default notify endpoint URL for this service.
     */
    public String getDefaultNotifyUrl() {
        return switch (this) {
            case CREDIT_CARD -> defaultBaseUrl + "/api/v1/credit-card/notify";
            case INVENTORY -> defaultBaseUrl + "/api/v1/inventory/notify";
            case LOGISTICS -> defaultBaseUrl + "/api/v1/logistics/notify";
            case SAGA -> throw new IllegalStateException("SAGA does not have a notify URL");
        };
    }

    /**
     * Get the default rollback endpoint URL for this service.
     */
    public String getDefaultRollbackUrl() {
        return switch (this) {
            case CREDIT_CARD -> defaultBaseUrl + "/api/v1/credit-card/rollback";
            case INVENTORY -> defaultBaseUrl + "/api/v1/inventory/rollback";
            case LOGISTICS -> defaultBaseUrl + "/api/v1/logistics/rollback";
            case SAGA -> throw new IllegalStateException("SAGA does not have a rollback URL");
        };
    }

    /**
     * Check if this is a downstream service (not the orchestrator).
     */
    public boolean isDownstreamService() {
        return this != SAGA;
    }
}
