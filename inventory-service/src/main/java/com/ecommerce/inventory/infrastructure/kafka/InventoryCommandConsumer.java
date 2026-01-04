package com.ecommerce.inventory.infrastructure.kafka;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.common.kafka.SagaMessage;
import com.ecommerce.inventory.application.port.in.ReserveInventoryUseCase;
import com.ecommerce.inventory.application.port.in.RollbackReservationUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for Inventory service commands.
 */
@Component
@ConditionalOnProperty(name = "saga.kafka.enabled", havingValue = "true")
public class InventoryCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandConsumer.class);

    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final RollbackReservationUseCase rollbackReservationUseCase;
    private final KafkaTemplate<String, SagaMessage> kafkaTemplate;

    @Value("${saga.kafka.topics.inventory-responses:saga.inventory.responses}")
    private String responsesTopic;

    public InventoryCommandConsumer(ReserveInventoryUseCase reserveInventoryUseCase,
                                     RollbackReservationUseCase rollbackReservationUseCase,
                                     KafkaTemplate<String, SagaMessage> kafkaTemplate) {
        this.reserveInventoryUseCase = reserveInventoryUseCase;
        this.rollbackReservationUseCase = rollbackReservationUseCase;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "${saga.kafka.topics.inventory-commands:saga.inventory.commands}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service}",
            containerFactory = "sagaKafkaListenerContainerFactory"
    )
    public void handleCommand(ConsumerRecord<String, SagaMessage> record, Acknowledgment acknowledgment) {
        SagaMessage command = record.value();
        String txId = command.getTxId();
        MDC.put("txId", txId);

        long startTime = System.currentTimeMillis();

        try {
            log.info("Received command: type={}, txId={}, orderId={}",
                    command.getMessageType(), txId, command.getOrderId());

            SagaMessage response;
            if (command.isRollback()) {
                response = processRollbackCommand(command);
            } else {
                response = processExecuteCommand(command);
            }

            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            sendResponse(response);
            acknowledgment.acknowledge();
            log.info("Command processed successfully in {}ms", response.getProcessingTimeMs());

        } catch (Exception e) {
            log.error("Error processing command: {}", e.getMessage(), e);
            SagaMessage errorResponse = SagaMessage.failureResponse(
                    txId,
                    command.getOrderId(),
                    ServiceName.INVENTORY,
                    "Processing error: " + e.getMessage(),
                    "INTERNAL_ERROR",
                    command.isRollback()
            );
            errorResponse.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            sendResponse(errorResponse);
            acknowledgment.acknowledge();
        } finally {
            MDC.remove("txId");
        }
    }

    private SagaMessage processExecuteCommand(SagaMessage command) {
        Map<String, Object> payload = command.getPayload();

        NotifyRequest request = NotifyRequest.of(
                UUID.fromString(command.getTxId()),
                UUID.fromString(command.getOrderId()),
                payload
        );

        NotifyResponse response = reserveInventoryUseCase.reserveInventory(request);

        if (response.success()) {
            return SagaMessage.successResponse(
                    command.getTxId(),
                    command.getOrderId(),
                    ServiceName.INVENTORY,
                    response.serviceReference(),
                    response.message(),
                    false
            );
        } else {
            return SagaMessage.failureResponse(
                    command.getTxId(),
                    command.getOrderId(),
                    ServiceName.INVENTORY,
                    response.message(),
                    "RESERVATION_FAILED",
                    false
            );
        }
    }

    private SagaMessage processRollbackCommand(SagaMessage command) {
        RollbackRequest request = RollbackRequest.of(
                UUID.fromString(command.getTxId()),
                UUID.fromString(command.getOrderId()),
                command.getServiceReference()
        );

        RollbackResponse response = rollbackReservationUseCase.releaseInventory(request);

        if (response.success()) {
            return SagaMessage.successResponse(
                    command.getTxId(),
                    command.getOrderId(),
                    ServiceName.INVENTORY,
                    null,
                    response.message(),
                    true
            );
        } else {
            return SagaMessage.failureResponse(
                    command.getTxId(),
                    command.getOrderId(),
                    ServiceName.INVENTORY,
                    response.message(),
                    "ROLLBACK_FAILED",
                    true
            );
        }
    }

    private void sendResponse(SagaMessage response) {
        try {
            kafkaTemplate.send(responsesTopic, response.getOrderId(), response)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send response: {}", ex.getMessage());
                        } else {
                            log.debug("Response sent to topic={}, partition={}, offset={}",
                                    responsesTopic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Error sending response: {}", e.getMessage(), e);
        }
    }
}
