package com.ecommerce.common.domain;

/**
 * Transaction status codes for saga state tracking.
 * <p>
 * Status transitions follow Event Sourcing pattern - states are appended, never updated.
 */
public enum TransactionStatus {

    /**
     * Uncommitted - Service call initiated, awaiting response.
     */
    U("Uncommitted"),

    /**
     * Success - Service responded successfully.
     */
    S("Success"),

    /**
     * Failed - Service responded with error.
     */
    F("Failed"),

    /**
     * Rolled back - Compensation executed successfully.
     */
    R("Rolled back"),

    /**
     * Done - Entire rollback flow completed.
     */
    D("Done"),

    /**
     * Rollback Failed - Compensation failed after max retries.
     */
    RF("Rollback Failed");

    private final String description;

    TransactionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get status code (single or double character representation).
     */
    public String getCode() {
        return this.name();
    }

    /**
     * Check if this is a terminal state (no further transitions allowed).
     */
    public boolean isTerminal() {
        return this == D || this == RF;
    }

    /**
     * Check if this status indicates a successful operation.
     */
    public boolean isSuccess() {
        return this == S || this == D;
    }

    /**
     * Check if this status indicates a failure.
     */
    public boolean isFailure() {
        return this == F || this == RF;
    }

    /**
     * Parse status from code string.
     */
    public static TransactionStatus fromCode(String code) {
        return valueOf(code);
    }

    /**
     * Unknown/uncommitted status constant for convenience.
     */
    public static final TransactionStatus UNKNOWN = U;
    public static final TransactionStatus SUCCESS = S;
    public static final TransactionStatus FAILED = F;
    public static final TransactionStatus ROLLBACK = R;
}
