package com.ecommerce.order.adapter.out.websocket;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.adapter.in.websocket.OrderWebSocketHandler;
import com.ecommerce.order.adapter.in.websocket.WebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketAdapter Unit Tests")
class WebSocketAdapterTest {

    @Mock
    private OrderWebSocketHandler webSocketHandler;

    private WebSocketAdapter webSocketAdapter;

    @BeforeEach
    void setUp() {
        webSocketAdapter = new WebSocketAdapter(webSocketHandler);
    }

    @Nested
    @DisplayName("Send Notification Tests")
    class SendNotificationTests {

        @Test
        @DisplayName("Should send WebSocketMessage directly")
        void shouldSendWebSocketMessageDirectly() {
            // Given
            String txId = UUID.randomUUID().toString();
            WebSocketMessage message = new WebSocketMessage(txId, "CREDIT_CARD", "SUCCESS", "Payment captured");

            // When
            webSocketAdapter.sendNotification(txId, message);

            // Then
            verify(webSocketHandler).sendNotification(eq(txId), eq(message));
        }

        @Test
        @DisplayName("Should convert non-WebSocketMessage to WebSocketMessage")
        void shouldConvertNonWebSocketMessage() {
            // Given
            String txId = UUID.randomUUID().toString();
            String plainMessage = "Simple text message";

            // When
            webSocketAdapter.sendNotification(txId, plainMessage);

            // Then
            ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
            verify(webSocketHandler).sendNotification(eq(txId), messageCaptor.capture());

            WebSocketMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.txId()).isEqualTo(txId);
            assertThat(capturedMessage.status()).isEqualTo("INFO");
            assertThat(capturedMessage.message()).isEqualTo(plainMessage);
        }

        @Test
        @DisplayName("Should handle null message")
        void shouldHandleNullMessage() {
            // Given
            String txId = UUID.randomUUID().toString();

            // When
            webSocketAdapter.sendNotification(txId, null);

            // Then
            ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
            verify(webSocketHandler).sendNotification(eq(txId), messageCaptor.capture());

            WebSocketMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.message()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Send Status Update Tests")
    class SendStatusUpdateTests {

        @Test
        @DisplayName("Should send status update with all fields")
        void shouldSendStatusUpdateWithAllFields() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            TransactionStatus status = TransactionStatus.SUCCESS;
            ServiceName currentStep = ServiceName.CREDIT_CARD;
            String message = "Payment processed successfully";

            // When
            webSocketAdapter.sendStatusUpdate(txId, orderId, status, currentStep, message);

            // Then
            ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
            verify(webSocketHandler).sendNotification(eq(txId.toString()), messageCaptor.capture());

            WebSocketMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.txId()).isEqualTo(txId.toString());
            assertThat(capturedMessage.serviceName()).isEqualTo("CREDIT_CARD");
            assertThat(capturedMessage.status()).isEqualTo("S"); // TransactionStatus.SUCCESS.name() returns "S"
            assertThat(capturedMessage.message()).isEqualTo(message);
        }

        @Test
        @DisplayName("Should handle null current step")
        void shouldHandleNullCurrentStep() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            TransactionStatus status = TransactionStatus.UNKNOWN;
            String message = "Processing";

            // When
            webSocketAdapter.sendStatusUpdate(txId, orderId, status, null, message);

            // Then
            ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
            verify(webSocketHandler).sendNotification(eq(txId.toString()), messageCaptor.capture());

            WebSocketMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.serviceName()).isNull();
        }

        @Test
        @DisplayName("Should send different status types")
        void shouldSendDifferentStatusTypes() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            // When - FAILED status
            webSocketAdapter.sendStatusUpdate(txId, orderId, TransactionStatus.FAILED, ServiceName.INVENTORY, "Out of stock");

            // Then
            ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
            verify(webSocketHandler).sendNotification(eq(txId.toString()), messageCaptor.capture());

            WebSocketMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.status()).isEqualTo("F"); // TransactionStatus.FAILED.name() returns "F"
            assertThat(capturedMessage.serviceName()).isEqualTo("INVENTORY");
        }
    }
}
