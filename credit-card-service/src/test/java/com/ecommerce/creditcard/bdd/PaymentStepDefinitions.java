package com.ecommerce.creditcard.bdd;

import com.ecommerce.common.dto.NotifyRequest;
import com.ecommerce.common.dto.NotifyResponse;
import com.ecommerce.common.dto.RollbackRequest;
import com.ecommerce.common.dto.RollbackResponse;
import com.ecommerce.creditcard.application.service.CreditCardService;
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

public class PaymentStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CreditCardService creditCardService;

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
        ReflectionTestUtils.setField(creditCardService, "failureEnabled", false);
        ReflectionTestUtils.setField(creditCardService, "failureRate", 0.0);
    }

    @假設("信用卡服務已啟動")
    public void creditCardServiceIsRunning() {
        String healthUrl = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @假設("一個有效的付款請求，交易ID為 {string}")
    public void aValidPaymentRequestWithTxId(String txId) {
        currentTxId = UUID.fromString(txId);
        Map<String, Object> payload = Map.of(
                "totalAmount", new BigDecimal("99.99"),
                "creditCardNumber", "4111111111111111",
                "userId", "user-123",
                "items", List.of(Map.of("sku", "SKU-001", "quantity", 1, "unitPrice", 99.99))
        );
        notifyRequest = NotifyRequest.of(currentTxId, currentOrderId, payload);
    }

    @假設("信用卡服務配置為失敗模式")
    public void creditCardServiceConfiguredToFail() {
        ReflectionTestUtils.setField(creditCardService, "failureEnabled", true);
        ReflectionTestUtils.setField(creditCardService, "failureRate", 1.0);
    }

    @假設("一個退款請求，交易ID為 {string}")
    public void aRollbackRequestWithTxId(String txId) {
        currentTxId = UUID.fromString(txId);
        rollbackRequest = RollbackRequest.of(currentTxId, currentOrderId, "Test rollback");
    }

    @當("發送付款通知請求")
    public void sendPaymentNotifyRequest() {
        String url = "http://localhost:" + port + "/api/v1/credit-card/notify";
        notifyResponse = restTemplate.postForEntity(url, notifyRequest, NotifyResponse.class);
    }

    @當("再次發送相同的付款通知請求")
    public void sendSamePaymentNotifyRequestAgain() {
        String url = "http://localhost:" + port + "/api/v1/credit-card/notify";
        secondNotifyResponse = restTemplate.postForEntity(url, notifyRequest, NotifyResponse.class);
    }

    @當("收到成功的付款回應")
    public void receiveSuccessfulPaymentResponse() {
        assertThat(notifyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(notifyResponse.getBody()).isNotNull();
        assertThat(notifyResponse.getBody().success()).isTrue();
    }

    @當("發送付款通知請求並成功")
    public void sendPaymentNotifyRequestAndSucceed() {
        sendPaymentNotifyRequest();
        receiveSuccessfulPaymentResponse();
    }

    @當("發送退款請求")
    public void sendRollbackRequest() {
        rollbackRequest = RollbackRequest.of(currentTxId, currentOrderId, "Rollback payment");
        String url = "http://localhost:" + port + "/api/v1/credit-card/rollback";
        rollbackResponse = restTemplate.postForEntity(url, rollbackRequest, RollbackResponse.class);
    }

    @當("再次發送相同的退款請求")
    public void sendSameRollbackRequestAgain() {
        String url = "http://localhost:" + port + "/api/v1/credit-card/rollback";
        secondRollbackResponse = restTemplate.postForEntity(url, rollbackRequest, RollbackResponse.class);
    }

    @當("發送退款請求給不存在的付款")
    public void sendRollbackRequestForNonExistentPayment() {
        rollbackRequest = RollbackRequest.of(currentTxId, currentOrderId, "Rollback non-existent");
        String url = "http://localhost:" + port + "/api/v1/credit-card/rollback";
        rollbackResponse = restTemplate.postForEntity(url, rollbackRequest, RollbackResponse.class);
    }

    @那麼("應該收到成功的付款回應")
    public void shouldReceiveSuccessfulPaymentResponse() {
        assertThat(notifyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(notifyResponse.getBody()).isNotNull();
        assertThat(notifyResponse.getBody().success()).isTrue();
    }

    @那麼("回應應包含授權碼")
    public void responseShouldContainAuthCode() {
        assertThat(notifyResponse.getBody().serviceReference()).isNotNull();
        assertThat(notifyResponse.getBody().serviceReference()).startsWith("AUTH-");
    }

    @那麼("兩次回應應該相同")
    public void bothResponsesShouldBeTheSame() {
        assertThat(notifyResponse.getBody().success()).isEqualTo(secondNotifyResponse.getBody().success());
        assertThat(notifyResponse.getBody().serviceReference()).isEqualTo(secondNotifyResponse.getBody().serviceReference());
    }

    @那麼("應該只處理一次付款")
    public void shouldOnlyProcessPaymentOnce() {
        // Verified by the idempotent response having the same auth code
        assertThat(notifyResponse.getBody().serviceReference()).isEqualTo(secondNotifyResponse.getBody().serviceReference());
    }

    @那麼("應該收到成功的退款回應")
    public void shouldReceiveSuccessfulRollbackResponse() {
        assertThat(rollbackResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rollbackResponse.getBody()).isNotNull();
        assertThat(rollbackResponse.getBody().success()).isTrue();
    }

    @那麼("退款訊息應為 {string}")
    public void rollbackMessageShouldBe(String expectedMessage) {
        assertThat(rollbackResponse.getBody().message()).isEqualTo(expectedMessage);
    }

    @那麼("兩次退款回應應該相同")
    public void bothRollbackResponsesShouldBeTheSame() {
        assertThat(rollbackResponse.getBody().success()).isEqualTo(secondRollbackResponse.getBody().success());
    }

    @那麼("第一次退款訊息應為 {string}")
    public void firstRollbackMessageShouldBe(String expectedMessage) {
        assertThat(rollbackResponse.getBody().message()).isEqualTo(expectedMessage);
    }

    @那麼("第二次退款訊息應為 {string}")
    public void secondRollbackMessageShouldBe(String expectedMessage) {
        assertThat(secondRollbackResponse.getBody().message()).isEqualTo(expectedMessage);
    }

    @那麼("應該收到失敗的付款回應")
    public void shouldReceiveFailedPaymentResponse() {
        assertThat(notifyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(notifyResponse.getBody()).isNotNull();
        assertThat(notifyResponse.getBody().success()).isFalse();
    }

    @那麼("失敗訊息應包含 {string}")
    public void failureMessageShouldContain(String expectedText) {
        assertThat(notifyResponse.getBody().message()).contains(expectedText);
    }
}
