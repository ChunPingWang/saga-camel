package com.ecommerce.order.infrastructure.config;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Apache Camel configuration.
 */
@Configuration
public class CamelConfig {

    @Bean
    public CamelContextConfiguration camelContextConfiguration() {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                // Configure Camel before startup
                camelContext.setStreamCaching(true);
                camelContext.setUseMDCLogging(true);
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // Post-startup configuration
            }
        };
    }
}
