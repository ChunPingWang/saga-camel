package com.ecommerce.order.adapter.out.http;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServiceClientAdapter Circuit Breaker functionality.
 */
@ExtendWith(MockitoExtension.class)
class ServiceClientAdapterCircuitBreakerTest {

    @Mock
    private RestTemplate restTemplate;

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private ServiceClientAdapter adapter;

    @BeforeEach
    void setUp() {
        // Configure Circuit Breaker with low thresholds for testing
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
        adapter = new ServiceClientAdapter(restTemplate, circuitBreakerRegistry);
    }

    private NotifyRequest createNotifyRequest(UUID txId, UUID orderId) {
        return NotifyRequest.of(txId, orderId, Map.of("totalAmount", 100, "items", 2));
    }

    private RollbackRequest createRollbackRequest(UUID txId, UUID orderId) {
        return RollbackRequest.of(txId, orderId, "Test rollback");
    }

    @Test
    @DisplayName("Circuit Breaker CLOSED - notify calls succeed normally")
    void notifySucceedsWhenCircuitBreakerClosed() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        NotifyRequest request = createNotifyRequest(txId, orderId);
        NotifyResponse expectedResponse = NotifyResponse.success(txId, "Payment processed", "REF-001");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(NotifyResponse.class)))
                .thenReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

        // When
        NotifyResponse response = adapter.notify(ServiceName.CREDIT_CARD, request);

        // Then
        assertTrue(response.success());
        assertEquals("Payment processed", response.message());
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(NotifyResponse.class));

        // Verify Circuit Breaker is still CLOSED
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(ServiceName.CREDIT_CARD.name());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    @DisplayName("Circuit Breaker CLOSED - rollback calls succeed normally")
    void rollbackSucceedsWhenCircuitBreakerClosed() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RollbackRequest request = createRollbackRequest(txId, orderId);
        RollbackResponse expectedResponse = RollbackResponse.success(txId, "Rollback completed");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(RollbackResponse.class)))
                .thenReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

        // When
        RollbackResponse response = adapter.rollback(ServiceName.INVENTORY, request);

        // Then
        assertTrue(response.success());
        assertEquals("Rollback completed", response.message());
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(RollbackResponse.class));
    }

    @Test
    @DisplayName("Circuit Breaker opens after consecutive failures")
    void circuitBreakerOpensAfterConsecutiveFailures() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        NotifyRequest request = createNotifyRequest(txId, orderId);

        // Simulate service failures
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(NotifyResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(ServiceName.LOGISTICS.name());

        // When - make calls until Circuit Breaker opens
        // With minimumNumberOfCalls=2 and failureRateThreshold=50%, we need 2 failures
        for (int i = 0; i < 3; i++) {
            adapter.notify(ServiceName.LOGISTICS, request);
        }

        // Then - Circuit Breaker should be OPEN
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    @DisplayName("Circuit Breaker OPEN - returns fallback without HTTP call")
    void notifyReturnsFallbackWhenCircuitBreakerOpen() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        NotifyRequest request = createNotifyRequest(txId, orderId);

        // Force Circuit Breaker to OPEN state
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(ServiceName.CREDIT_CARD.name());
        circuitBreaker.transitionToOpenState();

        // When
        NotifyResponse response = adapter.notify(ServiceName.CREDIT_CARD, request);

        // Then - should return failure without calling HTTP
        assertFalse(response.success());
        assertTrue(response.message().contains("Circuit breaker is OPEN"));

        // Verify NO HTTP calls were made
        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(NotifyResponse.class));
    }

    @Test
    @DisplayName("Circuit Breaker OPEN - rollback returns fallback without HTTP call")
    void rollbackReturnsFallbackWhenCircuitBreakerOpen() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RollbackRequest request = createRollbackRequest(txId, orderId);

        // Force Circuit Breaker to OPEN state
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(ServiceName.INVENTORY.name());
        circuitBreaker.transitionToOpenState();

        // When
        RollbackResponse response = adapter.rollback(ServiceName.INVENTORY, request);

        // Then - should return failure without calling HTTP
        assertFalse(response.success());
        assertTrue(response.message().contains("Circuit breaker is OPEN"));
        assertTrue(response.message().contains("rollback"));

        // Verify NO HTTP calls were made
        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(RollbackResponse.class));
    }

    @Test
    @DisplayName("Different services have independent Circuit Breakers")
    void differentServicesHaveIndependentCircuitBreakers() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        NotifyRequest request = createNotifyRequest(txId, orderId);

        // Force CREDIT_CARD Circuit Breaker to OPEN
        CircuitBreaker creditCardCB = circuitBreakerRegistry.circuitBreaker(ServiceName.CREDIT_CARD.name());
        creditCardCB.transitionToOpenState();

        // INVENTORY Circuit Breaker should still be CLOSED
        CircuitBreaker inventoryCB = circuitBreakerRegistry.circuitBreaker(ServiceName.INVENTORY.name());

        // When
        NotifyResponse expectedResponse = NotifyResponse.success(txId, "Reserved", "INV-001");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(NotifyResponse.class)))
                .thenReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

        NotifyResponse inventoryResponse = adapter.notify(ServiceName.INVENTORY, request);
        NotifyResponse creditCardResponse = adapter.notify(ServiceName.CREDIT_CARD, request);

        // Then
        assertTrue(inventoryResponse.success()); // INVENTORY should succeed
        assertFalse(creditCardResponse.success()); // CREDIT_CARD should fail (CB OPEN)

        assertEquals(CircuitBreaker.State.OPEN, creditCardCB.getState());
        assertEquals(CircuitBreaker.State.CLOSED, inventoryCB.getState());
    }

    @Test
    @DisplayName("Circuit Breaker records failed calls correctly")
    void circuitBreakerRecordsFailedCalls() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        NotifyRequest request = createNotifyRequest(txId, orderId);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(NotifyResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(ServiceName.LOGISTICS.name());

        // When
        adapter.notify(ServiceName.LOGISTICS, request);

        // Then
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        assertEquals(1, metrics.getNumberOfFailedCalls());
        assertEquals(0, metrics.getNumberOfSuccessfulCalls());
    }

    @Test
    @DisplayName("Circuit Breaker records successful calls correctly")
    void circuitBreakerRecordsSuccessfulCalls() {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        NotifyRequest request = createNotifyRequest(txId, orderId);
        NotifyResponse expectedResponse = NotifyResponse.success(txId, "Success", "REF-001");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(NotifyResponse.class)))
                .thenReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(ServiceName.CREDIT_CARD.name());

        // When
        adapter.notify(ServiceName.CREDIT_CARD, request);

        // Then
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        assertEquals(0, metrics.getNumberOfFailedCalls());
        assertEquals(1, metrics.getNumberOfSuccessfulCalls());
    }
}
