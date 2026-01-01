package com.ecommerce.common.exception;

import com.ecommerce.common.domain.ServiceName;

import java.util.UUID;

/**
 * Exception thrown when a downstream service call fails.
 */
public class ServiceException extends RuntimeException {

    private final UUID txId;
    private final ServiceName serviceName;
    private final String errorCode;

    public ServiceException(UUID txId, ServiceName serviceName, String message) {
        super(message);
        this.txId = txId;
        this.serviceName = serviceName;
        this.errorCode = "SERVICE_ERROR";
    }

    public ServiceException(UUID txId, ServiceName serviceName, String message, Throwable cause) {
        super(message, cause);
        this.txId = txId;
        this.serviceName = serviceName;
        this.errorCode = "SERVICE_ERROR";
    }

    public ServiceException(UUID txId, ServiceName serviceName, String message, String errorCode) {
        super(message);
        this.txId = txId;
        this.serviceName = serviceName;
        this.errorCode = errorCode;
    }

    public UUID getTxId() {
        return txId;
    }

    public ServiceName getServiceName() {
        return serviceName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return String.format("ServiceException[txId=%s, service=%s, errorCode=%s, message=%s]",
                txId, serviceName, errorCode, getMessage());
    }
}
