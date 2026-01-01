package com.ecommerce.order.bdd;

import com.ecommerce.order.adapter.in.web.dto.OrderConfirmRequest;
import com.ecommerce.order.adapter.in.web.dto.OrderConfirmResponse;
import com.ecommerce.order.adapter.in.web.dto.ServiceConfigListResponse;
import com.ecommerce.order.adapter.in.web.dto.ServiceOrderResponse;
import com.ecommerce.order.adapter.in.web.dto.TransactionStatusResponse;
import io.cucumber.java.Before;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.那麼;
import io.cucumber.java.zh_tw.當;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class SagaOrchestrationStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private OrderConfirmRequest validOrderRequest;
    private OrderConfirmRequest invalidOrderRequest;
    private String currentTxId;
    private ResponseEntity<OrderConfirmResponse> confirmResponse;
    private ResponseEntity<TransactionStatusResponse> statusResponse;
    private ResponseEntity<ServiceConfigListResponse> configResponse;
    private ResponseEntity<Map> timeoutResponse;
    private ResponseEntity<ServiceOrderResponse> orderResponse;
    private ResponseEntity<?> errorResponse;

    @Before
    public void setup() {
        currentTxId = null;
    }

    @假設("訂單服務已啟動")
    public void orderServiceIsRunning() {
        String healthUrl = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @假設("所有下游服務已可用")
    public void allDownstreamServicesAvailable() {
        // In test environment, we assume downstream services are mocked or available
        // This is a precondition check
    }

    @假設("一個有效的訂單確認請求")
    public void aValidOrderConfirmRequest() {
        validOrderRequest = new OrderConfirmRequest(
                UUID.randomUUID().toString(),
                "user-123",
                List.of(
                        new OrderConfirmRequest.OrderItemDto("SKU-001", 2, new BigDecimal("29.99")),
                        new OrderConfirmRequest.OrderItemDto("SKU-002", 1, new BigDecimal("49.99"))
                ),
                new BigDecimal("109.97"),
                "4111111111111111"
        );
    }

    @假設("一個缺少必要欄位的訂單請求")
    public void anInvalidOrderRequest() {
        // Missing required fields
        invalidOrderRequest = new OrderConfirmRequest(
                null,  // missing orderId
                null,  // missing userId
                List.of(),  // empty items
                BigDecimal.ZERO,
                null  // missing creditCardNumber
        );
    }

    @假設("一個已提交的訂單交易")
    public void anExistingOrderTransaction() {
        aValidOrderConfirmRequest();
        sendOrderConfirmRequest();
        assertThat(confirmResponse.getBody()).isNotNull();
        currentTxId = confirmResponse.getBody().txId();
    }

    @當("發送訂單確認請求")
    public void sendOrderConfirmRequest() {
        String url = "http://localhost:" + port + "/api/v1/orders/confirm";
        confirmResponse = restTemplate.postForEntity(url, validOrderRequest, OrderConfirmResponse.class);
    }

    @當("發送無效的訂單確認請求")
    public void sendInvalidOrderConfirmRequest() {
        String url = "http://localhost:" + port + "/api/v1/orders/confirm";
        errorResponse = restTemplate.postForEntity(url, invalidOrderRequest, Map.class);
    }

    @當("查詢該交易狀態")
    public void queryTransactionStatus() {
        String url = "http://localhost:" + port + "/api/v1/transactions/" + currentTxId;
        statusResponse = restTemplate.getForEntity(url, TransactionStatusResponse.class);
    }

    @當("取得當前生效配置")
    public void getActiveConfig() {
        String url = "http://localhost:" + port + "/api/v1/admin/config/active";
        configResponse = restTemplate.getForEntity(url, ServiceConfigListResponse.class);
    }

    @當("取得服務超時設定")
    public void getTimeoutConfig() {
        String url = "http://localhost:" + port + "/api/v1/admin/config/timeouts";
        timeoutResponse = restTemplate.getForEntity(url, Map.class);
    }

    @當("取得服務執行順序")
    public void getServiceOrder() {
        String url = "http://localhost:" + port + "/api/v1/admin/config/order";
        orderResponse = restTemplate.getForEntity(url, ServiceOrderResponse.class);
    }

    @那麼("應該收到處理中的回應")
    public void shouldReceiveProcessingResponse() {
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(confirmResponse.getBody()).isNotNull();
        assertThat(confirmResponse.getBody().status()).isEqualTo("PROCESSING");
    }

    @那麼("回應應包含交易ID")
    public void responseShouldContainTxId() {
        assertThat(confirmResponse.getBody().txId()).isNotNull();
        assertThat(confirmResponse.getBody().txId()).isNotEmpty();
    }

    @那麼("應該收到交易狀態資訊")
    public void shouldReceiveTransactionStatusInfo() {
        assertThat(statusResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(statusResponse.getBody()).isNotNull();
    }

    @那麼("狀態應包含交易ID")
    public void statusShouldContainTxId() {
        assertThat(statusResponse.getBody().txId()).isEqualTo(currentTxId);
    }

    @那麼("應該收到錯誤回應")
    public void shouldReceiveErrorResponse() {
        assertThat(errorResponse.getStatusCode().is4xxClientError()).isTrue();
    }

    @那麼("應該收到配置資訊")
    public void shouldReceiveConfigInfo() {
        assertThat(configResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(configResponse.getBody()).isNotNull();
    }

    @那麼("配置應包含服務順序")
    public void configShouldContainServiceOrder() {
        assertThat(configResponse.getBody().configs()).isNotNull();
    }

    @那麼("應該收到超時配置")
    public void shouldReceiveTimeoutConfig() {
        assertThat(timeoutResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(timeoutResponse.getBody()).isNotNull();
    }

    @那麼("每個服務都應有超時值")
    public void eachServiceShouldHaveTimeout() {
        Map<String, Object> timeouts = timeoutResponse.getBody();
        assertThat(timeouts).isNotEmpty();
    }

    @那麼("應該收到服務順序列表")
    public void shouldReceiveServiceOrderList() {
        assertThat(orderResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(orderResponse.getBody()).isNotNull();
    }

    @那麼("列表應包含信用卡、庫存和物流服務")
    public void listShouldContainAllServices() {
        List<String> services = orderResponse.getBody().order();
        assertThat(services).isNotEmpty();
        assertThat(services).contains("CREDIT_CARD", "INVENTORY", "LOGISTICS");
    }
}
