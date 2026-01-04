package com.ecommerce.order.adapter.in.web;

import com.ecommerce.order.adapter.in.web.dto.OrderTransactionHistoryResponse;
import com.ecommerce.order.adapter.in.web.dto.TransactionStatusResponse;
import com.ecommerce.order.application.port.in.TransactionQueryUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@DisplayName("TransactionController Contract Tests")
class TransactionControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionQueryUseCase transactionQueryUseCase;

    @Nested
    @DisplayName("GET /api/v1/transactions/{txId}")
    class GetTransactionStatus {

        @Test
        @DisplayName("should return 200 OK with transaction details when found")
        void shouldReturn200WithDetailsWhenFound() throws Exception {
            // Given
            String txId = UUID.randomUUID().toString();
            TransactionStatusResponse response = new TransactionStatusResponse(
                    txId,
                    "order-123",
                    "PROCESSING",
                    List.of(
                            new TransactionStatusResponse.ServiceStatusDto("CREDIT_CARD", "S", Instant.now()),
                            new TransactionStatusResponse.ServiceStatusDto("INVENTORY", "U", null)
                    )
            );

            when(transactionQueryUseCase.getTransactionStatus(txId))
                    .thenReturn(Optional.of(response));

            // When/Then
            mockMvc.perform(get("/api/v1/transactions/{txId}", txId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.txId").value(txId))
                    .andExpect(jsonPath("$.orderId").value("order-123"))
                    .andExpect(jsonPath("$.overallStatus").value("PROCESSING"))
                    .andExpect(jsonPath("$.services").isArray())
                    .andExpect(jsonPath("$.services.length()").value(2));
        }

        @Test
        @DisplayName("should return 404 Not Found when transaction does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            // Given
            String txId = UUID.randomUUID().toString();
            when(transactionQueryUseCase.getTransactionStatus(anyString()))
                    .thenReturn(Optional.empty());

            // When/Then
            mockMvc.perform(get("/api/v1/transactions/{txId}", txId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return completed status when all services succeed")
        void shouldReturnCompletedWhenAllServicesSucceed() throws Exception {
            // Given
            String txId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            TransactionStatusResponse response = new TransactionStatusResponse(
                    txId,
                    "order-456",
                    "COMPLETED",
                    List.of(
                            new TransactionStatusResponse.ServiceStatusDto("CREDIT_CARD", "S", now),
                            new TransactionStatusResponse.ServiceStatusDto("INVENTORY", "S", now),
                            new TransactionStatusResponse.ServiceStatusDto("LOGISTICS", "S", now)
                    )
            );

            when(transactionQueryUseCase.getTransactionStatus(txId))
                    .thenReturn(Optional.of(response));

            // When/Then
            mockMvc.perform(get("/api/v1/transactions/{txId}", txId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").value("COMPLETED"))
                    .andExpect(jsonPath("$.services[0].status").value("S"))
                    .andExpect(jsonPath("$.services[1].status").value("S"))
                    .andExpect(jsonPath("$.services[2].status").value("S"));
        }

        @Test
        @DisplayName("should return failed status when any service fails")
        void shouldReturnFailedWhenServiceFails() throws Exception {
            // Given
            String txId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            TransactionStatusResponse response = new TransactionStatusResponse(
                    txId,
                    "order-789",
                    "FAILED",
                    List.of(
                            new TransactionStatusResponse.ServiceStatusDto("CREDIT_CARD", "S", now),
                            new TransactionStatusResponse.ServiceStatusDto("INVENTORY", "F", now),
                            new TransactionStatusResponse.ServiceStatusDto("LOGISTICS", "U", null)
                    )
            );

            when(transactionQueryUseCase.getTransactionStatus(txId))
                    .thenReturn(Optional.of(response));

            // When/Then
            mockMvc.perform(get("/api/v1/transactions/{txId}", txId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").value("FAILED"))
                    .andExpect(jsonPath("$.services[1].status").value("F"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transactions/orders/{orderId}/history")
    class GetOrderTransactionHistory {

        @Test
        @DisplayName("should return 200 OK with transaction history when order exists")
        void shouldReturn200WithHistoryWhenOrderExists() throws Exception {
            // Given
            String orderId = UUID.randomUUID().toString();
            String txId1 = UUID.randomUUID().toString();
            String txId2 = UUID.randomUUID().toString();
            Instant now = Instant.now();

            OrderTransactionHistoryResponse response = new OrderTransactionHistoryResponse(
                    orderId,
                    2,
                    List.of(
                            new OrderTransactionHistoryResponse.TransactionSummary(
                                    txId1,
                                    "COMPLETED",
                                    now.toString(),
                                    List.of(
                                            new TransactionStatusResponse.ServiceStatusDto("CREDIT_CARD", "S", now),
                                            new TransactionStatusResponse.ServiceStatusDto("INVENTORY", "S", now),
                                            new TransactionStatusResponse.ServiceStatusDto("LOGISTICS", "S", now)
                                    )
                            ),
                            new OrderTransactionHistoryResponse.TransactionSummary(
                                    txId2,
                                    "ROLLED_BACK",
                                    now.minusSeconds(300).toString(),
                                    List.of(
                                            new TransactionStatusResponse.ServiceStatusDto("CREDIT_CARD", "R", now),
                                            new TransactionStatusResponse.ServiceStatusDto("INVENTORY", "F", now),
                                            new TransactionStatusResponse.ServiceStatusDto("LOGISTICS", "U", null)
                                    )
                            )
                    )
            );

            when(transactionQueryUseCase.getOrderTransactionHistory(orderId))
                    .thenReturn(Optional.of(response));

            // When/Then
            mockMvc.perform(get("/api/v1/transactions/orders/{orderId}/history", orderId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.orderId").value(orderId))
                    .andExpect(jsonPath("$.totalTransactions").value(2))
                    .andExpect(jsonPath("$.transactions").isArray())
                    .andExpect(jsonPath("$.transactions.length()").value(2))
                    .andExpect(jsonPath("$.transactions[0].txId").value(txId1))
                    .andExpect(jsonPath("$.transactions[0].overallStatus").value("COMPLETED"))
                    .andExpect(jsonPath("$.transactions[1].txId").value(txId2))
                    .andExpect(jsonPath("$.transactions[1].overallStatus").value("ROLLED_BACK"));
        }

        @Test
        @DisplayName("should return 404 Not Found when order has no transactions")
        void shouldReturn404WhenOrderNotFound() throws Exception {
            // Given
            String orderId = UUID.randomUUID().toString();
            when(transactionQueryUseCase.getOrderTransactionHistory(anyString()))
                    .thenReturn(Optional.empty());

            // When/Then
            mockMvc.perform(get("/api/v1/transactions/orders/{orderId}/history", orderId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return single transaction when order has one execution")
        void shouldReturnSingleTransactionHistory() throws Exception {
            // Given
            String orderId = UUID.randomUUID().toString();
            String txId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            OrderTransactionHistoryResponse response = new OrderTransactionHistoryResponse(
                    orderId,
                    1,
                    List.of(
                            new OrderTransactionHistoryResponse.TransactionSummary(
                                    txId,
                                    "PROCESSING",
                                    now.toString(),
                                    List.of(
                                            new TransactionStatusResponse.ServiceStatusDto("CREDIT_CARD", "S", now),
                                            new TransactionStatusResponse.ServiceStatusDto("INVENTORY", "U", null),
                                            new TransactionStatusResponse.ServiceStatusDto("LOGISTICS", "U", null)
                                    )
                            )
                    )
            );

            when(transactionQueryUseCase.getOrderTransactionHistory(orderId))
                    .thenReturn(Optional.of(response));

            // When/Then
            mockMvc.perform(get("/api/v1/transactions/orders/{orderId}/history", orderId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTransactions").value(1))
                    .andExpect(jsonPath("$.transactions[0].overallStatus").value("PROCESSING"))
                    .andExpect(jsonPath("$.transactions[0].services.length()").value(3));
        }

        @Test
        @DisplayName("should include service details in each transaction summary")
        void shouldIncludeServiceDetailsInTransactionSummary() throws Exception {
            // Given
            String orderId = UUID.randomUUID().toString();
            String txId = UUID.randomUUID().toString();
            Instant completedAt = Instant.parse("2026-01-04T10:30:00Z");

            OrderTransactionHistoryResponse response = new OrderTransactionHistoryResponse(
                    orderId,
                    1,
                    List.of(
                            new OrderTransactionHistoryResponse.TransactionSummary(
                                    txId,
                                    "COMPLETED",
                                    completedAt.toString(),
                                    List.of(
                                            new TransactionStatusResponse.ServiceStatusDto("CREDIT_CARD", "S", completedAt),
                                            new TransactionStatusResponse.ServiceStatusDto("INVENTORY", "S", completedAt.plusSeconds(5)),
                                            new TransactionStatusResponse.ServiceStatusDto("LOGISTICS", "S", completedAt.plusSeconds(10))
                                    )
                            )
                    )
            );

            when(transactionQueryUseCase.getOrderTransactionHistory(orderId))
                    .thenReturn(Optional.of(response));

            // When/Then
            mockMvc.perform(get("/api/v1/transactions/orders/{orderId}/history", orderId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transactions[0].services[0].serviceName").value("CREDIT_CARD"))
                    .andExpect(jsonPath("$.transactions[0].services[0].status").value("S"))
                    .andExpect(jsonPath("$.transactions[0].services[1].serviceName").value("INVENTORY"))
                    .andExpect(jsonPath("$.transactions[0].services[2].serviceName").value("LOGISTICS"));
        }
    }
}
