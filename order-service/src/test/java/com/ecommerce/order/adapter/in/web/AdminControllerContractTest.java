package com.ecommerce.order.adapter.in.web;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.adapter.in.web.dto.ServiceConfigDto;
import com.ecommerce.order.adapter.in.web.dto.ServiceConfigListRequest;
import com.ecommerce.order.application.service.SagaConfigService;
import com.ecommerce.order.domain.model.ServiceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@DisplayName("AdminController Contract Tests")
class AdminControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SagaConfigService sagaConfigService;

    @Nested
    @DisplayName("GET /api/v1/admin/config/active")
    class GetActiveConfig {

        @Test
        @DisplayName("should return 200 with active configuration")
        void shouldReturn200WithActiveConfiguration() throws Exception {
            // Given
            List<ServiceConfig> activeConfigs = List.of(
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 1, 30, true),
                    ServiceConfig.of(ServiceName.INVENTORY, 2, 60, true),
                    ServiceConfig.of(ServiceName.LOGISTICS, 3, 120, true)
            );
            when(sagaConfigService.getActiveConfig()).thenReturn(activeConfigs);

            // When/Then
            mockMvc.perform(get("/api/v1/admin/config/active")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configs").isArray())
                    .andExpect(jsonPath("$.configs.length()").value(3))
                    .andExpect(jsonPath("$.configs[0].serviceName").value("CREDIT_CARD"))
                    .andExpect(jsonPath("$.configs[0].order").value(1))
                    .andExpect(jsonPath("$.configs[0].timeoutSeconds").value(30));
        }

        @Test
        @DisplayName("should return 200 with empty array when no config")
        void shouldReturn200WithEmptyArrayWhenNoConfig() throws Exception {
            // Given
            when(sagaConfigService.getActiveConfig()).thenReturn(List.of());

            // When/Then
            mockMvc.perform(get("/api/v1/admin/config/active")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configs").isArray())
                    .andExpect(jsonPath("$.configs.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/config/pending")
    class GetPendingConfig {

        @Test
        @DisplayName("should return 200 with pending configuration")
        void shouldReturn200WithPendingConfiguration() throws Exception {
            // Given
            List<ServiceConfig> pendingConfigs = List.of(
                    ServiceConfig.of(ServiceName.INVENTORY, 1, 45, false),
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 2, 30, false)
            );
            when(sagaConfigService.getPendingConfig()).thenReturn(pendingConfigs);

            // When/Then
            mockMvc.perform(get("/api/v1/admin/config/pending")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configs").isArray())
                    .andExpect(jsonPath("$.configs.length()").value(2))
                    .andExpect(jsonPath("$.configs[0].serviceName").value("INVENTORY"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/admin/config/pending")
    class UpdatePendingConfig {

        @Test
        @DisplayName("should return 200 when config updated successfully")
        void shouldReturn200WhenConfigUpdatedSuccessfully() throws Exception {
            // Given
            ServiceConfigListRequest request = new ServiceConfigListRequest(List.of(
                    new ServiceConfigDto("CREDIT_CARD", 1, 30),
                    new ServiceConfigDto("INVENTORY", 2, 60),
                    new ServiceConfigDto("LOGISTICS", 3, 120)
            ));

            // When/Then
            mockMvc.perform(put("/api/v1/admin/config/pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Pending configuration updated"));

            verify(sagaConfigService).updatePendingConfig(any());
        }

        @Test
        @DisplayName("should return 400 when config is invalid")
        void shouldReturn400WhenConfigIsInvalid() throws Exception {
            // Given
            ServiceConfigListRequest request = new ServiceConfigListRequest(List.of(
                    new ServiceConfigDto("CREDIT_CARD", 1, 30),
                    new ServiceConfigDto("INVENTORY", 1, 60)  // Duplicate order
            ));
            doThrow(new IllegalArgumentException("Duplicate order"))
                    .when(sagaConfigService).updatePendingConfig(any());

            // When/Then
            mockMvc.perform(put("/api/v1/admin/config/pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/config/apply")
    class ApplyPendingConfig {

        @Test
        @DisplayName("should return 200 when config applied successfully")
        void shouldReturn200WhenConfigAppliedSuccessfully() throws Exception {
            // When/Then
            mockMvc.perform(post("/api/v1/admin/config/apply")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Configuration applied"));

            verify(sagaConfigService).applyPendingConfig();
        }

        @Test
        @DisplayName("should return 400 when no pending config exists")
        void shouldReturn400WhenNoPendingConfigExists() throws Exception {
            // Given
            doThrow(new IllegalStateException("No pending config"))
                    .when(sagaConfigService).applyPendingConfig();

            // When/Then
            mockMvc.perform(post("/api/v1/admin/config/apply")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/admin/config/pending")
    class DiscardPendingConfig {

        @Test
        @DisplayName("should return 200 when config discarded successfully")
        void shouldReturn200WhenConfigDiscardedSuccessfully() throws Exception {
            // When/Then
            mockMvc.perform(delete("/api/v1/admin/config/pending")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Pending configuration discarded"));

            verify(sagaConfigService).discardPendingConfig();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/config/timeouts")
    class GetTimeouts {

        @Test
        @DisplayName("should return 200 with timeout map")
        void shouldReturn200WithTimeoutMap() throws Exception {
            // Given
            when(sagaConfigService.getTimeouts()).thenReturn(java.util.Map.of(
                    ServiceName.CREDIT_CARD, 30,
                    ServiceName.INVENTORY, 60,
                    ServiceName.LOGISTICS, 120
            ));

            // When/Then
            mockMvc.perform(get("/api/v1/admin/config/timeouts")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.CREDIT_CARD").value(30))
                    .andExpect(jsonPath("$.INVENTORY").value(60))
                    .andExpect(jsonPath("$.LOGISTICS").value(120));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/config/order")
    class GetServiceOrder {

        @Test
        @DisplayName("should return 200 with service order")
        void shouldReturn200WithServiceOrder() throws Exception {
            // Given
            when(sagaConfigService.getServiceOrder()).thenReturn(List.of(
                    ServiceName.CREDIT_CARD,
                    ServiceName.INVENTORY,
                    ServiceName.LOGISTICS
            ));

            // When/Then
            mockMvc.perform(get("/api/v1/admin/config/order")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.order").isArray())
                    .andExpect(jsonPath("$.order[0]").value("CREDIT_CARD"))
                    .andExpect(jsonPath("$.order[1]").value("INVENTORY"))
                    .andExpect(jsonPath("$.order[2]").value("LOGISTICS"));
        }
    }
}
