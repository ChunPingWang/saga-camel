package com.ecommerce.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Logistics Service - Shipment Scheduling.
 * <p>
 * Handles shipment scheduling and cancellation as part of the saga workflow.
 * Provides idempotent notify and rollback APIs.
 */
@SpringBootApplication
public class LogisticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsServiceApplication.class, args);
    }
}
