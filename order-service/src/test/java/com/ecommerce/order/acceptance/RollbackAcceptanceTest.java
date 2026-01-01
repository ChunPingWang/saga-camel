package com.ecommerce.order.acceptance;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.application.port.out.NotificationPort;
import com.ecommerce.order.application.port.out.OutboxPort;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.application.service.RollbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BDD-style acceptance tests for rollback scenarios.
 *
 * Feature: Automatic Rollback on Service Failure
 *   As an e-commerce system
 *   I want to automatically rollback completed services when a later service fails
 *   So that the system remains in a consistent state
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Feature: Automatic Rollback on Service Failure")
class RollbackAcceptanceTest {

    @Autowired
    private RollbackService rollbackService;

    @MockBean
    private ServiceClientPort serviceClientPort;

    @MockBean
    private OutboxPort outboxPort;

    @MockBean
    private WebSocketPort webSocketPort;

    @MockBean
    private NotificationPort notificationPort;

    @Nested
    @DisplayName("Scenario: Inventory failure triggers payment rollback")
    class InventoryFailureTriggersPaymentRollback {

        @Test
        @DisplayName("Given payment succeeded, When inventory fails, Then payment should be rolled back")
        void shouldRollbackPaymentWhenInventoryFails() {
            // Given: Payment has succeeded
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD);

            // Mock: Payment rollback succeeds
            when(serviceClientPort.rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.success(txId, "Payment refunded"));

            // When: Inventory fails and rollback is triggered
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then: Payment service rollback is called
            verify(serviceClientPort).rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));

            // And: Inventory rollback is NOT called (it was the failing service)
            verify(serviceClientPort, never()).rollback(eq(ServiceName.INVENTORY), any());
        }
    }

    @Nested
    @DisplayName("Scenario: Logistics failure triggers payment and inventory rollback")
    class LogisticsFailureTriggersMultipleRollbacks {

        @Test
        @DisplayName("Given payment and inventory succeeded, When logistics fails, Then both should be rolled back in reverse order")
        void shouldRollbackBothServicesInReverseOrder() {
            // Given: Payment and inventory have succeeded
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD, ServiceName.INVENTORY);

            // Mock: Both rollbacks succeed
            when(serviceClientPort.rollback(any(ServiceName.class), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.success(txId, "Rolled back"));

            // When: Logistics fails and rollback is triggered
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then: Both services are rolled back in reverse order
            var inOrder = inOrder(serviceClientPort);
            inOrder.verify(serviceClientPort).rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class));
            inOrder.verify(serviceClientPort).rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));
        }
    }

    @Nested
    @DisplayName("Scenario: Rollback continues even when one service fails")
    class RollbackContinuesOnPartialFailure {

        @Test
        @DisplayName("Given multiple services to rollback, When one rollback fails, Then remaining rollbacks still execute")
        void shouldContinueRollbackEvenWhenOneServiceFails() {
            // Given: Both payment and inventory need rollback
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD, ServiceName.INVENTORY);

            // Mock: Inventory rollback fails, but payment rollback succeeds
            when(serviceClientPort.rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.failure(txId, "Inventory system unavailable"));
            when(serviceClientPort.rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.success(txId, "Payment refunded"));

            // When: Rollback is triggered
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then: Both services are attempted to be rolled back
            verify(serviceClientPort).rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class));
            verify(serviceClientPort).rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));
        }
    }

    @Nested
    @DisplayName("Scenario: Rollback with retry exhaustion sends admin notification")
    class RollbackRetryExhaustionNotifiesAdmin {

        @Test
        @DisplayName("Given rollback fails repeatedly, When max retries exceeded, Then admin is notified")
        void shouldNotifyAdminWhenMaxRetriesExceeded() {
            // Given: Service rollback always fails
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD);
            int maxRetries = 3;

            // Mock: Rollback always fails
            when(serviceClientPort.rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.failure(txId, "Service temporarily unavailable"));

            // When: Rollback with retries is triggered
            rollbackService.executeRollbackWithRetry(txId, orderId, successfulServices, maxRetries);

            // Then: Rollback is attempted max retries times
            verify(serviceClientPort, times(maxRetries))
                    .rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));

            // And: Admin notification is sent
            verify(notificationPort).sendRollbackFailureAlert(
                    eq(txId),
                    eq(orderId),
                    eq(ServiceName.CREDIT_CARD),
                    any(),
                    eq(maxRetries)
            );
        }
    }

    @Nested
    @DisplayName("Scenario: Rollback succeeds within retry limit")
    class RollbackSucceedsWithinRetryLimit {

        @Test
        @DisplayName("Given rollback fails initially, When it succeeds within retry limit, Then no admin notification is sent")
        void shouldNotNotifyAdminWhenRollbackEventuallySucceeds() {
            // Given: Service rollback fails twice then succeeds
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD);
            int maxRetries = 5;

            // Mock: Rollback fails twice, then succeeds
            when(serviceClientPort.rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.failure(txId, "Temporary error"))
                    .thenReturn(RollbackResponse.failure(txId, "Still failing"))
                    .thenReturn(RollbackResponse.success(txId, "Finally succeeded"));

            // When: Rollback with retries is triggered
            rollbackService.executeRollbackWithRetry(txId, orderId, successfulServices, maxRetries);

            // Then: Rollback is attempted 3 times (2 failures + 1 success)
            verify(serviceClientPort, times(3))
                    .rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));

            // And: No admin notification is sent
            verify(notificationPort, never()).sendRollbackFailureAlert(any(), any(), any(), any(), anyInt());
        }
    }
}
