package com.ecommerce.logistics.bdd;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.logistics.application.service.LogisticsService;
import io.cucumber.java.Before;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.那麼;
import io.cucumber.java.zh_tw.當;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class LogisticsStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LogisticsService logisticsService;

    private UUID currentTxId;
    private UUID currentOrderId;
    private NotifyRequest notifyRequest;
    private RollbackRequest rollbackRequest;
    private ResponseEntity<NotifyResponse> notifyResponse;
    private ResponseEntity<NotifyResponse> secondNotifyResponse;
    private ResponseEntity<RollbackResponse> rollbackResponse;
    private ResponseEntity<RollbackResponse> secondRollbackResponse;

    @Before
    public void setup() {
        currentOrderId = UUID.randomUUID();
        // Reset failure simulation
        ReflectionTestUtils.setField(logisticsService, "failureEnabled", false);
        ReflectionTestUtils.setField(logisticsService, "failureRate", 0.0);
    }

    @假設("物流服務已啟動")
    public void logisticsServiceIsRunning() {
        String healthUrl = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @假設("一個有效的運送請求，交易ID為 {string}")
    public void aValidShipmentRequestWithTxId(String txId) {
        currentTxId = UUID.fromString(txId);
        Map<String, Object> payload = Map.of(
                "userId", "user-123",
                "shippingAddress", "123 Main St, City, Country",
                "items", List.of(
                        Map.of("sku", "SKU-001", "quantity", 2)
                )
        );
        notifyRequest = NotifyRequest.of(currentTxId, currentOrderId, payload);
    }

    @假設("物流服務配置為失敗模式")
    public void logisticsServiceConfiguredToFail() {
        ReflectionTestUtils.setField(logisticsService, "failureEnabled", true);
        ReflectionTestUtils.setField(logisticsService, "failureRate", 1.0);
    }

    @假設("一個取消請求，交易ID為 {string}")
    public void aCancelRequestWithTxId(String txId) {
        currentTxId = UUID.fromString(txId);
        rollbackRequest = RollbackRequest.of(currentTxId, currentOrderId, "Test cancel");
    }

    @當("發送運送安排請求")
    public void sendShipmentRequest() {
        String url = "http://localhost:" + port + "/api/v1/logistics/notify";
        notifyResponse = restTemplate.postForEntity(url, notifyRequest, NotifyResponse.class);
    }

    @當("再次發送相同的運送安排請求")
    public void sendSameShipmentRequestAgain() {
        String url = "http://localhost:" + port + "/api/v1/logistics/notify";
        secondNotifyResponse = restTemplate.postForEntity(url, notifyRequest, NotifyResponse.class);
    }

    @當("收到成功的運送回應")
    public void receiveSuccessfulShipmentResponse() {
        assertThat(notifyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(notifyResponse.getBody()).isNotNull();
        assertThat(notifyResponse.getBody().success()).isTrue();
    }

    @當("發送運送安排請求並成功")
    public void sendShipmentRequestAndSucceed() {
        sendShipmentRequest();
        receiveSuccessfulShipmentResponse();
    }

    @當("發送取消運送請求")
    public void sendCancelRequest() {
        rollbackRequest = RollbackRequest.of(currentTxId, currentOrderId, "Cancel shipment");
        String url = "http://localhost:" + port + "/api/v1/logistics/rollback";
        rollbackResponse = restTemplate.postForEntity(url, rollbackRequest, RollbackResponse.class);
    }

    @當("再次發送相同的取消請求")
    public void sendSameCancelRequestAgain() {
        String url = "http://localhost:" + port + "/api/v1/logistics/rollback";
        secondRollbackResponse = restTemplate.postForEntity(url, rollbackRequest, RollbackResponse.class);
    }

    @當("發送取消請求給不存在的運送")
    public void sendCancelRequestForNonExistentShipment() {
        rollbackRequest = RollbackRequest.of(currentTxId, currentOrderId, "Cancel non-existent");
        String url = "http://localhost:" + port + "/api/v1/logistics/rollback";
        rollbackResponse = restTemplate.postForEntity(url, rollbackRequest, RollbackResponse.class);
    }

    @那麼("應該收到成功的運送回應")
    public void shouldReceiveSuccessfulShipmentResponse() {
        assertThat(notifyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(notifyResponse.getBody()).isNotNull();
        assertThat(notifyResponse.getBody().success()).isTrue();
    }

    @那麼("回應應包含物流追蹤編號")
    public void responseShouldContainTrackingNumber() {
        assertThat(notifyResponse.getBody().serviceReference()).isNotNull();
        assertThat(notifyResponse.getBody().serviceReference()).startsWith("TRK-");
    }

    @那麼("兩次運送回應應該相同")
    public void bothShipmentResponsesShouldBeTheSame() {
        assertThat(notifyResponse.getBody().success()).isEqualTo(secondNotifyResponse.getBody().success());
        assertThat(notifyResponse.getBody().serviceReference()).isEqualTo(secondNotifyResponse.getBody().serviceReference());
    }

    @那麼("應該只安排一次運送")
    public void shouldOnlyScheduleOnce() {
        assertThat(notifyResponse.getBody().serviceReference()).isEqualTo(secondNotifyResponse.getBody().serviceReference());
    }

    @那麼("應該收到成功的取消回應")
    public void shouldReceiveSuccessfulCancelResponse() {
        assertThat(rollbackResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rollbackResponse.getBody()).isNotNull();
        assertThat(rollbackResponse.getBody().success()).isTrue();
    }

    @那麼("取消訊息應為 {string}")
    public void cancelMessageShouldBe(String expectedMessage) {
        assertThat(rollbackResponse.getBody().message()).isEqualTo(expectedMessage);
    }

    @那麼("兩次取消回應應該相同")
    public void bothCancelResponsesShouldBeTheSame() {
        assertThat(rollbackResponse.getBody().success()).isEqualTo(secondRollbackResponse.getBody().success());
    }

    @那麼("第一次取消訊息應為 {string}")
    public void firstCancelMessageShouldBe(String expectedMessage) {
        assertThat(rollbackResponse.getBody().message()).isEqualTo(expectedMessage);
    }

    @那麼("第二次取消訊息應為 {string}")
    public void secondCancelMessageShouldBe(String expectedMessage) {
        assertThat(secondRollbackResponse.getBody().message()).isEqualTo(expectedMessage);
    }

    @那麼("應該收到失敗的運送回應")
    public void shouldReceiveFailedShipmentResponse() {
        assertThat(notifyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(notifyResponse.getBody()).isNotNull();
        assertThat(notifyResponse.getBody().success()).isFalse();
    }

    @那麼("失敗訊息應包含 {string}")
    public void failureMessageShouldContain(String expectedText) {
        assertThat(notifyResponse.getBody().message()).contains(expectedText);
    }
}
