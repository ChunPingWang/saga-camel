package com.ecommerce.order.infrastructure.camel.processor;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.service.RollbackService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Processor that triggers the rollback service.
 * Extracts successful services and initiates compensation.
 */
@Component
public class RollbackProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(RollbackProcessor.class);

    private final TransactionLogPort transactionLogPort;
    private final RollbackService rollbackService;

    public RollbackProcessor(TransactionLogPort transactionLogPort, RollbackService rollbackService) {
        this.transactionLogPort = transactionLogPort;
        this.rollbackService = rollbackService;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        UUID txId = exchange.getProperty("txId", UUID.class);
        UUID orderId = exchange.getProperty("orderId", UUID.class);
        ServiceName failedService = exchange.getProperty("failedService", ServiceName.class);
        String errorMessage = exchange.getProperty("errorMessage", String.class);

        log.info("RollbackProcessor: Starting rollback for txId={}, failed service={}",
                txId, failedService);

        // Record the failed service
        if (failedService != null) {
            transactionLogPort.recordStatusWithError(txId, orderId, failedService,
                    TransactionStatus.F, errorMessage);
        }

        // Get successful services from exchange property or from database
        @SuppressWarnings("unchecked")
        List<ServiceName> successfulServices = exchange.getProperty("successfulServices", List.class);

        if (successfulServices == null || successfulServices.isEmpty()) {
            // Fall back to database lookup
            successfulServices = transactionLogPort.findSuccessfulServices(txId);
        }

        log.info("RollbackProcessor: Found {} successful services to rollback for txId={}",
                successfulServices.size(), txId);

        // Execute rollback
        rollbackService.executeRollback(txId, orderId, successfulServices);

        log.info("RollbackProcessor: Rollback completed for txId={}", txId);
    }
}
