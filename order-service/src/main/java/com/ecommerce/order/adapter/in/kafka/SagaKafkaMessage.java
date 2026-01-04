package com.ecommerce.order.adapter.in.kafka;

import com.ecommerce.common.domain.ServiceName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

/**
 * Kafka message format for Saga commands and responses.
 * Used for both sending commands to downstream services and receiving responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SagaKafkaMessage {

    public enum MessageType {
        EXECUTE_COMMAND,
        ROLLBACK_COMMAND,
        EXECUTE_SUCCESS,
        EXECUTE_FAILURE,
        ROLLBACK_SUCCESS,
        ROLLBACK_FAILURE
    }

    private String txId;
    private String orderId;
    private ServiceName serviceName;
    private MessageType messageType;
    private Map<String, Object> payload;
    private String serviceReference;
    private String errorMessage;
    private String errorCode;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "UTC")
    private Instant timestamp;

    private String correlationId;
    private Integer retryCount;
    private Long processingTimeMs;

    public SagaKafkaMessage() {
        this.timestamp = Instant.now();
        this.retryCount = 0;
    }

    /**
     * Create an execute command message.
     */
    public static SagaKafkaMessage executeCommand(String txId, String orderId, ServiceName serviceName,
                                                   Map<String, Object> payload) {
        SagaKafkaMessage msg = new SagaKafkaMessage();
        msg.txId = txId;
        msg.orderId = orderId;
        msg.serviceName = serviceName;
        msg.messageType = MessageType.EXECUTE_COMMAND;
        msg.payload = payload;
        msg.correlationId = txId;
        return msg;
    }

    /**
     * Create a rollback command message.
     */
    public static SagaKafkaMessage rollbackCommand(String txId, String orderId, ServiceName serviceName,
                                                    String serviceReference) {
        SagaKafkaMessage msg = new SagaKafkaMessage();
        msg.txId = txId;
        msg.orderId = orderId;
        msg.serviceName = serviceName;
        msg.messageType = MessageType.ROLLBACK_COMMAND;
        msg.serviceReference = serviceReference;
        msg.correlationId = txId;
        return msg;
    }

    /**
     * Create a success response message.
     */
    public static SagaKafkaMessage successResponse(String txId, String orderId, ServiceName serviceName,
                                                    String serviceReference, boolean isRollback) {
        SagaKafkaMessage msg = new SagaKafkaMessage();
        msg.txId = txId;
        msg.orderId = orderId;
        msg.serviceName = serviceName;
        msg.messageType = isRollback ? MessageType.ROLLBACK_SUCCESS : MessageType.EXECUTE_SUCCESS;
        msg.serviceReference = serviceReference;
        msg.correlationId = txId;
        return msg;
    }

    /**
     * Create a failure response message.
     */
    public static SagaKafkaMessage failureResponse(String txId, String orderId, ServiceName serviceName,
                                                    String errorMessage, String errorCode, boolean isRollback) {
        SagaKafkaMessage msg = new SagaKafkaMessage();
        msg.txId = txId;
        msg.orderId = orderId;
        msg.serviceName = serviceName;
        msg.messageType = isRollback ? MessageType.ROLLBACK_FAILURE : MessageType.EXECUTE_FAILURE;
        msg.errorMessage = errorMessage;
        msg.errorCode = errorCode;
        msg.correlationId = txId;
        return msg;
    }

    // Convenience methods

    public boolean isCommand() {
        return messageType == MessageType.EXECUTE_COMMAND || messageType == MessageType.ROLLBACK_COMMAND;
    }

    public boolean isResponse() {
        return !isCommand();
    }

    public boolean isSuccess() {
        return messageType == MessageType.EXECUTE_SUCCESS || messageType == MessageType.ROLLBACK_SUCCESS;
    }

    public boolean isFailure() {
        return messageType == MessageType.EXECUTE_FAILURE || messageType == MessageType.ROLLBACK_FAILURE;
    }

    public boolean isRollback() {
        return messageType == MessageType.ROLLBACK_COMMAND ||
               messageType == MessageType.ROLLBACK_SUCCESS ||
               messageType == MessageType.ROLLBACK_FAILURE;
    }

    // Getters and Setters

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public ServiceName getServiceName() {
        return serviceName;
    }

    public void setServiceName(ServiceName serviceName) {
        this.serviceName = serviceName;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getServiceReference() {
        return serviceReference;
    }

    public void setServiceReference(String serviceReference) {
        this.serviceReference = serviceReference;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    @Override
    public String toString() {
        return "SagaKafkaMessage{" +
                "txId='" + txId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", serviceName=" + serviceName +
                ", messageType=" + messageType +
                ", timestamp=" + timestamp +
                '}';
    }
}
