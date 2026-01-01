package com.ecommerce.order.adapter.in.web;

import com.ecommerce.order.adapter.in.web.dto.OrderConfirmRequest;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmResponse;
import com.ecommerce.order.application.port.in.OrderConfirmUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController Contract Tests")
class OrderControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderConfirmUseCase orderConfirmUseCase;

    @Nested
    @DisplayName("POST /api/v1/orders/confirm")
    class ConfirmOrder {

        @Test
        @DisplayName("should return 202 Accepted with txId when order is valid")
        void shouldReturn202WithTxIdWhenOrderIsValid() throws Exception {
            // Given
            String expectedTxId = UUID.randomUUID().toString();
            OrderConfirmRequest request = createValidRequest();

            when(orderConfirmUseCase.confirmOrder(any()))
                    .thenReturn(new OrderConfirmResponse(expectedTxId, "PROCESSING"));

            // When/Then
            mockMvc.perform(post("/api/v1/orders/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.txId").value(expectedTxId))
                    .andExpect(jsonPath("$.status").value("PROCESSING"));
        }

        @Test
        @DisplayName("should return 400 Bad Request when orderId is missing")
        void shouldReturn400WhenOrderIdMissing() throws Exception {
            // Given
            OrderConfirmRequest request = new OrderConfirmRequest(
                    null,  // missing orderId
                    "user-123",
                    List.of(new OrderConfirmRequest.OrderItemDto("SKU-001", 2, new BigDecimal("29.99"))),
                    new BigDecimal("59.98"),
                    "4111111111111111"
            );

            // When/Then
            mockMvc.perform(post("/api/v1/orders/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 Bad Request when items list is empty")
        void shouldReturn400WhenItemsEmpty() throws Exception {
            // Given
            OrderConfirmRequest request = new OrderConfirmRequest(
                    "order-123",
                    "user-123",
                    List.of(),  // empty items
                    new BigDecimal("59.98"),
                    "4111111111111111"
            );

            // When/Then
            mockMvc.perform(post("/api/v1/orders/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 Bad Request when totalAmount is negative")
        void shouldReturn400WhenTotalAmountNegative() throws Exception {
            // Given
            OrderConfirmRequest request = new OrderConfirmRequest(
                    "order-123",
                    "user-123",
                    List.of(new OrderConfirmRequest.OrderItemDto("SKU-001", 2, new BigDecimal("29.99"))),
                    new BigDecimal("-10.00"),  // negative amount
                    "4111111111111111"
            );

            // When/Then
            mockMvc.perform(post("/api/v1/orders/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 Bad Request when creditCardNumber is blank")
        void shouldReturn400WhenCreditCardBlank() throws Exception {
            // Given
            OrderConfirmRequest request = new OrderConfirmRequest(
                    "order-123",
                    "user-123",
                    List.of(new OrderConfirmRequest.OrderItemDto("SKU-001", 2, new BigDecimal("29.99"))),
                    new BigDecimal("59.98"),
                    ""  // blank credit card
            );

            // When/Then
            mockMvc.perform(post("/api/v1/orders/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        private OrderConfirmRequest createValidRequest() {
            return new OrderConfirmRequest(
                    "order-123",
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
}
