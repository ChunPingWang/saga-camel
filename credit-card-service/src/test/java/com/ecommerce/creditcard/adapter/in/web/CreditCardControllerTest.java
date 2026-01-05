package com.ecommerce.creditcard.adapter.in.web;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.creditcard.application.port.in.ProcessPaymentUseCase;
import com.ecommerce.creditcard.application.port.in.RollbackPaymentUseCase;
import com.ecommerce.creditcard.domain.model.Payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreditCardController.class)
@DisplayName("CreditCardController Contract Tests")
class CreditCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProcessPaymentUseCase processPaymentUseCase;

    @MockBean
    private RollbackPaymentUseCase rollbackPaymentUseCase;

    @Test
    @DisplayName("POST /api/v1/credit-card/notify - should return success response")
    void notify_shouldReturnSuccessResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "totalAmount", new BigDecimal("99.99"),
                "creditCardNumber", "4111111111111111"
        );
        NotifyRequest request = NotifyRequest.of(txId, orderId, payload);
        Payment mockPayment = new Payment(txId, orderId, new BigDecimal("99.99"), "4111111111111111",
                Payment.PaymentStatus.APPROVED, "PAY-12345678", LocalDateTime.now());

        when(processPaymentUseCase.processPayment(any(NotifyRequest.class))).thenReturn(mockPayment);

        // When & Then
        mockMvc.perform(post("/api/v1/credit-card/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.txId").value(txId.toString()))
                .andExpect(jsonPath("$.serviceReference").value("PAY-12345678"));
    }

    @Test
    @DisplayName("POST /api/v1/credit-card/notify - should return failure response for declined payment")
    void notify_shouldReturnFailureResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "totalAmount", new BigDecimal("99.99"),
                "creditCardNumber", "4111111111111111"
        );
        NotifyRequest request = NotifyRequest.of(txId, orderId, payload);
        Payment mockPayment = new Payment(txId, orderId, new BigDecimal("99.99"), "4111111111111111",
                Payment.PaymentStatus.DECLINED, null, LocalDateTime.now());

        when(processPaymentUseCase.processPayment(any(NotifyRequest.class))).thenReturn(mockPayment);

        // When & Then
        mockMvc.perform(post("/api/v1/credit-card/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Payment declined"));
    }

    @Test
    @DisplayName("POST /api/v1/credit-card/rollback - should return success response")
    void rollback_shouldReturnSuccessResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RollbackRequest request = RollbackRequest.of(txId, orderId, "Rollback payment");
        RollbackResponse expectedResponse = RollbackResponse.success(txId, "Payment refunded successfully");

        when(rollbackPaymentUseCase.rollbackPayment(any(RollbackRequest.class))).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/credit-card/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.txId").value(txId.toString()))
                .andExpect(jsonPath("$.message").value("Payment refunded successfully"));
    }

    @Test
    @DisplayName("POST /api/v1/credit-card/rollback - should return failure response")
    void rollback_shouldReturnFailureResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RollbackRequest request = RollbackRequest.of(txId, orderId, "Rollback payment");
        RollbackResponse expectedResponse = RollbackResponse.failure(txId, "Cannot refund payment");

        when(rollbackPaymentUseCase.rollbackPayment(any(RollbackRequest.class))).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/credit-card/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot refund payment"));
    }
}
