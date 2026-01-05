package com.ecommerce.inventory.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.inventory.domain.model.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    }

    private NotifyRequest createValidNotifyRequest(UUID txId) {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "userId", "user-123",
                "items", List.of(
                        Map.of("productId", "SKU-001", "productName", "Test Item", "quantity", 2)
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
            Reservation reservation = inventoryService.reserveInventory(request);

            // Then
            assertThat(reservation.isReserved()).isTrue();
            assertThat(reservation.getTxId()).isEqualTo(txId);
            assertThat(reservation.getReferenceNumber()).startsWith("RES-");
            assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.RESERVED);
        }

        @Test
        @DisplayName("Should return valid reservations for multiple requests")
        void shouldReturnValidReservationsForMultipleRequests() {
            // Given
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            Reservation firstReservation = inventoryService.reserveInventory(request);
            Reservation secondReservation = inventoryService.reserveInventory(request);

            // Then
            assertThat(firstReservation.isReserved()).isTrue();
            assertThat(secondReservation.isReserved()).isTrue();
            assertThat(firstReservation.getReferenceNumber()).isNotBlank();
            assertThat(secondReservation.getReferenceNumber()).isNotBlank();
        }

        @Test
        @DisplayName("Should include items in reservation")
        void shouldIncludeItemsInReservation() {
            // Given
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            Reservation reservation = inventoryService.reserveInventory(request);

            // Then
            assertThat(reservation.getItems()).hasSize(1);
            assertThat(reservation.getItems().get(0).productId()).isEqualTo("SKU-001");
            assertThat(reservation.getItems().get(0).quantity()).isEqualTo(2);
        }
    }
}
