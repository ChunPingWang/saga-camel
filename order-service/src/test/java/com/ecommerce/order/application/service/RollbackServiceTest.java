package com.ecommerce.order.application.service;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.domain.model.TransactionLog;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RollbackService Tests")
class RollbackServiceTest {

    private static final String TEST_TX_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String TEST_ORDER_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

    @Mock
    private TransactionLogPort transactionLogPort;

    @Mock
    private ServiceClientPort serviceClientPort;

    @Mock
    private WebSocketPort webSocketPort;

    private RollbackService rollbackService;

    @BeforeEach
    void setUp() {
        rollbackService = new RollbackService(transactionLogPort, serviceClientPort, webSocketPort);
    }

    @Nested
    @DisplayName("executeRollback")
    class ExecuteRollback {

        @Test
        @DisplayName("should rollback services in reverse order")
        void shouldRollbackServicesInReverseOrder() {
            // Given
            UUID txId = UUID.fromString(TEST_TX_ID);
            UUID orderId = UUID.fromString(TEST_ORDER_ID);

            List<ServiceName> successfulServices = List.of(
                    ServiceName.CREDIT_CARD,
                    ServiceName.INVENTORY
            );

            when(serviceClientPort.rollback(any(ServiceName.class), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.success(txId, "Rolled back"));

            // When
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then - verify reverse order: INVENTORY first, then CREDIT_CARD
            var inOrder = inOrder(serviceClientPort);
            inOrder.verify(serviceClientPort).rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class));
            inOrder.verify(serviceClientPort).rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));
        }

        @Test
        @DisplayName("should record ROLLBACK status for each service")
        void shouldRecordRollbackStatusForEachService() {
            // Given
            UUID txId = UUID.fromString(TEST_TX_ID);
            UUID orderId = UUID.fromString(TEST_ORDER_ID);

            List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD);

            when(serviceClientPort.rollback(any(ServiceName.class), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.success(txId, "Rolled back"));

            // When
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then
            verify(transactionLogPort).recordStatus(
                    eq(txId),
                    eq(orderId),
                    eq(ServiceName.CREDIT_CARD),
                    eq(TransactionStatus.R)
            );
        }

        @Test
        @DisplayName("should record ROLLBACK_FAILED status when rollback fails")
        void shouldRecordRollbackFailedStatusWhenRollbackFails() {
            // Given
            UUID txId = UUID.fromString(TEST_TX_ID);
            UUID orderId = UUID.fromString(TEST_ORDER_ID);

            List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD);

            when(serviceClientPort.rollback(any(ServiceName.class), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.failure(txId, "Rollback failed"));

            // When
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then
            verify(transactionLogPort).recordStatusWithError(
                    eq(txId),
                    eq(orderId),
                    eq(ServiceName.CREDIT_CARD),
                    eq(TransactionStatus.RF),
                    eq("Rollback failed")
            );
        }

        @Test
        @DisplayName("should send WebSocket notification for each rollback")
        void shouldSendWebSocketNotificationForEachRollback() {
            // Given
            UUID txId = UUID.fromString(TEST_TX_ID);
            UUID orderId = UUID.fromString(TEST_ORDER_ID);

            List<ServiceName> successfulServices = List.of(
                    ServiceName.CREDIT_CARD,
                    ServiceName.INVENTORY
            );

            when(serviceClientPort.rollback(any(ServiceName.class), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.success(txId, "Rolled back"));

            // When
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then
            verify(webSocketPort).sendRollbackProgress(txId, orderId, ServiceName.INVENTORY);
            verify(webSocketPort).sendRollbackProgress(txId, orderId, ServiceName.CREDIT_CARD);
        }

        @Test
        @DisplayName("should continue rollback even if one service fails")
        void shouldContinueRollbackEvenIfOneServiceFails() {
            // Given
            UUID txId = UUID.fromString(TEST_TX_ID);
            UUID orderId = UUID.fromString(TEST_ORDER_ID);

            List<ServiceName> successfulServices = List.of(
                    ServiceName.CREDIT_CARD,
                    ServiceName.INVENTORY
            );

            // Inventory rollback fails, but credit card should still be attempted
            when(serviceClientPort.rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.failure(txId, "Failed"));
            when(serviceClientPort.rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.success(txId, "Rolled back"));

            // When
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then - both services should be called
            verify(serviceClientPort).rollback(eq(ServiceName.INVENTORY), any(RollbackRequest.class));
            verify(serviceClientPort).rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));
        }

        @Test
        @DisplayName("should send completed notification when all rollbacks succeed")
        void shouldSendCompletedNotificationWhenAllRollbacksSucceed() {
            // Given
            UUID txId = UUID.fromString(TEST_TX_ID);
            UUID orderId = UUID.fromString(TEST_ORDER_ID);

            List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD);

            when(serviceClientPort.rollback(any(ServiceName.class), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.success(txId, "Rolled back"));

            // When
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then
            verify(webSocketPort).sendRolledBack(txId, orderId);
        }

        @Test
        @DisplayName("should send failure notification when any rollback fails")
        void shouldSendFailureNotificationWhenAnyRollbackFails() {
            // Given
            UUID txId = UUID.fromString(TEST_TX_ID);
            UUID orderId = UUID.fromString(TEST_ORDER_ID);

            List<ServiceName> successfulServices = List.of(ServiceName.CREDIT_CARD);

            when(serviceClientPort.rollback(any(ServiceName.class), any(RollbackRequest.class)))
                    .thenReturn(RollbackResponse.failure(txId, "Rollback failed"));

            // When
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then
            verify(webSocketPort).sendRollbackFailed(eq(txId), eq(orderId), anyString());
        }

        @Test
        @DisplayName("should handle empty successful services list")
        void shouldHandleEmptySuccessfulServicesList() {
            // Given
            UUID txId = UUID.fromString(TEST_TX_ID);
            UUID orderId = UUID.fromString(TEST_ORDER_ID);

            List<ServiceName> successfulServices = List.of();

            // When
            rollbackService.executeRollback(txId, orderId, successfulServices);

            // Then - no rollback calls should be made
            verify(serviceClientPort, never()).rollback(any(ServiceName.class), any(RollbackRequest.class));
            // Should still send completed notification
            verify(webSocketPort).sendRolledBack(txId, orderId);
        }
    }

    @Nested
    @DisplayName("getSuccessfulServices")
    class GetSuccessfulServices {

        @Test
        @DisplayName("should return list of services with SUCCESS status")
        void shouldReturnListOfServicesWithSuccessStatus() {
            // Given
            UUID txId = UUID.fromString(TEST_TX_ID);

            when(transactionLogPort.findSuccessfulServices(txId))
                    .thenReturn(List.of(ServiceName.CREDIT_CARD, ServiceName.INVENTORY));

            // When
            List<ServiceName> result = rollbackService.getSuccessfulServices(txId);

            // Then
            assertThat(result).containsExactly(ServiceName.CREDIT_CARD, ServiceName.INVENTORY);
        }
    }
}
