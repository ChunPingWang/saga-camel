package com.ecommerce.creditcard.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CreditCardService Unit Tests")
class CreditCardServiceTest {

    private CreditCardService creditCardService;

    @BeforeEach
    void setUp() {
        creditCardService = new CreditCardService();
        ReflectionTestUtils.setField(creditCardService, "failureEnabled", false);
        ReflectionTestUtils.setField(creditCardService, "failureRate", 0.0);
        ReflectionTestUtils.setField(creditCardService, "delayEnabled", false);
        ReflectionTestUtils.setField(creditCardService, "delayMinMs", 0);
        ReflectionTestUtils.setField(creditCardService, "delayMaxMs", 0);
    }

    private NotifyRequest createValidNotifyRequest(UUID txId) {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "totalAmount", new BigDecimal("99.99"),
                "creditCardNumber", "4111111111111111",
                "userId", "user-123",
                "items", List.of(Map.of("sku", "SKU-001", "quantity", 1, "unitPrice", 99.99))
        );
        return NotifyRequest.of(txId, orderId, payload);
    }

    @Nested
    @DisplayName("Process Payment Tests")
    class ProcessPaymentTests {

        @Test
        @DisplayName("Should successfully process payment")
        void shouldSuccessfullyProcessPayment() {
            // Given
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            NotifyResponse response = creditCardService.processPayment(request);

            // Then
            assertThat(response.success()).isTrue();
            assertThat(response.txId()).isEqualTo(txId);
            assertThat(response.serviceReference()).startsWith("AUTH-");
            assertThat(response.message()).isEqualTo("Payment captured");
        }

        @Test
        @DisplayName("Should return same response for idempotent requests")
        void shouldReturnSameResponseForIdempotentRequests() {
            // Given
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            NotifyResponse firstResponse = creditCardService.processPayment(request);
            NotifyResponse secondResponse = creditCardService.processPayment(request);

            // Then
            assertThat(firstResponse.success()).isTrue();
            assertThat(secondResponse.success()).isTrue();
            assertThat(firstResponse.serviceReference()).isEqualTo(secondResponse.serviceReference());
        }

        @Test
        @DisplayName("Should fail payment when failure simulation is enabled")
        void shouldFailPaymentWhenFailureSimulationEnabled() {
            // Given
            ReflectionTestUtils.setField(creditCardService, "failureEnabled", true);
            ReflectionTestUtils.setField(creditCardService, "failureRate", 1.0);
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            NotifyResponse response = creditCardService.processPayment(request);

            // Then
            assertThat(response.success()).isFalse();
            assertThat(response.txId()).isEqualTo(txId);
            assertThat(response.message()).contains("Payment declined");
        }
    }

    @Nested
    @DisplayName("Rollback Payment Tests")
    class RollbackPaymentTests {

        @Test
        @DisplayName("Should successfully rollback processed payment")
        void shouldSuccessfullyRollbackPayment() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            NotifyRequest notifyRequest = createValidNotifyRequest(txId);
            creditCardService.processPayment(notifyRequest);
            RollbackRequest rollbackRequest = RollbackRequest.of(txId, orderId, "Test rollback");

            // When
            RollbackResponse response = creditCardService.rollbackPayment(rollbackRequest);

            // Then
            assertThat(response.success()).isTrue();
            assertThat(response.txId()).isEqualTo(txId);
            assertThat(response.message()).isEqualTo("Payment refunded successfully");
        }

        @Test
        @DisplayName("Should handle rollback for non-existent payment")
        void shouldHandleRollbackForNonExistentPayment() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            RollbackRequest rollbackRequest = RollbackRequest.of(txId, orderId, "Test rollback");

            // When
            RollbackResponse response = creditCardService.rollbackPayment(rollbackRequest);

            // Then
            assertThat(response.success()).isTrue();
            assertThat(response.message()).isEqualTo("No payment to rollback");
        }

        @Test
        @DisplayName("Should return idempotent response for already refunded payment")
        void shouldReturnIdempotentResponseForAlreadyRefundedPayment() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            NotifyRequest notifyRequest = createValidNotifyRequest(txId);
            creditCardService.processPayment(notifyRequest);
            RollbackRequest rollbackRequest = RollbackRequest.of(txId, orderId, "Test rollback");

            // When
            creditCardService.rollbackPayment(rollbackRequest);
            RollbackResponse secondResponse = creditCardService.rollbackPayment(rollbackRequest);

            // Then
            assertThat(secondResponse.success()).isTrue();
            assertThat(secondResponse.message()).isEqualTo("Payment already refunded");
        }
    }
}
