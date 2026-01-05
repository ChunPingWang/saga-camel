package com.ecommerce.logistics.application.service;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.logistics.domain.model.Shipment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogisticsService Unit Tests")
class LogisticsServiceTest {

    private LogisticsService logisticsService;

    @BeforeEach
    void setUp() {
        logisticsService = new LogisticsService();
        ReflectionTestUtils.setField(logisticsService, "failureEnabled", false);
        ReflectionTestUtils.setField(logisticsService, "failureRate", 0.0);
        ReflectionTestUtils.setField(logisticsService, "delayEnabled", false);
        ReflectionTestUtils.setField(logisticsService, "delayMinMs", 0);
        ReflectionTestUtils.setField(logisticsService, "delayMaxMs", 0);
    }

    private NotifyRequest createValidNotifyRequest(UUID txId) {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "userId", "user-123",
                "shippingAddress", "123 Main St",
                "items", List.of(Map.of("sku", "SKU-001", "quantity", 2))
        );
        return NotifyRequest.of(txId, orderId, payload);
    }

    @Nested
    @DisplayName("Schedule Shipment Tests")
    class ScheduleShipmentTests {

        @Test
        @DisplayName("Should successfully schedule shipment")
        void shouldSuccessfullyScheduleShipment() {
            // Given
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            Shipment shipment = logisticsService.scheduleShipment(request);

            // Then
            assertThat(shipment.isScheduled()).isTrue();
            assertThat(shipment.getTxId()).isEqualTo(txId);
            assertThat(shipment.getTrackingNumber()).startsWith("TRK-");
            assertThat(shipment.getStatus()).isEqualTo(Shipment.ShipmentStatus.SCHEDULED);
        }

        @Test
        @DisplayName("Should return same response for idempotent requests")
        void shouldReturnSameResponseForIdempotentRequests() {
            // Given
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            Shipment firstShipment = logisticsService.scheduleShipment(request);
            Shipment secondShipment = logisticsService.scheduleShipment(request);

            // Then
            assertThat(firstShipment.isScheduled()).isTrue();
            assertThat(secondShipment.isScheduled()).isTrue();
            assertThat(firstShipment.getTrackingNumber()).isEqualTo(secondShipment.getTrackingNumber());
        }

        @Test
        @DisplayName("Should fail scheduling when failure simulation is enabled")
        void shouldFailSchedulingWhenFailureSimulationEnabled() {
            // Given
            ReflectionTestUtils.setField(logisticsService, "failureEnabled", true);
            ReflectionTestUtils.setField(logisticsService, "failureRate", 1.0);
            UUID txId = UUID.randomUUID();
            NotifyRequest request = createValidNotifyRequest(txId);

            // When
            Shipment shipment = logisticsService.scheduleShipment(request);

            // Then
            assertThat(shipment.isScheduled()).isFalse();
            assertThat(shipment.getTxId()).isEqualTo(txId);
            assertThat(shipment.getStatus()).isEqualTo(Shipment.ShipmentStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("Cancel Shipment Tests")
    class CancelShipmentTests {

        @Test
        @DisplayName("Should successfully cancel scheduled shipment")
        void shouldSuccessfullyCancelShipment() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            NotifyRequest notifyRequest = createValidNotifyRequest(txId);
            logisticsService.scheduleShipment(notifyRequest);
            RollbackRequest rollbackRequest = RollbackRequest.of(txId, orderId, "Test cancel");

            // When
            RollbackResponse response = logisticsService.cancelShipment(rollbackRequest);

            // Then
            assertThat(response.success()).isTrue();
            assertThat(response.txId()).isEqualTo(txId);
            assertThat(response.message()).isEqualTo("Shipment cancelled successfully");
        }

        @Test
        @DisplayName("Should handle cancel for non-existent shipment")
        void shouldHandleCancelForNonExistentShipment() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            RollbackRequest rollbackRequest = RollbackRequest.of(txId, orderId, "Test cancel");

            // When
            RollbackResponse response = logisticsService.cancelShipment(rollbackRequest);

            // Then
            assertThat(response.success()).isTrue();
            assertThat(response.message()).isEqualTo("No shipment to cancel");
        }

        @Test
        @DisplayName("Should return idempotent response for already cancelled shipment")
        void shouldReturnIdempotentResponseForAlreadyCancelledShipment() {
            // Given
            UUID txId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            NotifyRequest notifyRequest = createValidNotifyRequest(txId);
            logisticsService.scheduleShipment(notifyRequest);
            RollbackRequest rollbackRequest = RollbackRequest.of(txId, orderId, "Test cancel");

            // When
            logisticsService.cancelShipment(rollbackRequest);
            RollbackResponse secondResponse = logisticsService.cancelShipment(rollbackRequest);

            // Then
            assertThat(secondResponse.success()).isTrue();
            assertThat(secondResponse.message()).isEqualTo("Shipment already cancelled");
        }
    }
}
