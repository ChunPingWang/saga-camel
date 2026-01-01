package com.ecommerce.order.infrastructure.camel;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.domain.model.ServiceConfig;
import com.ecommerce.order.infrastructure.camel.processor.PostNotifyProcessor;
import com.ecommerce.order.infrastructure.camel.processor.PreNotifyProcessor;
import com.ecommerce.order.infrastructure.camel.processor.RollbackProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Camel route for saga orchestration.
 * Processes services in configured order and triggers rollback on failure.
 */
@Component
public class OrderSagaRoute extends RouteBuilder {

    private static final List<ServiceConfig> DEFAULT_SERVICE_ORDER = List.of(
            new ServiceConfig(1, ServiceName.CREDIT_CARD,
                    "http://localhost:8081/api/v1/credit-card/notify",
                    "http://localhost:8081/api/v1/credit-card/rollback"),
            new ServiceConfig(2, ServiceName.INVENTORY,
                    "http://localhost:8082/api/v1/inventory/notify",
                    "http://localhost:8082/api/v1/inventory/rollback"),
            new ServiceConfig(3, ServiceName.LOGISTICS,
                    "http://localhost:8083/api/v1/logistics/notify",
                    "http://localhost:8083/api/v1/logistics/rollback")
    );

    private final PreNotifyProcessor preNotifyProcessor;
    private final PostNotifyProcessor postNotifyProcessor;
    private final RollbackProcessor rollbackProcessor;
    private final ServiceClientPort serviceClientPort;
    private final WebSocketPort webSocketPort;
    private final ObjectMapper objectMapper;

    public OrderSagaRoute(PreNotifyProcessor preNotifyProcessor,
                          PostNotifyProcessor postNotifyProcessor,
                          RollbackProcessor rollbackProcessor,
                          ServiceClientPort serviceClientPort,
                          WebSocketPort webSocketPort,
                          ObjectMapper objectMapper) {
        this.preNotifyProcessor = preNotifyProcessor;
        this.postNotifyProcessor = postNotifyProcessor;
        this.rollbackProcessor = rollbackProcessor;
        this.serviceClientPort = serviceClientPort;
        this.webSocketPort = webSocketPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() throws Exception {
        // Error handler - trigger rollback on exception
        onException(Exception.class)
                .handled(true)
                .log("Exception in saga: ${exception.message}")
                .process(exchange -> {
                    Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    exchange.setProperty("errorMessage", exception.getMessage());
                    exchange.setProperty("sagaFailed", true);
                })
                .to("direct:rollback");

        // Main saga route
        from("direct:order-saga")
                .routeId("order-saga-route")
                .log("Starting saga for order: ${body}")
                .process(exchange -> {
                    // Parse the event payload
                    String jsonBody = exchange.getMessage().getBody(String.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = objectMapper.readValue(jsonBody, Map.class);

                    // Extract transaction details
                    String txId = (String) event.get("txId");
                    String orderId = (String) event.get("orderId");

                    exchange.setProperty("txId", UUID.fromString(txId));
                    exchange.setProperty("orderId", UUID.fromString(orderId));
                    exchange.setProperty("payload", event);
                    exchange.setProperty("sagaFailed", false);
                    exchange.setProperty("successfulServices", new ArrayList<ServiceName>());

                    // Get service order from header or use default
                    @SuppressWarnings("unchecked")
                    List<ServiceConfig> serviceOrder = exchange.getMessage()
                            .getHeader("serviceOrder", List.class);
                    if (serviceOrder == null) {
                        serviceOrder = DEFAULT_SERVICE_ORDER;
                    }
                    exchange.setProperty("serviceOrder", serviceOrder);
                })
                // Process each service in order
                .loop(simple("${exchangeProperty.serviceOrder.size}"))
                    .process(exchange -> {
                        int index = exchange.getProperty(Exchange.LOOP_INDEX, Integer.class);
                        @SuppressWarnings("unchecked")
                        List<ServiceConfig> serviceOrder = exchange.getProperty("serviceOrder", List.class);
                        ServiceConfig config = serviceOrder.get(index);
                        exchange.setProperty("currentService", config.name());
                        exchange.setProperty("currentServiceConfig", config);
                    })
                    // Check if saga has failed
                    .choice()
                        .when(simple("${exchangeProperty.sagaFailed} == true"))
                            .log("Saga failed, skipping remaining services")
                        .otherwise()
                            // Pre-notify: prepare request and record status
                            .process(preNotifyProcessor)
                            // Call the service
                            .process(exchange -> {
                                NotifyRequest request = exchange.getMessage().getBody(NotifyRequest.class);
                                ServiceName serviceName = exchange.getProperty("currentService", ServiceName.class);

                                NotifyResponse response = serviceClientPort.notify(serviceName, request);
                                exchange.getMessage().setBody(response);
                            })
                            // Post-notify: process response
                            .process(postNotifyProcessor)
                    .end()
                .end()
                // Check if saga completed successfully or needs rollback
                .choice()
                    .when(simple("${exchangeProperty.sagaFailed} == true"))
                        .log("Saga failed, triggering rollback")
                        .to("direct:rollback")
                    .otherwise()
                        .log("Saga completed successfully")
                        .process(exchange -> {
                            UUID txId = exchange.getProperty("txId", UUID.class);
                            UUID orderId = exchange.getProperty("orderId", UUID.class);
                            webSocketPort.sendCompleted(txId, orderId);
                        })
                .end();

        // Rollback route
        from("direct:rollback")
                .routeId("rollback-route")
                .log("Executing rollback for txId=${exchangeProperty.txId}")
                .process(rollbackProcessor)
                .log("Rollback completed for txId=${exchangeProperty.txId}");
    }
}
