package com.ecommerce.order.infrastructure.camel;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.order.application.port.out.ServiceClientPort;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.application.port.out.WebSocketPort;
import com.ecommerce.order.application.service.RollbackService;
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
@DisplayName("RollbackRoute Tests")
@org.junit.jupiter.api.Disabled("Pending implementation of RollbackRoute - T098")
class RollbackRouteTest {

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

    @MockBean
    private RollbackService rollbackService;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Rollback Execution")
    class RollbackExecution {

        @Test
        @DisplayName("should execute rollback when triggered")
        void shouldExecuteRollbackWhenTriggered() throws Exception {
            // Given
            AdviceWith.adviceWith(camelContext, "rollback-route", builder -> {
                builder.weaveAddLast().to("mock:result");
            });
            camelContext.start();

            String txId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
            String orderId = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

            List<ServiceName> successfulServices = List.of(
                    ServiceName.CREDIT_CARD,
                    ServiceName.INVENTORY
            );

            when(transactionLogPort.findSuccessfulServices(any(UUID.class)))
                    .thenReturn(successfulServices);

            // When
            producerTemplate.sendBodyAndHeaders(
                    "direct:rollback",
                    null,
                    Map.of(
                            "txId", txId,
                            "orderId", orderId,
                            "failedService", ServiceName.LOGISTICS.name(),
                            "errorMessage", "Carrier unavailable"
                    )
            );

            // Then
            mockResult.expectedMessageCount(1);
            mockResult.assertIsSatisfied();

            verify(rollbackService).executeRollback(
                    eq(UUID.fromString(txId)),
                    eq(UUID.fromString(orderId)),
                    eq(successfulServices)
            );
        }

        @Test
        @DisplayName("should log failed service before rollback")
        void shouldLogFailedServiceBeforeRollback() throws Exception {
            // Given
            AdviceWith.adviceWith(camelContext, "rollback-route", builder -> {
                builder.weaveAddLast().to("mock:result");
            });
            camelContext.start();

            String txId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
            String orderId = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

            when(transactionLogPort.findSuccessfulServices(any(UUID.class)))
                    .thenReturn(List.of(ServiceName.CREDIT_CARD));

            // When
            producerTemplate.sendBodyAndHeaders(
                    "direct:rollback",
                    null,
                    Map.of(
                            "txId", txId,
                            "orderId", orderId,
                            "failedService", ServiceName.INVENTORY.name(),
                            "errorMessage", "Out of stock"
                    )
            );

            // Then
            mockResult.expectedMessageCount(1);
            mockResult.assertIsSatisfied();

            // Verify the failed service is logged
            verify(transactionLogPort).recordStatusWithError(
                    eq(UUID.fromString(txId)),
                    eq(UUID.fromString(orderId)),
                    eq(ServiceName.INVENTORY),
                    any(),
                    eq("Out of stock")
            );
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should handle rollback service exception gracefully")
        void shouldHandleRollbackServiceExceptionGracefully() throws Exception {
            // Given
            AdviceWith.adviceWith(camelContext, "rollback-route", builder -> {
                builder.weaveAddLast().to("mock:result");
            });
            camelContext.start();

            String txId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
            String orderId = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

            when(transactionLogPort.findSuccessfulServices(any(UUID.class)))
                    .thenReturn(List.of(ServiceName.CREDIT_CARD));

            doThrow(new RuntimeException("Rollback service error"))
                    .when(rollbackService).executeRollback(any(), any(), any());

            // When
            producerTemplate.sendBodyAndHeaders(
                    "direct:rollback",
                    null,
                    Map.of(
                            "txId", txId,
                            "orderId", orderId,
                            "failedService", ServiceName.INVENTORY.name(),
                            "errorMessage", "Out of stock"
                    )
            );

            // Then - should not throw, route should handle error
            mockResult.expectedMessageCount(1);
            mockResult.assertIsSatisfied();
        }
    }
}
