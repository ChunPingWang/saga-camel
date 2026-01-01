package com.ecommerce.order.infrastructure.observability;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Configuration for distributed tracing and request correlation.
 * Adds request IDs to MDC for structured logging.
 */
@Configuration
public class TracingConfig {

    public static final String TX_ID_MDC_KEY = "txId";
    public static final String ORDER_ID_MDC_KEY = "orderId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    /**
     * Filter to add request ID to MDC for logging correlation.
     * Uses X-Request-ID header if present, otherwise generates one.
     */
    @Bean
    public OncePerRequestFilter requestCorrelationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {
                String requestId = request.getHeader(REQUEST_ID_HEADER);
                if (requestId == null || requestId.isBlank()) {
                    requestId = UUID.randomUUID().toString().substring(0, 8);
                }

                try {
                    MDC.put(REQUEST_ID_MDC_KEY, requestId);
                    response.setHeader(REQUEST_ID_HEADER, requestId);
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.remove(REQUEST_ID_MDC_KEY);
                }
            }
        };
    }

    /**
     * Helper to set transaction context in MDC.
     * Should be called when starting saga processing.
     */
    public static void setTransactionContext(UUID txId, UUID orderId) {
        if (txId != null) {
            MDC.put(TX_ID_MDC_KEY, txId.toString());
        }
        if (orderId != null) {
            MDC.put(ORDER_ID_MDC_KEY, orderId.toString());
        }
    }

    /**
     * Helper to set transaction context in MDC (String version).
     */
    public static void setTransactionContext(String txId, String orderId) {
        if (txId != null && !txId.isBlank()) {
            MDC.put(TX_ID_MDC_KEY, txId);
        }
        if (orderId != null && !orderId.isBlank()) {
            MDC.put(ORDER_ID_MDC_KEY, orderId);
        }
    }

    /**
     * Helper to clear transaction context from MDC.
     * Should be called when saga processing completes.
     */
    public static void clearTransactionContext() {
        MDC.remove(TX_ID_MDC_KEY);
        MDC.remove(ORDER_ID_MDC_KEY);
    }

    /**
     * Get current txId from MDC.
     */
    public static String getCurrentTxId() {
        return MDC.get(TX_ID_MDC_KEY);
    }

    /**
     * Get current orderId from MDC.
     */
    public static String getCurrentOrderId() {
        return MDC.get(ORDER_ID_MDC_KEY);
    }
}
