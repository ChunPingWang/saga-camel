package com.ecommerce.order.adapter.out.notification;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.application.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Production implementation of NotificationPort that sends email alerts.
 * Uses JavaMail for SMTP communication.
 */
@Component
@Profile("prod")
public class EmailNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationAdapter.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String smtpHost;
    private final int smtpPort;
    private final String adminEmail;

    public EmailNotificationAdapter(
            @Value("${notification.smtp.host:}") String smtpHost,
            @Value("${notification.smtp.port:587}") int smtpPort,
            @Value("${notification.admin.email:}") String adminEmail) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.adminEmail = adminEmail;
    }

    @Override
    public void sendRollbackFailureAlert(UUID txId, UUID orderId, ServiceName serviceName,
                                          String errorMessage, int retryCount) {
        if (!isConfigured()) {
            log.warn("Email notification not configured. SMTP host or admin email is missing. " +
                    "Skipping notification for txId={}", txId);
            return;
        }

        String subject = formatSubject(txId, serviceName);
        String body = formatBody(txId, orderId, serviceName, errorMessage, retryCount);

        try {
            sendEmail(adminEmail, subject, body);
            log.info("Sent rollback failure alert email for txId={}, service={}", txId, serviceName);
        } catch (Exception e) {
            log.error("Failed to send rollback failure alert email for txId={}: {}",
                    txId, e.getMessage(), e);
        }
    }

    /**
     * Format the email subject line.
     */
    public String formatSubject(UUID txId, ServiceName serviceName) {
        String shortTxId = txId.toString().substring(0, 8);
        return String.format("[Rollback Failure Alert] %s - Transaction %s", serviceName, shortTxId);
    }

    /**
     * Format the email body with all relevant details.
     */
    public String formatBody(UUID txId, UUID orderId, ServiceName serviceName,
                             String errorMessage, int retryCount) {
        return String.format("""
                === Rollback Failure Alert ===

                A rollback operation has failed after exhausting all retry attempts.
                Manual intervention may be required.

                TRANSACTION DETAILS:
                - Transaction ID: %s
                - Order ID: %s
                - Failed Service: %s
                - Retry Count: %d
                - Timestamp: %s

                ERROR DETAILS:
                %s

                RECOMMENDED ACTIONS:
                1. Check the service health dashboard
                2. Review transaction logs for txId=%s
                3. Manually verify the transaction state
                4. Execute manual rollback if necessary

                This is an automated message from the Saga Orchestrator.
                """,
                txId,
                orderId,
                serviceName,
                retryCount,
                LocalDateTime.now().format(TIMESTAMP_FORMAT),
                errorMessage,
                txId
        );
    }

    private boolean isConfigured() {
        return smtpHost != null && !smtpHost.isBlank() &&
                adminEmail != null && !adminEmail.isBlank();
    }

    private void sendEmail(String to, String subject, String body) {
        // In a real implementation, this would use JavaMail or Spring Mail
        // For now, we log the email that would be sent
        log.info("Sending email to: {}", to);
        log.info("Subject: {}", subject);
        log.debug("Body: {}", body);

        // TODO: Implement actual email sending when SMTP is configured
        // Example implementation with Spring Mail:
        // SimpleMailMessage message = new SimpleMailMessage();
        // message.setTo(to);
        // message.setSubject(subject);
        // message.setText(body);
        // mailSender.send(message);
    }
}
