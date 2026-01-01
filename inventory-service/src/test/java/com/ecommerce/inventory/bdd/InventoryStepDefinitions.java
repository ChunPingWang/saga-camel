package com.ecommerce.inventory.bdd;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.inventory.application.service.InventoryService;
import io.cucumber.java.Before;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.那麼;
import io.cucumber.java.zh_tw.當;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class InventoryStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InventoryService inventoryService;

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
        ReflectionTestUtils.setField(inventoryService, "failureEnabled", false);
        ReflectionTestUtils.setField(inventoryService, "failureRate", 0.0);
    }

    @假設("庫存服務已啟動")
    public void inventoryServiceIsRunning() {
        String healthUrl = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @假設("一個有效的庫存預留請求，交易ID為 {string}")
    public void aValidReservationRequestWithTxId(String txId) {
        currentTxId = UUID.fromString(txId);
        Map<String, Object> payload = Map.of(
                "userId", "user-123",
                "items", List.of(
                        Map.of("sku", "SKU-001", "quantity", 2, "unitPrice", 29.99),
                        Map.of("sku", "SKU-002", "quantity", 1, "unitPrice", 49.99)
                )
        );
        notifyRequest = NotifyRequest.of(currentTxId, currentOrderId, payload);
    }

    @假設("庫存服務配置為失敗模式")
    public void inventoryServiceConfiguredToFail() {
        ReflectionTestUtils.setField(inventoryService, "failureEnabled", true);
        ReflectionTestUtils.setField(inventoryService, "failureRate", 1.0);
    }

    @假設("一個釋放請求，交易ID為 {string}")
    public void aReleaseRequestWithTxId(String txId) {
        currentTxId = UUID.fromString(txId);
        rollbackRequest = RollbackRequest.of(currentTxId, currentOrderId, "Test release");
    }

    @當("發送庫存預留請求")
    public void sendReservationRequest() {
        String url = "http://localhost:" + port + "/api/v1/inventory/notify";
        notifyResponse = restTemplate.postForEntity(url, notifyRequest, NotifyResponse.class);
    }

    @當("再次發送相同的庫存預留請求")
    public void sendSameReservationRequestAgain() {
        String url = "http://localhost:" + port + "/api/v1/inventory/notify";
        secondNotifyResponse = restTemplate.postForEntity(url, notifyRequest, NotifyResponse.class);
    }

    @當("收到成功的預留回應")
    public void receiveSuccessfulReservationResponse() {
        assertThat(notifyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(notifyResponse.getBody()).isNotNull();
        assertThat(notifyResponse.getBody().success()).isTrue();
    }

    @當("發送庫存預留請求並成功")
    public void sendReservationRequestAndSucceed() {
        sendReservationRequest();
        receiveSuccessfulReservationResponse();
    }

    @當("發送庫存釋放請求")
    public void sendReleaseRequest() {
        rollbackRequest = RollbackRequest.of(currentTxId, currentOrderId, "Release inventory");
        String url = "http://localhost:" + port + "/api/v1/inventory/rollback";
        rollbackResponse = restTemplate.postForEntity(url, rollbackRequest, RollbackResponse.class);
    }

    @當("再次發送相同的釋放請求")
    public void sendSameReleaseRequestAgain() {
        String url = "http://localhost:" + port + "/api/v1/inventory/rollback";
        secondRollbackResponse = restTemplate.postForEntity(url, rollbackRequest, RollbackResponse.class);
    }

    @當("發送釋放請求給不存在的預留")
    public void sendReleaseRequestForNonExistentReservation() {
        rollbackRequest = RollbackRequest.of(currentTxId, currentOrderId, "Release non-existent");
        String url = "http://localhost:" + port + "/api/v1/inventory/rollback";
        rollbackResponse = restTemplate.postForEntity(url, rollbackRequest, RollbackResponse.class);
    }

    @那麼("應該收到成功的預留回應")
    public void shouldReceiveSuccessfulReservationResponse() {
        assertThat(notifyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(notifyResponse.getBody()).isNotNull();
        assertThat(notifyResponse.getBody().success()).isTrue();
    }

    @那麼("回應應包含預留編號")
    public void responseShouldContainReservationRef() {
        assertThat(notifyResponse.getBody().serviceReference()).isNotNull();
        assertThat(notifyResponse.getBody().serviceReference()).startsWith("RES-");
    }

    @那麼("兩次預留回應應該相同")
    public void bothReservationResponsesShouldBeTheSame() {
        assertThat(notifyResponse.getBody().success()).isEqualTo(secondNotifyResponse.getBody().success());
        assertThat(notifyResponse.getBody().serviceReference()).isEqualTo(secondNotifyResponse.getBody().serviceReference());
    }

    @那麼("應該只預留一次庫存")
    public void shouldOnlyReserveOnce() {
        assertThat(notifyResponse.getBody().serviceReference()).isEqualTo(secondNotifyResponse.getBody().serviceReference());
    }

    @那麼("應該收到成功的釋放回應")
    public void shouldReceiveSuccessfulReleaseResponse() {
        assertThat(rollbackResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rollbackResponse.getBody()).isNotNull();
        assertThat(rollbackResponse.getBody().success()).isTrue();
    }

    @那麼("釋放訊息應為 {string}")
    public void releaseMessageShouldBe(String expectedMessage) {
        assertThat(rollbackResponse.getBody().message()).isEqualTo(expectedMessage);
    }

    @那麼("兩次釋放回應應該相同")
    public void bothReleaseResponsesShouldBeTheSame() {
        assertThat(rollbackResponse.getBody().success()).isEqualTo(secondRollbackResponse.getBody().success());
    }

    @那麼("第一次釋放訊息應為 {string}")
    public void firstReleaseMessageShouldBe(String expectedMessage) {
        assertThat(rollbackResponse.getBody().message()).isEqualTo(expectedMessage);
    }

    @那麼("第二次釋放訊息應為 {string}")
    public void secondReleaseMessageShouldBe(String expectedMessage) {
        assertThat(secondRollbackResponse.getBody().message()).isEqualTo(expectedMessage);
    }

    @那麼("應該收到失敗的預留回應")
    public void shouldReceiveFailedReservationResponse() {
        assertThat(notifyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(notifyResponse.getBody()).isNotNull();
        assertThat(notifyResponse.getBody().success()).isFalse();
    }

    @那麼("失敗訊息應包含 {string}")
    public void failureMessageShouldContain(String expectedText) {
        assertThat(notifyResponse.getBody().message()).contains(expectedText);
    }
}
