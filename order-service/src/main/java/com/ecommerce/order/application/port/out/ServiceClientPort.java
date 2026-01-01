package com.ecommerce.order.application.port.out;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;

/**
 * Output port for downstream service communication.
 */
public interface ServiceClientPort {

    /**
     * Get the service name this client handles.
     */
    ServiceName getServiceName();

    /**
     * Call the notify endpoint of a downstream service by service name.
     */
    NotifyResponse notify(ServiceName serviceName, NotifyRequest request);

    /**
     * Call the rollback endpoint of a downstream service by service name (idempotent).
     */
    RollbackResponse rollback(ServiceName serviceName, RollbackRequest request);

    /**
     * Call the notify endpoint of the downstream service using explicit URL.
     */
    NotifyResponse notify(String notifyUrl, NotifyRequest request);

    /**
     * Call the rollback endpoint of the downstream service (idempotent) using explicit URL.
     */
    RollbackResponse rollback(String rollbackUrl, RollbackRequest request);
}
