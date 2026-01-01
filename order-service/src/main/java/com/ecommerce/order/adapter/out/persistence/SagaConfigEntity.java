package com.ecommerce.order.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA entity for saga_config table.
 */
@Entity
@Table(name = "saga_config")
public class SagaConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_type", nullable = false, length = 50)
    private String configType;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Lob
    @Column(name = "config_value", nullable = false)
    private String configValue;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Column(name = "is_pending", nullable = false)
    private Boolean isPending = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public SagaConfigEntity() {
    }

    public SagaConfigEntity(String configType, String configKey, String configValue, boolean isActive, boolean isPending) {
        this.configType = configType;
        this.configKey = configKey;
        this.configValue = configValue;
        this.isActive = isActive;
        this.isPending = isPending;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Boolean getIsPending() {
        return isPending;
    }

    public void setIsPending(Boolean pending) {
        isPending = pending;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
