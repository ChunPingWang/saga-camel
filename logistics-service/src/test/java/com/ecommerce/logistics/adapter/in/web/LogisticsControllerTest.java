package com.ecommerce.logistics.adapter.in.web;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.logistics.application.port.in.RollbackShipmentUseCase;
import com.ecommerce.logistics.application.port.in.ScheduleShipmentUseCase;
import com.ecommerce.logistics.domain.model.Shipment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogisticsController.class)
@DisplayName("LogisticsController Contract Tests")
class LogisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScheduleShipmentUseCase scheduleShipmentUseCase;

    @MockBean
    private RollbackShipmentUseCase rollbackShipmentUseCase;

    @Test
    @DisplayName("POST /api/v1/logistics/notify - should return success response")
    void notify_shouldReturnSuccessResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "shippingAddress", "123 Main St",
                "items", List.of(Map.of("sku", "SKU-001", "quantity", 2))
        );
        NotifyRequest request = NotifyRequest.of(txId, orderId, payload);

        Shipment mockShipment = new Shipment(txId, orderId, "TRK-12345678",
                Shipment.ShipmentStatus.SCHEDULED, LocalDateTime.now().plusDays(3), LocalDateTime.now());

        when(scheduleShipmentUseCase.scheduleShipment(any(NotifyRequest.class))).thenReturn(mockShipment);

        // When & Then
        mockMvc.perform(post("/api/v1/logistics/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.txId").value(txId.toString()))
                .andExpect(jsonPath("$.serviceReference").value("TRK-12345678"));
    }

    @Test
    @DisplayName("POST /api/v1/logistics/notify - should return failure response")
    void notify_shouldReturnFailureResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "shippingAddress", "123 Main St"
        );
        NotifyRequest request = NotifyRequest.of(txId, orderId, payload);

        Shipment mockShipment = new Shipment(txId, orderId, null,
                Shipment.ShipmentStatus.CANCELLED, null, LocalDateTime.now());

        when(scheduleShipmentUseCase.scheduleShipment(any(NotifyRequest.class))).thenReturn(mockShipment);

        // When & Then
        mockMvc.perform(post("/api/v1/logistics/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/logistics/rollback - should return success response")
    void rollback_shouldReturnSuccessResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RollbackRequest request = RollbackRequest.of(txId, orderId, "Cancel shipment");
        RollbackResponse expectedResponse = RollbackResponse.success(txId, "Shipment cancelled successfully");

        when(rollbackShipmentUseCase.cancelShipment(any(RollbackRequest.class))).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/logistics/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.txId").value(txId.toString()))
                .andExpect(jsonPath("$.message").value("Shipment cancelled successfully"));
    }

    @Test
    @DisplayName("POST /api/v1/logistics/rollback - should return failure response")
    void rollback_shouldReturnFailureResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RollbackRequest request = RollbackRequest.of(txId, orderId, "Cancel shipment");
        RollbackResponse expectedResponse = RollbackResponse.failure(txId, "Cannot cancel shipment");

        when(rollbackShipmentUseCase.cancelShipment(any(RollbackRequest.class))).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/logistics/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot cancel shipment"));
    }
}
