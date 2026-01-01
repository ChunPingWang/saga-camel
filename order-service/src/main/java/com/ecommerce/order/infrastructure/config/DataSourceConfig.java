package com.ecommerce.order.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Data source and JPA configuration.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.ecommerce.order.adapter.out.persistence")
@EnableTransactionManagement
public class DataSourceConfig {
    // H2 datasource is auto-configured by Spring Boot
    // Additional configuration can be added here if needed
}
