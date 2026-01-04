package com.ecommerce.order.application.service;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmRequest;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmResponse;
import com.ecommerce.order.adapter.in.web.dto.OrderTransactionHistoryResponse;
import com.ecommerce.order.adapter.in.web.dto.TransactionStatusResponse;
import com.ecommerce.order.application.port.out.CheckerPort;
import com.ecommerce.order.application.port.out.OutboxPort;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.domain.model.TransactionLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaService Tests")
class OrderSagaServiceTest {

    private static final String TEST_TX_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String TEST_ORDER_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

    @Mock
    private TransactionLogPort transactionLogPort;

    @Mock
    private OutboxPort outboxPort;

    @Mock
    private CheckerPort checkerPort;

    private OrderSagaService orderSagaService;

    @BeforeEach
    void setUp() {
        orderSagaService = new OrderSagaService(transactionLogPort, outboxPort, checkerPort);
    }

    @Nested
    @DisplayName("confirmOrder")
    class ConfirmOrder {

        @Test
        @DisplayName("should generate unique txId and return PROCESSING status")
        void shouldGenerateTxIdAndReturnProcessingStatus() {
            // Given
            OrderConfirmRequest request = createValidRequest();

            // When
            OrderConfirmResponse response = orderSagaService.confirmOrder(request);

            // Then
            assertThat(response.txId()).isNotBlank();
            assertThat(response.status()).isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("should create initial transaction log entries for all services")
        void shouldCreateInitialTransactionLogEntries() {
            // Given
            OrderConfirmRequest request = createValidRequest();

            // When
            orderSagaService.confirmOrder(request);

            // Then
            ArgumentCaptor<TransactionLog> captor = ArgumentCaptor.forClass(TransactionLog.class);
            verify(transactionLogPort, times(3)).save(captor.capture());

            List<TransactionLog> logs = captor.getAllValues();
            assertThat(logs).hasSize(3);
            assertThat(logs).extracting(TransactionLog::getServiceName)
                    .containsExactly(ServiceName.CREDIT_CARD, ServiceName.INVENTORY, ServiceName.LOGISTICS);
            assertThat(logs).extracting(TransactionLog::getStatus)
                    .containsOnly(TransactionStatus.UNKNOWN);
        }

        @Test
        @DisplayName("should create outbox event with order payload")
        void shouldCreateOutboxEvent() {
            // Given
            OrderConfirmRequest request = createValidRequest();

            // When
            OrderConfirmResponse response = orderSagaService.confirmOrder(request);

            // Then
            verify(outboxPort).save(argThat(event ->
                    event.getTxId().equals(response.txId()) &&
                            event.getOrderId().equals(request.orderId()) &&
                            event.getEventType().equals("ORDER_CONFIRMED")
            ));
        }

        @Test
        @DisplayName("should use same txId for all transaction logs and outbox event")
        void shouldUseSameTxIdForAllEntries() {
            // Given
            OrderConfirmRequest request = createValidRequest();

            // When
            OrderConfirmResponse response = orderSagaService.confirmOrder(request);

            // Then
            ArgumentCaptor<TransactionLog> logCaptor = ArgumentCaptor.forClass(TransactionLog.class);
            verify(transactionLogPort, times(3)).save(logCaptor.capture());

            logCaptor.getAllValues().forEach(log ->
                    assertThat(log.getTxId().toString()).isEqualTo(response.txId())
            );
        }

        private OrderConfirmRequest createValidRequest() {
            return new OrderConfirmRequest(
                    "c1d2e3f4-a5b6-7890-cdef-123456789012",  // valid UUID format
                    "user-123",
                    List.of(
                            new OrderConfirmRequest.OrderItemDto("SKU-001", 2, new BigDecimal("29.99")),
                            new OrderConfirmRequest.OrderItemDto("SKU-002", 1, new BigDecimal("49.99"))
                    ),
                    new BigDecimal("109.97"),
                    "4111111111111111"
            );
        }
    }

    @Nested
    @DisplayName("getTransactionStatus")
    class GetTransactionStatus {

        @Test
        @DisplayName("should return empty when no transaction logs found")
        void shouldReturnEmptyWhenNotFound() {
            // Given
            when(transactionLogPort.findLatestByTxId(anyString())).thenReturn(List.of());

            // When
            Optional<TransactionStatusResponse> result = orderSagaService.getTransactionStatus(TEST_TX_ID);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return PROCESSING when any service is UNKNOWN")
        void shouldReturnProcessingWhenAnyUnknown() {
            // Given
            List<TransactionLog> logs = List.of(
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.CREDIT_CARD, TransactionStatus.SUCCESS),
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.INVENTORY, TransactionStatus.UNKNOWN)
            );
            when(transactionLogPort.findLatestByTxId(TEST_TX_ID)).thenReturn(logs);

            // When
            Optional<TransactionStatusResponse> result = orderSagaService.getTransactionStatus(TEST_TX_ID);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().overallStatus()).isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("should return COMPLETED when all services are SUCCESS")
        void shouldReturnCompletedWhenAllSuccess() {
            // Given
            List<TransactionLog> logs = List.of(
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.CREDIT_CARD, TransactionStatus.SUCCESS),
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.INVENTORY, TransactionStatus.SUCCESS),
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.LOGISTICS, TransactionStatus.SUCCESS)
            );
            when(transactionLogPort.findLatestByTxId(TEST_TX_ID)).thenReturn(logs);

            // When
            Optional<TransactionStatusResponse> result = orderSagaService.getTransactionStatus(TEST_TX_ID);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().overallStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("should return FAILED when any service is FAILED")
        void shouldReturnFailedWhenAnyFailed() {
            // Given
            List<TransactionLog> logs = List.of(
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.CREDIT_CARD, TransactionStatus.SUCCESS),
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.INVENTORY, TransactionStatus.FAILED)
            );
            when(transactionLogPort.findLatestByTxId(TEST_TX_ID)).thenReturn(logs);

            // When
            Optional<TransactionStatusResponse> result = orderSagaService.getTransactionStatus(TEST_TX_ID);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().overallStatus()).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("should return ROLLING_BACK when any service is ROLLBACK")
        void shouldReturnRollingBackWhenAnyRollback() {
            // Given
            List<TransactionLog> logs = List.of(
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.CREDIT_CARD, TransactionStatus.ROLLBACK),
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.INVENTORY, TransactionStatus.SUCCESS)
            );
            when(transactionLogPort.findLatestByTxId(TEST_TX_ID)).thenReturn(logs);

            // When
            Optional<TransactionStatusResponse> result = orderSagaService.getTransactionStatus(TEST_TX_ID);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().overallStatus()).isEqualTo("ROLLING_BACK");
        }
    }

    @Nested
    @DisplayName("getOrderTransactionHistory")
    class GetOrderTransactionHistory {

        private static final String TEST_TX_ID_2 = "c3d4e5f6-a7b8-9012-cdef-234567890123";

        @Test
        @DisplayName("should return empty when no transactions found for order")
        void shouldReturnEmptyWhenNoTransactionsFound() {
            // Given
            when(transactionLogPort.findDistinctTxIdsByOrderId(any(UUID.class))).thenReturn(List.of());

            // When
            Optional<OrderTransactionHistoryResponse> result = orderSagaService.getOrderTransactionHistory(TEST_ORDER_ID);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for invalid order ID format")
        void shouldReturnEmptyForInvalidOrderIdFormat() {
            // When
            Optional<OrderTransactionHistoryResponse> result = orderSagaService.getOrderTransactionHistory("invalid-uuid");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return single transaction history")
        void shouldReturnSingleTransactionHistory() {
            // Given
            UUID orderUuid = UUID.fromString(TEST_ORDER_ID);
            UUID txUuid = UUID.fromString(TEST_TX_ID);

            when(transactionLogPort.findDistinctTxIdsByOrderId(orderUuid))
                    .thenReturn(List.of(txUuid));

            List<TransactionLog> logs = List.of(
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.CREDIT_CARD, TransactionStatus.SUCCESS),
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.INVENTORY, TransactionStatus.SUCCESS),
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.LOGISTICS, TransactionStatus.SUCCESS)
            );
            when(transactionLogPort.findLatestByTxId(TEST_TX_ID)).thenReturn(logs);

            // When
            Optional<OrderTransactionHistoryResponse> result = orderSagaService.getOrderTransactionHistory(TEST_ORDER_ID);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().orderId()).isEqualTo(TEST_ORDER_ID);
            assertThat(result.get().totalTransactions()).isEqualTo(1);
            assertThat(result.get().transactions()).hasSize(1);
            assertThat(result.get().transactions().get(0).txId()).isEqualTo(TEST_TX_ID);
            assertThat(result.get().transactions().get(0).overallStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("should return multiple transaction history for order with retries")
        void shouldReturnMultipleTransactionHistory() {
            // Given
            UUID orderUuid = UUID.fromString(TEST_ORDER_ID);
            UUID txUuid1 = UUID.fromString(TEST_TX_ID);
            UUID txUuid2 = UUID.fromString(TEST_TX_ID_2);

            when(transactionLogPort.findDistinctTxIdsByOrderId(orderUuid))
                    .thenReturn(List.of(txUuid1, txUuid2));

            // First transaction - rolled back
            List<TransactionLog> logs1 = List.of(
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.CREDIT_CARD, TransactionStatus.ROLLBACK),
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.INVENTORY, TransactionStatus.FAILED)
            );
            when(transactionLogPort.findLatestByTxId(TEST_TX_ID)).thenReturn(logs1);

            // Second transaction - completed
            List<TransactionLog> logs2 = List.of(
                    TransactionLog.create(TEST_TX_ID_2, TEST_ORDER_ID, ServiceName.CREDIT_CARD, TransactionStatus.SUCCESS),
                    TransactionLog.create(TEST_TX_ID_2, TEST_ORDER_ID, ServiceName.INVENTORY, TransactionStatus.SUCCESS),
                    TransactionLog.create(TEST_TX_ID_2, TEST_ORDER_ID, ServiceName.LOGISTICS, TransactionStatus.SUCCESS)
            );
            when(transactionLogPort.findLatestByTxId(TEST_TX_ID_2)).thenReturn(logs2);

            // When
            Optional<OrderTransactionHistoryResponse> result = orderSagaService.getOrderTransactionHistory(TEST_ORDER_ID);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().totalTransactions()).isEqualTo(2);
            assertThat(result.get().transactions()).hasSize(2);

            // First transaction should show ROLLING_BACK (has ROLLBACK status)
            assertThat(result.get().transactions().get(0).overallStatus()).isEqualTo("ROLLING_BACK");

            // Second transaction should be COMPLETED
            assertThat(result.get().transactions().get(1).overallStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("should include service statuses in each transaction summary")
        void shouldIncludeServiceStatusesInSummary() {
            // Given
            UUID orderUuid = UUID.fromString(TEST_ORDER_ID);
            UUID txUuid = UUID.fromString(TEST_TX_ID);

            when(transactionLogPort.findDistinctTxIdsByOrderId(orderUuid))
                    .thenReturn(List.of(txUuid));

            List<TransactionLog> logs = List.of(
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.CREDIT_CARD, TransactionStatus.SUCCESS),
                    TransactionLog.create(TEST_TX_ID, TEST_ORDER_ID, ServiceName.INVENTORY, TransactionStatus.UNKNOWN)
            );
            when(transactionLogPort.findLatestByTxId(TEST_TX_ID)).thenReturn(logs);

            // When
            Optional<OrderTransactionHistoryResponse> result = orderSagaService.getOrderTransactionHistory(TEST_ORDER_ID);

            // Then
            assertThat(result).isPresent();
            var services = result.get().transactions().get(0).services();
            assertThat(services).hasSize(2);
            assertThat(services).extracting(TransactionStatusResponse.ServiceStatusDto::serviceName)
                    .containsExactly("CREDIT_CARD", "INVENTORY");
            assertThat(services).extracting(TransactionStatusResponse.ServiceStatusDto::status)
                    .containsExactly("S", "U");
        }
    }
}
