package com.ecommerce.order.application.service;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.order.application.port.out.SagaConfigPort;
import com.ecommerce.order.domain.model.ServiceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaConfigService Tests")
class SagaConfigServiceTest {

    @Mock
    private SagaConfigPort sagaConfigPort;

    private SagaConfigService configService;

    @BeforeEach
    void setUp() {
        configService = new SagaConfigService(sagaConfigPort);
    }

    @Nested
    @DisplayName("getActiveConfig")
    class GetActiveConfig {

        @Test
        @DisplayName("should return active configuration")
        void shouldReturnActiveConfiguration() {
            // Given
            List<ServiceConfig> activeConfigs = List.of(
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 1, 30, true),
                    ServiceConfig.of(ServiceName.INVENTORY, 2, 60, true),
                    ServiceConfig.of(ServiceName.LOGISTICS, 3, 120, true)
            );
            when(sagaConfigPort.findActiveConfigs()).thenReturn(activeConfigs);

            // When
            List<ServiceConfig> result = configService.getActiveConfig();

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).containsExactlyElementsOf(activeConfigs);
        }

        @Test
        @DisplayName("should return empty list when no active config")
        void shouldReturnEmptyListWhenNoActiveConfig() {
            // Given
            when(sagaConfigPort.findActiveConfigs()).thenReturn(List.of());

            // When
            List<ServiceConfig> result = configService.getActiveConfig();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPendingConfig")
    class GetPendingConfig {

        @Test
        @DisplayName("should return pending configuration")
        void shouldReturnPendingConfiguration() {
            // Given
            List<ServiceConfig> pendingConfigs = List.of(
                    ServiceConfig.of(ServiceName.INVENTORY, 1, 45, false),
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 2, 30, false),
                    ServiceConfig.of(ServiceName.LOGISTICS, 3, 120, false)
            );
            when(sagaConfigPort.findPendingConfigs()).thenReturn(pendingConfigs);

            // When
            List<ServiceConfig> result = configService.getPendingConfig();

            // Then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).name()).isEqualTo(ServiceName.INVENTORY);
        }
    }

    @Nested
    @DisplayName("updatePendingConfig")
    class UpdatePendingConfig {

        @Test
        @DisplayName("should update pending configuration")
        void shouldUpdatePendingConfiguration() {
            // Given
            List<ServiceConfig> newConfig = List.of(
                    ServiceConfig.of(ServiceName.INVENTORY, 1, 45, false),
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 2, 30, false),
                    ServiceConfig.of(ServiceName.LOGISTICS, 3, 90, false)
            );

            // When
            configService.updatePendingConfig(newConfig);

            // Then
            verify(sagaConfigPort).savePendingConfigs(newConfig);
        }

        @Test
        @DisplayName("should validate service order uniqueness")
        void shouldValidateServiceOrderUniqueness() {
            // Given - duplicate order
            List<ServiceConfig> invalidConfig = List.of(
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 1, 30, false),
                    ServiceConfig.of(ServiceName.INVENTORY, 1, 60, false)  // Same order
            );

            // When/Then
            assertThatThrownBy(() -> configService.updatePendingConfig(invalidConfig))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("order");
        }

        @Test
        @DisplayName("should validate timeout is positive")
        void shouldValidateTimeoutIsPositive() {
            // Given - negative timeout
            List<ServiceConfig> invalidConfig = List.of(
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 1, -5, false)
            );

            // When/Then
            assertThatThrownBy(() -> configService.updatePendingConfig(invalidConfig))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("timeout");
        }
    }

    @Nested
    @DisplayName("applyPendingConfig")
    class ApplyPendingConfig {

        @Test
        @DisplayName("should apply pending config as active")
        void shouldApplyPendingConfigAsActive() {
            // Given
            List<ServiceConfig> pendingConfigs = List.of(
                    ServiceConfig.of(ServiceName.INVENTORY, 1, 45, false),
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 2, 30, false),
                    ServiceConfig.of(ServiceName.LOGISTICS, 3, 120, false)
            );
            when(sagaConfigPort.findPendingConfigs()).thenReturn(pendingConfigs);

            // When
            configService.applyPendingConfig();

            // Then
            verify(sagaConfigPort).activatePendingConfigs();
        }

        @Test
        @DisplayName("should throw when no pending config exists")
        void shouldThrowWhenNoPendingConfigExists() {
            // Given
            when(sagaConfigPort.findPendingConfigs()).thenReturn(List.of());

            // When/Then
            assertThatThrownBy(() -> configService.applyPendingConfig())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("pending");
        }
    }

    @Nested
    @DisplayName("discardPendingConfig")
    class DiscardPendingConfig {

        @Test
        @DisplayName("should discard pending configuration")
        void shouldDiscardPendingConfiguration() {
            // When
            configService.discardPendingConfig();

            // Then
            verify(sagaConfigPort).deletePendingConfigs();
        }
    }

    @Nested
    @DisplayName("getServiceTimeout")
    class GetServiceTimeout {

        @Test
        @DisplayName("should return timeout for service from active config")
        void shouldReturnTimeoutForServiceFromActiveConfig() {
            // Given
            List<ServiceConfig> activeConfigs = List.of(
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 1, 30, true),
                    ServiceConfig.of(ServiceName.INVENTORY, 2, 60, true)
            );
            when(sagaConfigPort.findActiveConfigs()).thenReturn(activeConfigs);

            // When
            int timeout = configService.getServiceTimeout(ServiceName.INVENTORY);

            // Then
            assertThat(timeout).isEqualTo(60);
        }

        @Test
        @DisplayName("should return default timeout when service not configured")
        void shouldReturnDefaultTimeoutWhenServiceNotConfigured() {
            // Given
            when(sagaConfigPort.findActiveConfigs()).thenReturn(List.of());

            // When
            int timeout = configService.getServiceTimeout(ServiceName.LOGISTICS);

            // Then
            assertThat(timeout).isEqualTo(120); // Default timeout
        }
    }

    @Nested
    @DisplayName("getServiceOrder")
    class GetServiceOrder {

        @Test
        @DisplayName("should return services in configured order")
        void shouldReturnServicesInConfiguredOrder() {
            // Given
            List<ServiceConfig> activeConfigs = List.of(
                    ServiceConfig.of(ServiceName.INVENTORY, 1, 60, true),
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 2, 30, true),
                    ServiceConfig.of(ServiceName.LOGISTICS, 3, 120, true)
            );
            when(sagaConfigPort.findActiveConfigs()).thenReturn(activeConfigs);

            // When
            List<ServiceName> order = configService.getServiceOrder();

            // Then
            assertThat(order).containsExactly(
                    ServiceName.INVENTORY,
                    ServiceName.CREDIT_CARD,
                    ServiceName.LOGISTICS
            );
        }
    }

    @Nested
    @DisplayName("getTimeouts")
    class GetTimeouts {

        @Test
        @DisplayName("should return map of service timeouts")
        void shouldReturnMapOfServiceTimeouts() {
            // Given
            List<ServiceConfig> activeConfigs = List.of(
                    ServiceConfig.of(ServiceName.CREDIT_CARD, 1, 30, true),
                    ServiceConfig.of(ServiceName.INVENTORY, 2, 60, true),
                    ServiceConfig.of(ServiceName.LOGISTICS, 3, 120, true)
            );
            when(sagaConfigPort.findActiveConfigs()).thenReturn(activeConfigs);

            // When
            Map<ServiceName, Integer> timeouts = configService.getTimeouts();

            // Then
            assertThat(timeouts)
                    .containsEntry(ServiceName.CREDIT_CARD, 30)
                    .containsEntry(ServiceName.INVENTORY, 60)
                    .containsEntry(ServiceName.LOGISTICS, 120);
        }
    }
}
