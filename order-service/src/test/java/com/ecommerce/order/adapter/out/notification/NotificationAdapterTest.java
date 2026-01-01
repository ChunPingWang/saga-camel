package com.ecommerce.order.adapter.out.notification;

import com.ecommerce.common.domain.ServiceName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationAdapter Tests")
class NotificationAdapterTest {

    private static final UUID TX_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID ORDER_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @Nested
    @DisplayName("MockEmailNotificationAdapter")
    class MockEmailNotificationAdapterTests {

        @Test
        @DisplayName("should log notification without throwing exception")
        void shouldLogNotificationWithoutException() {
            // Given
            MockEmailNotificationAdapter adapter = new MockEmailNotificationAdapter();

            // When/Then - Should not throw
            assertThatCode(() ->
                    adapter.sendRollbackFailureAlert(
                            TX_ID,
                            ORDER_ID,
                            ServiceName.INVENTORY,
                            "Connection timeout",
                            5
                    )
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should track sent notifications")
        void shouldTrackSentNotifications() {
            // Given
            MockEmailNotificationAdapter adapter = new MockEmailNotificationAdapter();

            // When
            adapter.sendRollbackFailureAlert(TX_ID, ORDER_ID, ServiceName.CREDIT_CARD, "Refund failed", 5);
            adapter.sendRollbackFailureAlert(TX_ID, ORDER_ID, ServiceName.INVENTORY, "Stock unlock failed", 3);

            // Then
            assertThat(adapter.getSentNotifications()).hasSize(2);
            assertThat(adapter.getSentNotifications().get(0).serviceName()).isEqualTo(ServiceName.CREDIT_CARD);
            assertThat(adapter.getSentNotifications().get(1).serviceName()).isEqualTo(ServiceName.INVENTORY);
        }

        @Test
        @DisplayName("should clear notifications on reset")
        void shouldClearNotificationsOnReset() {
            // Given
            MockEmailNotificationAdapter adapter = new MockEmailNotificationAdapter();
            adapter.sendRollbackFailureAlert(TX_ID, ORDER_ID, ServiceName.LOGISTICS, "Carrier unreachable", 5);
            assertThat(adapter.getSentNotifications()).hasSize(1);

            // When
            adapter.reset();

            // Then
            assertThat(adapter.getSentNotifications()).isEmpty();
        }
    }

    @Nested
    @DisplayName("EmailNotificationAdapter")
    class EmailNotificationAdapterTests {

        @Test
        @DisplayName("should format email subject correctly")
        void shouldFormatEmailSubjectCorrectly() {
            // Given
            EmailNotificationAdapter adapter = new EmailNotificationAdapter(
                    "smtp.test.com", 587, "admin@test.com"
            );

            // When
            String subject = adapter.formatSubject(TX_ID, ServiceName.INVENTORY);

            // Then
            assertThat(subject).contains("Rollback Failure Alert");
            assertThat(subject).contains("INVENTORY");
            assertThat(subject).contains(TX_ID.toString().substring(0, 8));
        }

        @Test
        @DisplayName("should format email body with all details")
        void shouldFormatEmailBodyWithAllDetails() {
            // Given
            EmailNotificationAdapter adapter = new EmailNotificationAdapter(
                    "smtp.test.com", 587, "admin@test.com"
            );

            // When
            String body = adapter.formatBody(TX_ID, ORDER_ID, ServiceName.CREDIT_CARD, "Refund failed", 5);

            // Then
            assertThat(body).contains(TX_ID.toString());
            assertThat(body).contains(ORDER_ID.toString());
            assertThat(body).contains("CREDIT_CARD");
            assertThat(body).contains("Refund failed");
            assertThat(body).contains("5");
        }

        @Test
        @DisplayName("should handle missing SMTP configuration gracefully")
        void shouldHandleMissingSmtpConfigGracefully() {
            // Given - No SMTP configured (simulation of misconfiguration)
            EmailNotificationAdapter adapter = new EmailNotificationAdapter(null, 0, null);

            // When/Then - Should log warning but not throw
            assertThatCode(() ->
                    adapter.sendRollbackFailureAlert(
                            TX_ID,
                            ORDER_ID,
                            ServiceName.LOGISTICS,
                            "Carrier unavailable",
                            5
                    )
            ).doesNotThrowAnyException();
        }
    }
}
