package com.ecommerce.order.infrastructure.poller;

import com.ecommerce.order.application.port.out.OutboxPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPoller Unit Tests")
class OutboxPollerTest {

    @Mock
    private OutboxPort outboxPort;

    @Mock
    private ProducerTemplate producerTemplate;

    private ObjectMapper objectMapper;
    private OutboxPoller outboxPoller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        outboxPoller = new OutboxPoller(outboxPort, producerTemplate, objectMapper);
    }

    @Nested
    @DisplayName("Poll Outbox Tests")
    class PollOutboxTests {

        @Test
        @DisplayName("Should process unprocessed events")
        void shouldProcessUnprocessedEvents() throws Exception {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            OutboxPort.OutboxEvent event = new OutboxPort.OutboxEvent(
                    1L,
                    txId,
                    orderId,
                    "ORDER_CREATED",
                    "{\"key\": \"value\"}"
            );
            when(outboxPort.findUnprocessed()).thenReturn(List.of(event));

            // When
            outboxPoller.pollOutbox();

            // Then
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(producerTemplate).sendBody(eq("direct:order-saga"), messageCaptor.capture());
            verify(outboxPort).markProcessed(1L);

            String sentMessage = messageCaptor.getValue();
            assertThat(sentMessage).contains(txId.toString());
            assertThat(sentMessage).contains(orderId.toString());
        }

        @Test
        @DisplayName("Should handle empty outbox")
        void shouldHandleEmptyOutbox() {
            // Given
            when(outboxPort.findUnprocessed()).thenReturn(List.of());

            // When
            outboxPoller.pollOutbox();

            // Then
            verify(producerTemplate, never()).sendBody(anyString(), anyString());
            verify(outboxPort, never()).markProcessed(anyLong());
        }

        @Test
        @DisplayName("Should process multiple events")
        void shouldProcessMultipleEvents() throws Exception {
            // Given
            OutboxPort.OutboxEvent event1 = new OutboxPort.OutboxEvent(
                    1L,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "ORDER_CREATED",
                    "{}"
            );
            OutboxPort.OutboxEvent event2 = new OutboxPort.OutboxEvent(
                    2L,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "ORDER_CREATED",
                    "{}"
            );
            when(outboxPort.findUnprocessed()).thenReturn(List.of(event1, event2));

            // When
            outboxPoller.pollOutbox();

            // Then
            verify(producerTemplate, times(2)).sendBody(eq("direct:order-saga"), anyString());
            verify(outboxPort).markProcessed(1L);
            verify(outboxPort).markProcessed(2L);
        }

        @Test
        @DisplayName("Should continue processing other events when one fails")
        void shouldContinueProcessingWhenOneFails() throws Exception {
            // Given
            OutboxPort.OutboxEvent event1 = new OutboxPort.OutboxEvent(
                    1L,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "ORDER_CREATED",
                    "{}"
            );
            OutboxPort.OutboxEvent event2 = new OutboxPort.OutboxEvent(
                    2L,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "ORDER_CREATED",
                    "{}"
            );
            when(outboxPort.findUnprocessed()).thenReturn(List.of(event1, event2));
            doThrow(new RuntimeException("Simulated failure"))
                    .doNothing()
                    .when(producerTemplate).sendBody(eq("direct:order-saga"), anyString());

            // When
            outboxPoller.pollOutbox();

            // Then
            verify(producerTemplate, times(2)).sendBody(eq("direct:order-saga"), anyString());
            // First event fails, so not marked as processed
            verify(outboxPort, never()).markProcessed(1L);
            // Second event succeeds
            verify(outboxPort).markProcessed(2L);
        }

        @Test
        @DisplayName("Should handle invalid JSON payload gracefully")
        void shouldHandleInvalidJsonPayload() throws Exception {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            OutboxPort.OutboxEvent event = new OutboxPort.OutboxEvent(
                    1L,
                    txId,
                    orderId,
                    "ORDER_CREATED",
                    "invalid json"
            );
            when(outboxPort.findUnprocessed()).thenReturn(List.of(event));

            // When
            outboxPoller.pollOutbox();

            // Then
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(producerTemplate).sendBody(eq("direct:order-saga"), messageCaptor.capture());
            verify(outboxPort).markProcessed(1L);

            String sentMessage = messageCaptor.getValue();
            assertThat(sentMessage).contains("payload");
            assertThat(sentMessage).contains("invalid json");
        }
    }
}
