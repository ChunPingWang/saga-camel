package com.ecommerce.inventory.adapter.in.web;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.inventory.application.port.in.ReserveInventoryUseCase;
import com.ecommerce.inventory.application.port.in.RollbackReservationUseCase;
import com.ecommerce.inventory.domain.model.Reservation;
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

@WebMvcTest(InventoryController.class)
@DisplayName("InventoryController Contract Tests")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReserveInventoryUseCase reserveInventoryUseCase;

    @MockBean
    private RollbackReservationUseCase rollbackReservationUseCase;

    @Test
    @DisplayName("POST /api/v1/inventory/notify - should return success response")
    void notify_shouldReturnSuccessResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "items", List.of(Map.of("productId", "SKU-001", "productName", "Item", "quantity", 2))
        );
        NotifyRequest request = NotifyRequest.of(txId, orderId, payload);

        List<Reservation.ReservedItem> items = List.of(new Reservation.ReservedItem("SKU-001", "Item", 2));
        Reservation mockReservation = new Reservation(txId, orderId, items,
                Reservation.ReservationStatus.RESERVED, "RES-12345678", LocalDateTime.now());

        when(reserveInventoryUseCase.reserveInventory(any(NotifyRequest.class))).thenReturn(mockReservation);

        // When & Then
        mockMvc.perform(post("/api/v1/inventory/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.txId").value(txId.toString()))
                .andExpect(jsonPath("$.serviceReference").value("RES-12345678"));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/notify - should return failure response for out of stock")
    void notify_shouldReturnFailureResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "items", List.of(Map.of("productId", "SKU-001", "productName", "Item", "quantity", 2))
        );
        NotifyRequest request = NotifyRequest.of(txId, orderId, payload);

        List<Reservation.ReservedItem> items = List.of(new Reservation.ReservedItem("SKU-001", "Item", 2));
        Reservation mockReservation = new Reservation(txId, orderId, items,
                Reservation.ReservationStatus.OUT_OF_STOCK, null, LocalDateTime.now());

        when(reserveInventoryUseCase.reserveInventory(any(NotifyRequest.class))).thenReturn(mockReservation);

        // When & Then
        mockMvc.perform(post("/api/v1/inventory/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/rollback - should return success response")
    void rollback_shouldReturnSuccessResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RollbackRequest request = RollbackRequest.of(txId, orderId, "Release inventory");
        RollbackResponse expectedResponse = RollbackResponse.success(txId, "Inventory released successfully");

        when(rollbackReservationUseCase.releaseInventory(any(RollbackRequest.class))).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/inventory/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.txId").value(txId.toString()))
                .andExpect(jsonPath("$.message").value("Inventory released successfully"));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/rollback - should return failure response")
    void rollback_shouldReturnFailureResponse() throws Exception {
        // Given
        UUID txId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RollbackRequest request = RollbackRequest.of(txId, orderId, "Release inventory");
        RollbackResponse expectedResponse = RollbackResponse.failure(txId, "Cannot release reservation");

        when(rollbackReservationUseCase.releaseInventory(any(RollbackRequest.class))).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/inventory/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot release reservation"));
    }
}
