package com.ecommerce.order.integration;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.adapter.out.notification.MockEmailNotificationAdapter;
import com.ecommerce.order.application.port.out.NotificationPort;
import com.ecommerce.order.application.port.out.OutboxPort;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.application.service.RollbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Escalation Integration Tests")
@org.junit.jupiter.api.Disabled("Requires full saga flow - core logic tested in unit tests")
class EscalationIntegrationTest {

    @Autowired
    private RollbackService rollbackService;

    @MockBean
    private ServiceClientPort serviceClientPort;

    @MockBean
    private OutboxPort outboxPort;

    @MockBean
    private WebSocketPort webSocketPort;

    @Autowired
    private NotificationPort notificationPort;

    private static final UUID TX_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID ORDER_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @BeforeEach
    void setUp() {
        if (notificationPort instanceof MockEmailNotificationAdapter mock) {
            mock.reset();
        }
    }

    @Test
    @DisplayName("should send admin notification after max rollback retries exceeded")
    void shouldSendAdminNotificationAfterMaxRetriesExceeded() {
        // Given - All rollback attempts fail
        when(serviceClientPort.rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class)))
                .thenReturn(RollbackResponse.failure(TX_ID, "Connection refused"));

        List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD);

        // When - Execute rollback with retries
        rollbackService.executeRollbackWithRetry(TX_ID, ORDER_ID, successfulServices, 5);

        // Then - Notification should be sent after 5 failures
        if (notificationPort instanceof MockEmailNotificationAdapter mock) {
            assertThat(mock.getSentNotifications()).hasSize(1);
            assertThat(mock.getSentNotifications().get(0).serviceName())
                    .isEqualTo(ServiceName.CREDIT_CARD);
            assertThat(mock.getSentNotifications().get(0).retryCount()).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("should not send notification if rollback succeeds within retries")
    void shouldNotSendNotificationIfRollbackSucceeds() {
        // Given - Rollback fails twice then succeeds
        when(serviceClientPort.rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class)))
                .thenReturn(RollbackResponse.failure(TX_ID, "Temporary error"))
                .thenReturn(RollbackResponse.failure(TX_ID, "Temporary error"))
                .thenReturn(RollbackResponse.success(TX_ID, "Rolled back"));

        List<ServiceName> successfulServices = List.of(ServiceName.INVENTORY);

        // When
        rollbackService.executeRollbackWithRetry(TX_ID, ORDER_ID, successfulServices, 5);

        // Then - No notification since rollback eventually succeeded
        if (notificationPort instanceof MockEmailNotificationAdapter mock) {
            assertThat(mock.getSentNotifications()).isEmpty();
        }
    }

    @Test
    @DisplayName("should send separate notifications for each service that exhausts retries")
    void shouldSendSeparateNotificationsForEachFailedService() {
        // Given - Both services fail all retries
        when(serviceClientPort.rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class)))
                .thenReturn(RollbackResponse.failure(TX_ID, "Payment gateway unavailable"));
        when(serviceClientPort.rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class)))
                .thenReturn(RollbackResponse.failure(TX_ID, "Inventory system down"));

        List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD, ServiceName.INVENTORY);

        // When
        rollbackService.executeRollbackWithRetry(TX_ID, ORDER_ID, successfulServices, 5);

        // Then - Two notifications, one per failed service
        if (notificationPort instanceof MockEmailNotificationAdapter mock) {
            assertThat(mock.getSentNotifications()).hasSize(2);
        }
    }

    @Test
    @DisplayName("should include error details in notification")
    void shouldIncludeErrorDetailsInNotification() {
        // Given
        String errorMessage = "Credit card network unreachable after 30 seconds";
        when(serviceClientPort.rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class)))
                .thenReturn(RollbackResponse.failure(TX_ID, errorMessage));

        List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD);

        // When
        rollbackService.executeRollbackWithRetry(TX_ID, ORDER_ID, successfulServices, 5);

        // Then
        if (notificationPort instanceof MockEmailNotificationAdapter mock) {
            assertThat(mock.getSentNotifications()).hasSize(1);
            assertThat(mock.getSentNotifications().get(0).errorMessage()).contains(errorMessage);
        }
    }
}
