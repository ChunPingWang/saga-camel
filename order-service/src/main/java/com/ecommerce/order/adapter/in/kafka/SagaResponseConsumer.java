package com.ecommerce.order.adapter.in.kafka;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.application.service.RollbackService;
import com.ecommerce.order.domain.model.TransactionLog;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Kafka consumer for handling Saga responses from downstream services.
 * Listens to response topics and updates saga state accordingly.
 */
@Component
@ConditionalOnProperty(name = "saga.messaging.type", havingValue = "kafka")
public class SagaResponseConsumer {

    private static final Logger log = LoggerFactory.getLogger(SagaResponseConsumer.class);

    private static final List<ServiceName> SAGA_SERVICES = List.of(
            ServiceName.CREDIT_CARD,
            ServiceName.INVENTORY,
            ServiceName.LOGISTICS
    );

    private final TransactionLogPort transactionLogPort;
    private final WebSocketPort webSocketPort;
    private final RollbackService rollbackService;

    public SagaResponseConsumer(TransactionLogPort transactionLogPort,
                                 WebSocketPort webSocketPort,
                                 RollbackService rollbackService) {
        this.transactionLogPort = transactionLogPort;
        this.webSocketPort = webSocketPort;
        this.rollbackService = rollbackService;
    }

    /**
     * Handle responses from Credit Card service.
     */
    @KafkaListener(
            topics = "${saga.kafka.topics.credit-card-responses:saga.credit-card.responses}",
            groupId = "${spring.kafka.consumer.group-id:saga-orchestrator}",
            containerFactory = "sagaKafkaListenerContainerFactory"
    )
    public void handleCreditCardResponse(ConsumerRecord<String, SagaKafkaMessage> record,
                                          Acknowledgment acknowledgment) {
        handleServiceResponse(record, ServiceName.CREDIT_CARD, acknowledgment);
    }

    /**
     * Handle responses from Inventory service.
     */
    @KafkaListener(
            topics = "${saga.kafka.topics.inventory-responses:saga.inventory.responses}",
            groupId = "${spring.kafka.consumer.group-id:saga-orchestrator}",
            containerFactory = "sagaKafkaListenerContainerFactory"
    )
    public void handleInventoryResponse(ConsumerRecord<String, SagaKafkaMessage> record,
                                          Acknowledgment acknowledgment) {
        handleServiceResponse(record, ServiceName.INVENTORY, acknowledgment);
    }

    /**
     * Handle responses from Logistics service.
     */
    @KafkaListener(
            topics = "${saga.kafka.topics.logistics-responses:saga.logistics.responses}",
            groupId = "${spring.kafka.consumer.group-id:saga-orchestrator}",
            containerFactory = "sagaKafkaListenerContainerFactory"
    )
    public void handleLogisticsResponse(ConsumerRecord<String, SagaKafkaMessage> record,
                                          Acknowledgment acknowledgment) {
        handleServiceResponse(record, ServiceName.LOGISTICS, acknowledgment);
    }

    /**
     * Common handler for all service responses.
     */
    @Transactional
    protected void handleServiceResponse(ConsumerRecord<String, SagaKafkaMessage> record,
                                          ServiceName expectedService,
                                          Acknowledgment acknowledgment) {
        SagaKafkaMessage message = record.value();
        String txId = message.getTxId();
        MDC.put("txId", txId);

        try {
            log.info("Received {} response from {} - topic={}, partition={}, offset={}",
                    message.getMessageType(),
                    message.getServiceName(),
                    record.topic(),
                    record.partition(),
                    record.offset());

            // Validate message
            if (message.getServiceName() != expectedService) {
                log.warn("Unexpected service in message: expected={}, actual={}",
                        expectedService, message.getServiceName());
            }

            if (message.isRollback()) {
                handleRollbackResponse(message);
            } else {
                handleExecuteResponse(message);
            }

            // Acknowledge the message
            acknowledgment.acknowledge();
            log.debug("Message acknowledged successfully");

        } catch (Exception e) {
            log.error("Error processing response message: {}", e.getMessage(), e);
            // Don't acknowledge - message will be redelivered
            throw e;
        } finally {
            MDC.remove("txId");
        }
    }

    /**
     * Handle execute response (success or failure).
     */
    private void handleExecuteResponse(SagaKafkaMessage message) {
        String txId = message.getTxId();
        String orderId = message.getOrderId();
        ServiceName serviceName = message.getServiceName();

        if (message.isSuccess()) {
            log.info("Service {} completed successfully for txId={}, serviceRef={}",
                    serviceName, txId, message.getServiceReference());

            // Update transaction log to SUCCESS
            TransactionLog logEntry = TransactionLog.create(
                    txId,
                    orderId,
                    serviceName,
                    TransactionStatus.SUCCESS
            );
            transactionLogPort.save(logEntry);

            // Send WebSocket update
            webSocketPort.sendSuccess(
                    UUID.fromString(txId),
                    UUID.fromString(orderId),
                    serviceName
            );

            // Check if saga is complete
            checkSagaCompletion(txId, orderId);

        } else {
            log.warn("Service {} failed for txId={}: {}",
                    serviceName, txId, message.getErrorMessage());

            // Update transaction log to FAILED with error message
            TransactionLog logEntry = TransactionLog.createWithError(
                    UUID.fromString(txId),
                    UUID.fromString(orderId),
                    serviceName,
                    TransactionStatus.FAILED,
                    message.getErrorMessage()
            );
            transactionLogPort.save(logEntry);

            // Send WebSocket update
            webSocketPort.sendFailure(
                    UUID.fromString(txId),
                    UUID.fromString(orderId),
                    serviceName,
                    message.getErrorMessage()
            );

            // Trigger rollback for completed services
            triggerRollback(txId, orderId);
        }
    }

    /**
     * Handle rollback response.
     */
    private void handleRollbackResponse(SagaKafkaMessage message) {
        String txId = message.getTxId();
        String orderId = message.getOrderId();
        ServiceName serviceName = message.getServiceName();

        if (message.isSuccess()) {
            log.info("Rollback for {} completed successfully for txId={}", serviceName, txId);

            // Update transaction log to ROLLED_BACK
            TransactionLog logEntry = TransactionLog.create(
                    txId,
                    orderId,
                    serviceName,
                    TransactionStatus.ROLLBACK
            );
            transactionLogPort.save(logEntry);

            // Send WebSocket update
            webSocketPort.sendRollbackProgress(
                    UUID.fromString(txId),
                    UUID.fromString(orderId),
                    serviceName
            );

        } else {
            log.error("Rollback for {} failed for txId={}: {}",
                    serviceName, txId, message.getErrorMessage());

            // Update transaction log to ROLLBACK_FAILED with error message
            TransactionLog logEntry = TransactionLog.createWithError(
                    UUID.fromString(txId),
                    UUID.fromString(orderId),
                    serviceName,
                    TransactionStatus.RF,
                    message.getErrorMessage()
            );
            transactionLogPort.save(logEntry);

            // Send WebSocket update
            webSocketPort.sendRollbackFailed(
                    UUID.fromString(txId),
                    UUID.fromString(orderId),
                    "Rollback failed for " + serviceName + ": " + message.getErrorMessage()
            );
        }
    }

    /**
     * Check if all saga steps are complete.
     */
    private void checkSagaCompletion(String txId, String orderId) {
        List<TransactionLog> logs = transactionLogPort.findLatestByTxId(txId);

        boolean allSuccess = logs.size() == SAGA_SERVICES.size() &&
                logs.stream().allMatch(l -> l.getStatus() == TransactionStatus.SUCCESS);

        if (allSuccess) {
            log.info("Saga completed successfully for txId={}", txId);
            webSocketPort.sendCompleted(UUID.fromString(txId), UUID.fromString(orderId));
        }
    }

    /**
     * Trigger rollback for already completed services.
     */
    private void triggerRollback(String txId, String orderId) {
        try {
            log.info("Triggering rollback for txId={}", txId);
            // Get list of successful services that need to be rolled back
            List<ServiceName> successfulServices = rollbackService.getSuccessfulServices(UUID.fromString(txId));
            if (!successfulServices.isEmpty()) {
                rollbackService.executeRollback(UUID.fromString(txId), UUID.fromString(orderId), successfulServices);
            } else {
                log.info("No successful services to rollback for txId={}", txId);
            }
        } catch (Exception e) {
            log.error("Failed to trigger rollback for txId={}: {}", txId, e.getMessage());
        }
    }
}
