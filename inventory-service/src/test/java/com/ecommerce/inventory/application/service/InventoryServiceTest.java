package com.ecommerce.inventory.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InventoryService Unit Tests")
class InventoryServiceTest {

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService();
        ReflectionTestUtils.setField(inventoryService, "failureEnabled", false);
        ReflectionTestUtils.setField(inventoryService, "failureRate", 0.0);
        ReflectionTestUtils.setField(inventoryService, "delayEnabled", false);
        ReflectionTestUtils.setField(inventoryService, "delayMinMs", 0);
        ReflectionTestUtils.setField(inventoryService, "delayMaxMs", 0);
    }

    private NotifyRequest createValidNotifyRequest(UUID txId) {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "userId", "user-123",
                "items", List.of(
                        Map.of("sku", "SKU-001", "quantity", 2, "unitPrice", 29.99)
                )
        );
        return NotifyRequest.of(txId, orderId, payload);
    }

    @Nested
    @DisplayName("Reserve Inventory Tests")
    class ReserveInventoryTests {

        @Test
        @DisplayName("Should successfully reserve inventory")
        void shouldSuccessfullyReserveInventory() {
            // Given
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            NotifyResponse response = inventoryService.reserveInventory(request);

            // Then
            assertThat(response.success()).isTrue();
            assertThat(response.txId()).isEqualTo(txId);
            assertThat(response.serviceReference()).startsWith("RES-");
            assertThat(response.message()).isEqualTo("Inventory reserved");
        }

        @Test
        @DisplayName("Should return same response for idempotent requests")
        void shouldReturnSameResponseForIdempotentRequests() {
            // Given
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            NotifyResponse firstResponse = inventoryService.reserveInventory(request);
            NotifyResponse secondResponse = inventoryService.reserveInventory(request);

            // Then
            assertThat(firstResponse.success()).isTrue();
            assertThat(secondResponse.success()).isTrue();
            assertThat(firstResponse.serviceReference()).isEqualTo(secondResponse.serviceReference());
        }

        @Test
        @DisplayName("Should fail reservation when failure simulation is enabled")
        void shouldFailReservationWhenFailureSimulationEnabled() {
            // Given
            ReflectionTestUtils.setField(inventoryService, "failureEnabled", true);
            ReflectionTestUtils.setField(inventoryService, "failureRate", 1.0);
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            NotifyResponse response = inventoryService.reserveInventory(request);

            // Then
            assertThat(response.success()).isFalse();
            assertThat(response.txId()).isEqualTo(txId);
            assertThat(response.message()).contains("Out of stock");
        }
    }

    @Nested
    @DisplayName("Release Inventory Tests")
    class ReleaseInventoryTests {

        @Test
        @DisplayName("Should successfully release reserved inventory")
        void shouldSuccessfullyReleaseInventory() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            NotifyRequest notifyRequest = createValidNotifyRequest(txId);
            inventoryService.reserveInventory(notifyRequest);
            RollbackRequest rollbackRequest = RollbackRequest.of(txId, orderId, "Test release");

            // When
            RollbackResponse response = inventoryService.releaseInventory(rollbackRequest);

            // Then
            assertThat(response.success()).isTrue();
            assertThat(response.txId()).isEqualTo(txId);
            assertThat(response.message()).isEqualTo("Inventory released successfully");
        }

        @Test
        @DisplayName("Should handle release for non-existent reservation")
        void shouldHandleReleaseForNonExistentReservation() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            RollbackRequest rollbackRequest = RollbackRequest.of(txId, orderId, "Test release");

            // When
            RollbackResponse response = inventoryService.releaseInventory(rollbackRequest);

            // Then
            assertThat(response.success()).isTrue();
            assertThat(response.message()).isEqualTo("No reservation to release");
        }

        @Test
        @DisplayName("Should return idempotent response for already released inventory")
        void shouldReturnIdempotentResponseForAlreadyReleasedInventory() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            NotifyRequest notifyRequest = createValidNotifyRequest(txId);
            inventoryService.reserveInventory(notifyRequest);
            RollbackRequest rollbackRequest = RollbackRequest.of(txId, orderId, "Test release");

            // When
            inventoryService.releaseInventory(rollbackRequest);
            RollbackResponse secondResponse = inventoryService.releaseInventory(rollbackRequest);

            // Then
            assertThat(secondResponse.success()).isTrue();
            assertThat(secondResponse.message()).isEqualTo("Inventory already released");
        }
    }
}
