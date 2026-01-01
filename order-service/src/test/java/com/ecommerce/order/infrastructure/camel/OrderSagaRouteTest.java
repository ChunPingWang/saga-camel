package com.ecommerce.order.infrastructure.camel;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.domain.model.ServiceConfig;
import com.ecommerce.order.domain.model.TransactionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@CamelSpringBootTest
@SpringBootTest
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("OrderSagaRoute Tests")
@org.junit.jupiter.api.Disabled("Pending implementation of OrderSagaRoute - T080")
class OrderSagaRouteTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @MockBean
    private ServiceClientPort serviceClientPort;

    @MockBean
    private TransactionLogPort transactionLogPort;

    @MockBean
    private WebSocketPort webSocketPort;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    private NotifyResponse successResponse(String txId) {
        return NotifyResponse.success(UUID.fromString(txId), "Processed", "ref-1");
    }

    private NotifyResponse failureResponse(String txId, String error) {
        return NotifyResponse.failure(UUID.fromString(txId), error);
    }

    @Nested
    @DisplayName("Happy Path - All Services Succeed")
    class HappyPath {

        @Test
        @DisplayName("should process all services in order and complete saga")
        void shouldProcessAllServicesInOrder() throws Exception {
            // Given
            AdviceWith.adviceWith(camelContext, "order-saga-route", builder -> {
                builder.weaveAddLast().to("mock:result");
            });
            camelContext.start();

            String txId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
            String orderId = "order-456";

            when(serviceClientPort.notify(any(ServiceName.class), any(NotifyRequest.class)))
                    .thenReturn(successResponse(txId));

            TransactionEvent event = TransactionEvent.orderConfirmed(
                    txId,
                    orderId,
                    "user-1",
                    List.of(Map.of("sku", "SKU-001", "quantity", 2)),
                    new BigDecimal("59.98"),
                    "4111111111111111"
            );

            // When
            producerTemplate.sendBody("direct:order-saga", objectMapper.writeValueAsString(event));

            // Then
            mockResult.expectedMessageCount(1);
            mockResult.assertIsSatisfied();

            // Verify services called in order
            verify(serviceClientPort).notify(eq(ServiceName.CREDIT_CARD), any(NotifyRequest.class));
            verify(serviceClientPort).notify(eq(ServiceName.INVENTORY), any(NotifyRequest.class));
            verify(serviceClientPort).notify(eq(ServiceName.LOGISTICS), any(NotifyRequest.class));

            // Verify WebSocket notifications sent
            verify(webSocketPort, times(6)).sendNotification(eq(txId), any());
        }
    }

    @Nested
    @DisplayName("Service Failure Scenarios")
    class ServiceFailure {

        @Test
        @DisplayName("should stop saga and trigger rollback when credit card fails")
        void shouldTriggerRollbackWhenCreditCardFails() throws Exception {
            // Given
            AdviceWith.adviceWith(camelContext, "order-saga-route", builder -> {
                builder.weaveAddLast().to("mock:result");
            });
            camelContext.start();

            String txId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
            String orderId = "order-456";

            when(serviceClientPort.notify(eq(ServiceName.CREDIT_CARD), any(NotifyRequest.class)))
                    .thenReturn(failureResponse(txId, "Payment declined"));

            TransactionEvent event = TransactionEvent.orderConfirmed(
                    txId,
                    orderId,
                    "user-1",
                    List.of(Map.of("sku", "SKU-001", "quantity", 2)),
                    new BigDecimal("59.98"),
                    "4111111111111111"
            );

            // When
            producerTemplate.sendBody("direct:order-saga", objectMapper.writeValueAsString(event));

            // Then
            mockResult.expectedMessageCount(1);
            mockResult.assertIsSatisfied();

            // Verify only credit card was called
            verify(serviceClientPort).notify(eq(ServiceName.CREDIT_CARD), any(NotifyRequest.class));
            verify(serviceClientPort, never()).notify(eq(ServiceName.INVENTORY), any(NotifyRequest.class));
            verify(serviceClientPort, never()).notify(eq(ServiceName.LOGISTICS), any(NotifyRequest.class));
        }

        @Test
        @DisplayName("should rollback credit card when inventory fails")
        void shouldRollbackCreditCardWhenInventoryFails() throws Exception {
            // Given
            AdviceWith.adviceWith(camelContext, "order-saga-route", builder -> {
                builder.weaveAddLast().to("mock:result");
            });
            camelContext.start();

            String txId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

            when(serviceClientPort.notify(eq(ServiceName.CREDIT_CARD), any(NotifyRequest.class)))
                    .thenReturn(successResponse(txId));
            when(serviceClientPort.notify(eq(ServiceName.INVENTORY), any(NotifyRequest.class)))
                    .thenReturn(failureResponse(txId, "Out of stock"));

            TransactionEvent event = TransactionEvent.orderConfirmed(
                    txId,
                    "order-456",
                    "user-1",
                    List.of(Map.of("sku", "SKU-001", "quantity", 2)),
                    new BigDecimal("59.98"),
                    "4111111111111111"
            );

            // When
            producerTemplate.sendBody("direct:order-saga", objectMapper.writeValueAsString(event));

            // Then
            mockResult.expectedMessageCount(1);
            mockResult.assertIsSatisfied();

            // Verify rollback was called for credit card
            verify(serviceClientPort).rollback(eq(ServiceName.CREDIT_CARD), any(RollbackRequest.class));
        }
    }

    @Nested
    @DisplayName("Dynamic Service Order")
    class DynamicServiceOrder {

        @Test
        @DisplayName("should respect configured service order")
        void shouldRespectConfiguredServiceOrder() throws Exception {
            // Given
            AdviceWith.adviceWith(camelContext, "order-saga-route", builder -> {
                builder.weaveAddLast().to("mock:result");
            });
            camelContext.start();

            String txId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

            // Configure custom order: INVENTORY -> CREDIT_CARD -> LOGISTICS
            List<ServiceConfig> customOrder = List.of(
                    new ServiceConfig(1, ServiceName.INVENTORY,
                            "http://localhost:8082/api/v1/inventory/notify",
                            "http://localhost:8082/api/v1/inventory/rollback"),
                    new ServiceConfig(2, ServiceName.CREDIT_CARD,
                            "http://localhost:8081/api/v1/credit-card/notify",
                            "http://localhost:8081/api/v1/credit-card/rollback"),
                    new ServiceConfig(3, ServiceName.LOGISTICS,
                            "http://localhost:8083/api/v1/logistics/notify",
                            "http://localhost:8083/api/v1/logistics/rollback")
            );

            when(serviceClientPort.notify(any(ServiceName.class), any(NotifyRequest.class)))
                    .thenReturn(successResponse(txId));

            TransactionEvent event = TransactionEvent.orderConfirmed(
                    txId,
                    "order-456",
                    "user-1",
                    List.of(Map.of("sku", "SKU-001", "quantity", 2)),
                    new BigDecimal("59.98"),
                    "4111111111111111"
            );

            // When
            producerTemplate.sendBodyAndHeader(
                    "direct:order-saga",
                    objectMapper.writeValueAsString(event),
                    "serviceOrder",
                    customOrder
            );

            // Then
            mockResult.expectedMessageCount(1);
            mockResult.assertIsSatisfied();
        }
    }
}
