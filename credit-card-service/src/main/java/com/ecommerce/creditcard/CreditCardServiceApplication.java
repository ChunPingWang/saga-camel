package com.ecommerce.creditcard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Credit Card Service - Payment Processing.
 * <p>
 * Handles payment processing and refunds as part of the saga workflow.
 * Provides idempotent notify and rollback APIs.
 */
@SpringBootApplication
public class CreditCardServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditCardServiceApplication.class, args);
    }
}
