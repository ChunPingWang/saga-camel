package com.ecommerce.order.adapter.out.persistence;

import com.ecommerce.order.application.port.out.OutboxPort;
import com.ecommerce.order.domain.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistence adapter for outbox pattern operations.
 * Implements the OutboxPort interface for atomic event storage.
 */
@Component
public class OutboxPersistenceAdapter implements OutboxPort {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxPersistenceAdapter(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(OutboxEventData event) {
        OutboxEventEntity entity = new OutboxEventEntity(
                event.getTxId(),
                event.getOrderId(),
                event.getEventType(),
                event.getPayload()
        );
        outboxEventRepository.save(entity);
    }

    @Override
    public Long createSagaEvent(UUID txId, UUID orderId, Order order) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new OrderPayload(order));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order", e);
        }

        OutboxEventEntity entity = new OutboxEventEntity(
                txId.toString(),
                orderId.toString(),
                "ORDER_CONFIRMED",
                payload
        );
        OutboxEventEntity saved = outboxEventRepository.save(entity);
        return saved.getId();
    }

    @Override
    public List<OutboxEvent> findUnprocessed() {
        return outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc()
                .stream()
                .map(entity -> new OutboxEvent(
                        entity.getId(),
                        UUID.fromString(entity.getTxId()),
                        UUID.fromString(entity.getOrderId()),
                        entity.getEventType(),
                        entity.getPayload()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public void markProcessed(Long eventId) {
        outboxEventRepository.findById(eventId).ifPresent(entity -> {
            entity.setProcessed(true);
            entity.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(entity);
        });
    }

    /**
     * Payload wrapper for order serialization.
     */
    private record OrderPayload(
            String orderId,
            String customerId,
            String totalAmount,
            List<ItemPayload> items
    ) {
        OrderPayload(Order order) {
            this(
                    order.getOrderId().toString(),
                    order.getCustomerId(),
                    order.getTotalAmount().toString(),
                    order.getItems().stream()
                            .map(item -> new ItemPayload(
                                    item.productId(),
                                    item.quantity(),
                                    item.price().toString()
                            ))
                            .collect(Collectors.toList())
            );
        }
    }

    private record ItemPayload(String productId, int quantity, String price) {}
}
