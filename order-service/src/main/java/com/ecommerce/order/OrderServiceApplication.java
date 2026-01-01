package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Order Service - Saga Orchestrator.
 * <p>
 * This service coordinates the distributed transaction flow for order processing,
 * calling downstream services (payment, inventory, logistics) and managing
 * compensation (rollback) on failures.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
