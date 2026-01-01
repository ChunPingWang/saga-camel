package com.ecommerce.order.domain.model;

import com.ecommerce.common.domain.ServiceName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServiceConfig value object.
 */
class ServiceConfigTest {

    @Test
    @DisplayName("should create service config with valid data")
    void shouldCreateServiceConfigWithValidData() {
        // When
        ServiceConfig config = new ServiceConfig(
                1,
                ServiceName.CREDIT_CARD,
                "http://localhost:8081/api/v1/credit-card/notify",
                "http://localhost:8081/api/v1/credit-card/rollback"
        );

        // Then
        assertEquals(1, config.order());
        assertEquals(ServiceName.CREDIT_CARD, config.name());
        assertEquals("http://localhost:8081/api/v1/credit-card/notify", config.notifyUrl());
        assertEquals("http://localhost:8081/api/v1/credit-card/rollback", config.rollbackUrl());
    }

    @Test
    @DisplayName("should throw exception when order is less than 1")
    void shouldThrowExceptionWhenOrderIsLessThan1() {
        assertThrows(IllegalArgumentException.class, () ->
                new ServiceConfig(0, ServiceName.CREDIT_CARD, "http://notify", "http://rollback")
        );
    }

    @Test
    @DisplayName("should throw exception when name is null")
    void shouldThrowExceptionWhenNameIsNull() {
        assertThrows(NullPointerException.class, () ->
                new ServiceConfig(1, null, "http://notify", "http://rollback")
        );
    }

    @Test
    @DisplayName("should throw exception when notifyUrl is blank")
    void shouldThrowExceptionWhenNotifyUrlIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                new ServiceConfig(1, ServiceName.CREDIT_CARD, "", "http://rollback")
        );
    }

    @Test
    @DisplayName("should throw exception when rollbackUrl is blank")
    void shouldThrowExceptionWhenRollbackUrlIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                new ServiceConfig(1, ServiceName.CREDIT_CARD, "http://notify", "")
        );
    }

    @Test
    @DisplayName("should create default config from ServiceName")
    void shouldCreateDefaultConfigFromServiceName() {
        // When
        ServiceConfig config = ServiceConfig.defaultFor(ServiceName.INVENTORY, 2);

        // Then
        assertEquals(2, config.order());
        assertEquals(ServiceName.INVENTORY, config.name());
        assertTrue(config.notifyUrl().contains("inventory"));
        assertTrue(config.rollbackUrl().contains("inventory"));
    }
}
