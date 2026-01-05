package com.ecommerce.creditcard.infrastructure.kafka;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.common.kafka.SagaMessage;
import com.ecommerce.creditcard.application.port.in.ProcessPaymentUseCase;
import com.ecommerce.creditcard.application.port.in.RollbackPaymentUseCase;
import com.ecommerce.creditcard.domain.model.Payment;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for Credit Card service commands.
 * Processes payment commands and sends responses.
 */
@Component
@ConditionalOnProperty(name = "saga.kafka.enabled", havingValue = "true")
public class CreditCardCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(CreditCardCommandConsumer.class);

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final RollbackPaymentUseCase rollbackPaymentUseCase;
    private final KafkaTemplate<String, SagaMessage> kafkaTemplate;

    @Value("${saga.kafka.topics.credit-card-responses:saga.credit-card.responses}")
    private String responsesTopic;

    public CreditCardCommandConsumer(ProcessPaymentUseCase processPaymentUseCase,
                                      RollbackPaymentUseCase rollbackPaymentUseCase,
                                      KafkaTemplate<String, SagaMessage> kafkaTemplate) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.rollbackPaymentUseCase = rollbackPaymentUseCase;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "${saga.kafka.topics.credit-card-commands:saga.credit-card.commands}",
            groupId = "${spring.kafka.consumer.group-id:credit-card-service}",
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

            // Set processing time
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            // Send response
            sendResponse(response);

            // Acknowledge the message
            acknowledgment.acknowledge();
            log.info("Command processed successfully in {}ms", response.getProcessingTimeMs());

        } catch (Exception e) {
            log.error("Error processing command: {}", e.getMessage(), e);
            // Send error response
            SagaMessage errorResponse = SagaMessage.failureResponse(
                    txId,
                    command.getOrderId(),
                    ServiceName.CREDIT_CARD,
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

        // Build NotifyRequest from payload
        NotifyRequest request = NotifyRequest.of(
                UUID.fromString(command.getTxId()),
                UUID.fromString(command.getOrderId()),
                payload
        );

        // Process payment
        Payment payment = processPaymentUseCase.processPayment(request);

        // Build response message
        if (payment.isApproved()) {
            return SagaMessage.successResponse(
                    command.getTxId(),
                    command.getOrderId(),
                    ServiceName.CREDIT_CARD,
                    payment.getReferenceNumber(),
                    "Payment approved",
                    false
            );
        } else {
            return SagaMessage.failureResponse(
                    command.getTxId(),
                    command.getOrderId(),
                    ServiceName.CREDIT_CARD,
                    "Payment declined: " + payment.getStatus(),
                    "PAYMENT_FAILED",
                    false
            );
        }
    }

    private SagaMessage processRollbackCommand(SagaMessage command) {
        // Build RollbackRequest
        RollbackRequest request = RollbackRequest.of(
                UUID.fromString(command.getTxId()),
                UUID.fromString(command.getOrderId()),
                command.getServiceReference()
        );

        // Process rollback
        RollbackResponse response = rollbackPaymentUseCase.rollbackPayment(request);

        // Build response message
        if (response.success()) {
            return SagaMessage.successResponse(
                    command.getTxId(),
                    command.getOrderId(),
                    ServiceName.CREDIT_CARD,
                    null,
                    response.message(),
                    true
            );
        } else {
            return SagaMessage.failureResponse(
                    command.getTxId(),
                    command.getOrderId(),
                    ServiceName.CREDIT_CARD,
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
