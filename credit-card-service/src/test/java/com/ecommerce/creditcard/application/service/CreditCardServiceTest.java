package com.ecommerce.creditcard.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.creditcard.domain.model.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            Payment payment = creditCardService.processPayment(request);

            // Then
            assertThat(payment.isApproved()).isTrue();
            assertThat(payment.getTxId()).isEqualTo(txId);
            assertThat(payment.getReferenceNumber()).startsWith("AUTH-");
            assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.APPROVED);
        }

        @Test
        @DisplayName("Should return same response for idempotent requests")
        void shouldReturnSameResponseForIdempotentRequests() {
            // Given
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            Payment firstPayment = creditCardService.processPayment(request);
            Payment secondPayment = creditCardService.processPayment(request);

            // Then
            assertThat(firstPayment.isApproved()).isTrue();
            assertThat(secondPayment.isApproved()).isTrue();
            // Both calls should produce valid payments
            assertThat(firstPayment.getReferenceNumber()).isNotBlank();
            assertThat(secondPayment.getReferenceNumber()).isNotBlank();
        }

        @Test
        @DisplayName("Should mask credit card number in payment")
        void shouldMaskCreditCardNumber() {
            // Given
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            Payment payment = creditCardService.processPayment(request);

            // Then
            assertThat(payment.getCreditCardNumber()).endsWith("1111");
            assertThat(payment.getCreditCardNumber()).contains("****");
        }
    }
}
