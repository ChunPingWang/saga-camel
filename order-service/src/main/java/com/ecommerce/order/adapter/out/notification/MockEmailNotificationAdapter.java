package com.ecommerce.order.adapter.out.notification;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.application.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Mock implementation of NotificationPort for development and testing.
 * Logs notifications to console and stores them for test verification.
 */
@Component
@Profile({"dev", "test"})
public class MockEmailNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(MockEmailNotificationAdapter.class);

    private final List<NotificationRecord> sentNotifications = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void sendRollbackFailureAlert(UUID txId, UUID orderId, ServiceName serviceName,
                                          String errorMessage, int retryCount) {
        log.warn("=== MOCK ADMIN NOTIFICATION ===");
        log.warn("Transaction ID: {}", txId);
        log.warn("Order ID: {}", orderId);
        log.warn("Failed Service: {}", serviceName);
        log.warn("Error Message: {}", errorMessage);
        log.warn("Retry Count: {}", retryCount);
        log.warn("Timestamp: {}", LocalDateTime.now());
        log.warn("================================");
        log.warn("[In production, this would send an email to administrators]");

        sentNotifications.add(new NotificationRecord(
                txId,
                orderId,
                serviceName,
                errorMessage,
                retryCount,
                LocalDateTime.now()
        ));
    }

    /**
     * Get all notifications sent (for test verification).
     */
    public List<NotificationRecord> getSentNotifications() {
        return List.copyOf(sentNotifications);
    }

    /**
     * Clear all recorded notifications (for test setup).
     */
    public void reset() {
        sentNotifications.clear();
    }

    /**
     * Record of a sent notification for testing.
     */
    public record NotificationRecord(
            UUID txId,
            UUID orderId,
            ServiceName serviceName,
            String errorMessage,
            int retryCount,
            LocalDateTime sentAt
    ) {}
}
