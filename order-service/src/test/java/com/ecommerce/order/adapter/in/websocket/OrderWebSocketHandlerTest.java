package com.ecommerce.order.adapter.in.websocket;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderWebSocketHandler Tests")
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class OrderWebSocketHandlerTest {

    @Mock
    private WebSocketSession session;

    private OrderWebSocketHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new OrderWebSocketHandler(objectMapper);
    }

    @Nested
    @DisplayName("Connection Management")
    class ConnectionManagement {

        @Test
        @DisplayName("should register session on connection established")
        void shouldRegisterSessionOnConnect() throws Exception {
            // Given
            String txId = "tx-123";
            when(session.isOpen()).thenReturn(true);
            when(session.getId()).thenReturn("session-1");
            when(session.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));

            // When
            handler.afterConnectionEstablished(session);

            // Then
            assertThat(handler.hasSession(txId)).isTrue();
        }

        @Test
        @DisplayName("should remove session on connection closed")
        void shouldRemoveSessionOnClose() throws Exception {
            // Given
            String txId = "tx-123";
            when(session.isOpen()).thenReturn(true);
            when(session.getId()).thenReturn("session-1");
            when(session.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));

            handler.afterConnectionEstablished(session);

            // When
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);

            // Then
            assertThat(handler.hasSession(txId)).isFalse();
        }

        @Test
        @DisplayName("should handle multiple sessions for same txId")
        void shouldHandleMultipleSessionsForSameTxId() throws Exception {
            // Given
            String txId = "tx-123";
            WebSocketSession session2 = mock(WebSocketSession.class);

            when(session.isOpen()).thenReturn(true);
            when(session.getId()).thenReturn("session-1");
            when(session.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));

            when(session2.isOpen()).thenReturn(true);
            when(session2.getId()).thenReturn("session-2");
            when(session2.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));

            // When
            handler.afterConnectionEstablished(session);
            handler.afterConnectionEstablished(session2);

            // Then
            assertThat(handler.getSessionCount(txId)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Send Notification")
    class SendNotification {

        @Test
        @DisplayName("should send message to all sessions for txId")
        void shouldSendMessageToAllSessions() throws Exception {
            // Given
            String txId = "tx-123";
            WebSocketSession session2 = mock(WebSocketSession.class);

            when(session.isOpen()).thenReturn(true);
            when(session.getId()).thenReturn("session-1");
            when(session.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));

            when(session2.isOpen()).thenReturn(true);
            when(session2.getId()).thenReturn("session-2");
            when(session2.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));

            handler.afterConnectionEstablished(session);
            handler.afterConnectionEstablished(session2);

            WebSocketMessage message = new WebSocketMessage(
                    txId,
                    ServiceName.CREDIT_CARD.name(),
                    TransactionStatus.SUCCESS.getCode(),
                    "Payment processed"
            );

            // When
            handler.sendNotification(txId, message);

            // Then
            verify(session).sendMessage(any(TextMessage.class));
            verify(session2).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("should send correctly formatted JSON message")
        void shouldSendCorrectlyFormattedJsonMessage() throws Exception {
            // Given
            String txId = "tx-123";
            when(session.isOpen()).thenReturn(true);
            when(session.getId()).thenReturn("session-1");
            when(session.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));

            handler.afterConnectionEstablished(session);

            WebSocketMessage message = new WebSocketMessage(
                    txId,
                    ServiceName.INVENTORY.name(),
                    TransactionStatus.SUCCESS.getCode(),
                    "Inventory reserved"
            );

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);

            // When
            handler.sendNotification(txId, message);

            // Then
            verify(session).sendMessage(messageCaptor.capture());
            String payload = messageCaptor.getValue().getPayload();

            Map<String, Object> parsed = objectMapper.readValue(payload, Map.class);
            assertThat(parsed.get("txId")).isEqualTo(txId);
            assertThat(parsed.get("serviceName")).isEqualTo("INVENTORY");
            assertThat(parsed.get("status")).isEqualTo("S");
            assertThat(parsed.get("message")).isEqualTo("Inventory reserved");
        }

        @Test
        @DisplayName("should skip closed sessions")
        void shouldSkipClosedSessions() throws Exception {
            // Given
            String txId = "tx-123";
            when(session.getId()).thenReturn("session-1");
            when(session.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));

            // Session is added during connection
            handler.afterConnectionEstablished(session);

            // After connection, session becomes closed
            when(session.isOpen()).thenReturn(false);

            WebSocketMessage message = new WebSocketMessage(
                    txId,
                    ServiceName.CREDIT_CARD.name(),
                    TransactionStatus.SUCCESS.getCode(),
                    null
            );

            // When
            handler.sendNotification(txId, message);

            // Then - should not send since session is closed
            verify(session, never()).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("should handle no sessions gracefully")
        void shouldHandleNoSessionsGracefully() {
            // Given
            String txId = "tx-nonexistent";
            WebSocketMessage message = new WebSocketMessage(
                    txId,
                    ServiceName.CREDIT_CARD.name(),
                    TransactionStatus.SUCCESS.getCode(),
                    null
            );

            // When/Then - should not throw
            handler.sendNotification(txId, message);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should remove session on transport error")
        void shouldRemoveSessionOnTransportError() throws Exception {
            // Given
            String txId = "tx-123";
            when(session.isOpen()).thenReturn(true);
            when(session.getId()).thenReturn("session-1");
            when(session.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));

            handler.afterConnectionEstablished(session);

            // When
            handler.handleTransportError(session, new RuntimeException("Connection lost"));

            // Then
            assertThat(handler.hasSession(txId)).isFalse();
        }

        @Test
        @DisplayName("should continue sending to other sessions when one fails")
        void shouldContinueSendingWhenOneFails() throws Exception {
            // Given
            String txId = "tx-123";
            WebSocketSession session2 = mock(WebSocketSession.class);

            when(session.isOpen()).thenReturn(true);
            when(session.getId()).thenReturn("session-1");
            when(session.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));
            doThrow(new RuntimeException("Send failed")).when(session).sendMessage(any());

            when(session2.isOpen()).thenReturn(true);
            when(session2.getId()).thenReturn("session-2");
            when(session2.getUri()).thenReturn(java.net.URI.create("/ws/orders/tx-123"));

            handler.afterConnectionEstablished(session);
            handler.afterConnectionEstablished(session2);

            WebSocketMessage message = new WebSocketMessage(
                    txId,
                    ServiceName.CREDIT_CARD.name(),
                    TransactionStatus.SUCCESS.getCode(),
                    null
            );

            // When
            handler.sendNotification(txId, message);

            // Then
            verify(session2).sendMessage(any(TextMessage.class));
        }
    }
}
