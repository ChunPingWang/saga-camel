package com.ecommerce.order.application.service;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmRequest;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmResponse;
import com.ecommerce.order.adapter.in.web.dto.TransactionStatusResponse;
import com.ecommerce.order.application.port.in.OrderConfirmUseCase;
import com.ecommerce.order.application.port.in.TransactionQueryUseCase;
import com.ecommerce.order.application.port.out.CheckerPort;
import com.ecommerce.order.application.port.out.OutboxPort;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.domain.model.TransactionLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Saga orchestration service implementing order confirmation and status query.
 */
@Service
public class OrderSagaService implements OrderConfirmUseCase, TransactionQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private static final List<ServiceName> SAGA_SERVICES = List.of(
            ServiceName.CREDIT_CARD,
            ServiceName.INVENTORY,
            ServiceName.LOGISTICS
    );

    // Default timeout thresholds in seconds (from PRD Section 8.4)
    private static final Map<ServiceName, Integer> DEFAULT_TIMEOUTS = Map.of(
            ServiceName.CREDIT_CARD, 30,
            ServiceName.INVENTORY, 60,
            ServiceName.LOGISTICS, 120
    );

    private final TransactionLogPort transactionLogPort;
    private final OutboxPort outboxPort;
    private final CheckerPort checkerPort;
    private final ObjectMapper objectMapper;

    public OrderSagaService(TransactionLogPort transactionLogPort,
                            OutboxPort outboxPort,
                            CheckerPort checkerPort) {
        this.transactionLogPort = transactionLogPort;
        this.outboxPort = outboxPort;
        this.checkerPort = checkerPort;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional
    public OrderConfirmResponse confirmOrder(OrderConfirmRequest request) {
        String txId = UUID.randomUUID().toString();
        MDC.put("txId", txId);

        try {
            log.info("Confirming order orderId={}, items={}", request.orderId(), request.items().size());

            // Create initial transaction log entries for all services (status: UNKNOWN)
            for (ServiceName serviceName : SAGA_SERVICES) {
                TransactionLog logEntry = TransactionLog.create(
                        txId,
                        request.orderId(),
                        serviceName,
                        TransactionStatus.UNKNOWN
                );
                transactionLogPort.save(logEntry);
                log.debug("Created initial log entry for service={}", serviceName);
            }

            // Create outbox event for Camel to pick up
            Map<String, Object> payload = buildPayload(request);
            String payloadJson = serializePayload(payload);

            OutboxPort.OutboxEventData outboxEvent = new OutboxPort.OutboxEventData(
                    txId,
                    request.orderId(),
                    "ORDER_CONFIRMED",
                    payloadJson
            );
            outboxPort.save(outboxEvent);

            // Start checker thread to monitor for timeout/failure (T106)
            UUID txUuid = UUID.fromString(txId);
            UUID orderUuid = UUID.fromString(request.orderId());
            checkerPort.startCheckerThread(txUuid, orderUuid, DEFAULT_TIMEOUTS);

            log.info("Order confirmation initiated successfully txId={}", txId);
            return new OrderConfirmResponse(txId, "PROCESSING");

        } finally {
            MDC.remove("txId");
        }
    }

    @Override
    public Optional<TransactionStatusResponse> getTransactionStatus(String txId) {
        MDC.put("txId", txId);
        try {
            List<TransactionLog> logs = transactionLogPort.findLatestByTxId(txId);

            if (logs.isEmpty()) {
                log.debug("No transaction logs found for txId={}", txId);
                return Optional.empty();
            }

            String orderId = logs.get(0).getOrderId().toString();
            String overallStatus = calculateOverallStatus(logs);

            List<TransactionStatusResponse.ServiceStatusDto> serviceStatuses = logs.stream()
                    .map(log -> new TransactionStatusResponse.ServiceStatusDto(
                            log.getServiceName().name(),
                            log.getStatus().getCode(),
                            log.getCreatedAt() != null ?
                                log.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null
                    ))
                    .toList();

            return Optional.of(new TransactionStatusResponse(txId, orderId, overallStatus, serviceStatuses));

        } finally {
            MDC.remove("txId");
        }
    }

    private String calculateOverallStatus(List<TransactionLog> logs) {
        boolean anyUnknown = false;
        boolean anyFailed = false;
        boolean anyRollback = false;
        boolean allSuccess = true;
        boolean allRollbackComplete = true;

        for (TransactionLog log : logs) {
            TransactionStatus status = log.getStatus();

            switch (status) {
                case U -> {
                    anyUnknown = true;
                    allSuccess = false;
                    allRollbackComplete = false;
                }
                case S -> allRollbackComplete = false;
                case F -> {
                    anyFailed = true;
                    allSuccess = false;
                    allRollbackComplete = false;
                }
                case R -> {
                    anyRollback = true;
                    allSuccess = false;
                }
                case D -> allSuccess = false;
                case RF -> {
                    anyFailed = true;
                    allSuccess = false;
                    allRollbackComplete = false;
                }
            }
        }

        // Determine overall status
        if (anyRollback) {
            return "ROLLING_BACK";
        }
        if (anyFailed) {
            return "FAILED";
        }
        if (anyUnknown) {
            return "PROCESSING";
        }
        if (allSuccess) {
            return "COMPLETED";
        }
        if (allRollbackComplete) {
            return "ROLLED_BACK";
        }

        return "PROCESSING";
    }

    private Map<String, Object> buildPayload(OrderConfirmRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", request.orderId());
        payload.put("userId", request.userId());
        payload.put("items", request.items().stream()
                .map(item -> Map.of(
                        "sku", item.sku(),
                        "quantity", item.quantity(),
                        "unitPrice", item.unitPrice()
                ))
                .toList());
        payload.put("totalAmount", request.totalAmount());
        payload.put("creditCardNumber", request.creditCardNumber());
        return payload;
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload", e);
            throw new RuntimeException("Failed to serialize order payload", e);
        }
    }
}
