# 電子商務微服務交易編排系統 - 技術規格文件 (TECH)

> **版本**: 3.0  
> **建立日期**: 2026-01-01  
> **狀態**: Draft

---

## 1. 技術架構概述

### 1.1 系統架構圖

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API Gateway                                     │
│                           (Spring Cloud Gateway)                             │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Order Service (Orchestrator)                       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      Outbox Pattern (異步)                          │    │
│  │  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐          │    │
│  │  │ Order API    │───►│ Outbox Table │───►│ Single Poller│          │    │
│  │  │ (202 Accept) │    │ (DB Tx)      │    │              │          │    │
│  │  └──────────────┘    └──────────────┘    └──────┬───────┘          │    │
│  └─────────────────────────────────────────────────┼───────────────────┘    │
│                                                    │                         │
│  ┌─────────────────────────────────────────────────┼───────────────────┐    │
│  │                    Apache Camel Route           │                   │    │
│  │                  + Resilience4j Circuit Breaker │                   │    │
│  │  ┌─────────┐    ┌─────────┐    ┌─────────┐     │                   │    │
│  │  │CreditCard│───►│Inventory│───►│Logistics│◄────┘                   │    │
│  │  │  [CB]   │    │  [CB]   │    │  [CB]   │                          │    │
│  │  └─────────┘    └─────────┘    └─────────┘                          │    │
│  │       │              │              │                               │    │
│  │       └──────────────┴──────────────┘                               │    │
│  │                      │ On Failure / Timeout / CB Open               │    │
│  │                      ▼                                              │    │
│  │              ┌─────────────┐                                        │    │
│  │              │  Rollback   │──► 反向順序回滾                        │    │
│  │              │  Handler    │                                        │    │
│  │              └─────────────┘                                        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    Checker Thread (每訂單一個)                       │    │
│  │  - 監控 Pending 狀態超時                                            │    │
│  │  - 偵測 Fail 狀態觸發回滾                                           │    │
│  │  - 全 Success / 全 RollbackDone / 有 RollbackFail 時停止            │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    WebSocket Handler                                 │    │
│  │  - 即時推送狀態變更給 Client                                        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │   Transaction Log Table (H2)  │   Outbox Table (H2)                   │  │
│  │   - Event Sourcing 方式       │   - 待處理事件                        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
          │                      │                      │
          ▼                      ▼                      ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Credit Card    │    │   Inventory     │    │    Logistics    │
│    Service      │    │    Service      │    │     Service     │
│ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │ notify API  │ │    │ │ notify API  │ │    │ │ notify API  │ │
│ │ rollback API│ │    │ │ rollback API│ │    │ │ rollback API│ │
│ │ (冪等)      │ │    │ │ (冪等)      │ │    │ │ (冪等)      │ │
│ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │
│    ┌─────┐      │    │    ┌─────┐      │    │    ┌─────┐      │
│    │ H2  │      │    │    │ H2  │      │    │    │ H2  │      │
│    └─────┘      │    │    └─────┘      │    │    └─────┘      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 1.2 技術棧

| 層級 | 技術選型 |
|------|----------|
| 語言 | Java 21 |
| 框架 | Spring Boot 3.2.x |
| 編排引擎 | Apache Camel 4.x |
| 韌性模式 | Resilience4j (Circuit Breaker) |
| WebSocket | Spring WebSocket |
| 資料庫 | H2 Database (Embedded) |
| API 文件 | Springdoc OpenAPI (Swagger) |
| 測試框架 | JUnit 5, Mockito, Camel Test |
| 可觀測性 | Micrometer, Micrometer Tracing |
| 建置工具 | Gradle 8.x (Kotlin DSL) |

### 1.3 架構原則

- **六角形架構 (Hexagonal Architecture)**: 核心業務邏輯與外部依賴分離
- **SOLID Principles**: 確保程式碼的可維護性與擴展性
- **Monorepo**: 所有微服務在同一程式碼庫管理

---

## 2. 專案結構 (Monorepo)

```
ecommerce-saga/
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Multi-module settings
├── gradle.properties                   # Shared properties
│
├── common/                             # 共用模組
│   ├── build.gradle.kts
│   └── src/main/java/com/ecommerce/common/
│       ├── domain/                     # 共用領域模型
│       │   ├── TransactionStatus.java
│       │   └── ServiceName.java
│       ├── dto/                        # 共用 DTO
│       │   ├── NotifyRequest.java
│       │   ├── NotifyResponse.java
│       │   ├── RollbackRequest.java
│       │   └── RollbackResponse.java
│       ├── event/                      # 共用事件
│       │   └── SagaEvent.java
│       └── exception/                  # 共用例外
│           └── ServiceException.java
│
├── order-service/                      # 訂單服務 (Orchestrator)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/ecommerce/order/
│       │   ├── OrderServiceApplication.java
│       │   │
│       │   ├── adapter/                # 外部適配器層
│       │   │   ├── in/
│       │   │   │   ├── web/
│       │   │   │   │   ├── OrderController.java
│       │   │   │   │   ├── TransactionController.java
│       │   │   │   │   ├── AdminController.java
│       │   │   │   │   └── dto/
│       │   │   │   │       ├── OrderConfirmRequest.java
│       │   │   │   │       ├── OrderConfirmResponse.java
│       │   │   │   │       ├── TransactionQueryResponse.java
│       │   │   │   │       ├── ServiceOrderRequest.java
│       │   │   │   │       ├── TimeoutConfigRequest.java
│       │   │   │   │       ├── ServiceRegistrationRequest.java
│       │   │   │   │       └── WebSocketMessage.java
│       │   │   │   └── websocket/
│       │   │   │       └── OrderWebSocketHandler.java
│       │   │   └── out/
│       │   │       ├── persistence/
│       │   │       │   ├── TransactionLogEntity.java
│       │   │       │   ├── TransactionLogRepository.java
│       │   │       │   ├── TransactionLogPersistenceAdapter.java
│       │   │       │   ├── OutboxEventEntity.java
│       │   │       │   ├── OutboxEventRepository.java
│       │   │       │   ├── SagaConfigEntity.java
│       │   │       │   └── SagaConfigRepository.java
│       │   │       ├── service/
│       │   │       │   ├── CreditCardServiceClient.java
│       │   │       │   ├── InventoryServiceClient.java
│       │   │       │   └── LogisticsServiceClient.java
│       │   │       └── notification/
│       │   │           ├── EmailNotificationAdapter.java
│       │   │           └── MockEmailNotificationAdapter.java
│       │   │
│       │   ├── application/            # 應用層
│       │   │   ├── port/
│       │   │   │   ├── in/
│       │   │   │   │   ├── OrderConfirmUseCase.java
│       │   │   │   │   ├── TransactionQueryUseCase.java
│       │   │   │   │   ├── SagaConfigUseCase.java
│       │   │   │   │   └── ServiceManagementUseCase.java
│       │   │   │   └── out/
│       │   │   │       ├── TransactionLogPort.java
│       │   │   │       ├── OutboxPort.java
│       │   │   │       ├── ServiceClientPort.java
│       │   │   │       ├── WebSocketPort.java
│       │   │   │       └── NotificationPort.java
│       │   │   └── service/
│       │   │       ├── OrderSagaService.java
│       │   │       ├── TransactionQueryService.java
│       │   │       ├── RollbackService.java
│       │   │       ├── SagaConfigService.java
│       │   │       ├── ServiceManagementService.java
│       │   │       └── SagaRecoveryService.java
│       │   │
│       │   ├── domain/                 # 領域層
│       │   │   ├── model/
│       │   │   │   ├── Order.java
│       │   │   │   ├── TransactionLog.java
│       │   │   │   ├── SagaConfig.java
│       │   │   │   └── ServiceConfig.java
│       │   │   └── event/
│       │   │       └── TransactionEvent.java
│       │   │
│       │   └── infrastructure/         # 基礎設施層
│       │       ├── camel/
│       │       │   ├── OrderSagaRoute.java
│       │       │   ├── RollbackRoute.java
│       │       │   ├── DynamicServiceRoute.java
│       │       │   └── processor/
│       │       │       ├── PreNotifyProcessor.java
│       │       │       ├── PostNotifyProcessor.java
│       │       │       ├── RollbackProcessor.java
│       │       │       └── OutboxProcessor.java
│       │       ├── config/
│       │       │   ├── CamelConfig.java
│       │       │   ├── DataSourceConfig.java
│       │       │   ├── WebSocketConfig.java
│       │       │   ├── CircuitBreakerConfig.java
│       │       │   └── SwaggerConfig.java
│       │       ├── circuitbreaker/
│       │       │   ├── ServiceCircuitBreaker.java
│       │       │   └── CircuitBreakerRegistry.java
│       │       ├── poller/
│       │       │   └── OutboxPoller.java
│       │       ├── checker/
│       │       │   ├── CheckerThreadManager.java
│       │       │   └── TransactionCheckerThread.java
│       │       ├── recovery/
│       │       │   └── SagaRecoveryRunner.java
│       │       └── observability/
│       │           ├── SagaMetrics.java
│       │           ├── CircuitBreakerMetrics.java
│       │           └── TracingConfig.java
│       │
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── schema.sql
│       │
│       └── test/java/com/ecommerce/order/
│           ├── adapter/
│           ├── application/
│           ├── domain/
│           └── infrastructure/
│
├── credit-card-service/                # 信用卡服務
├── inventory-service/                  # 倉管服務
└── logistics-service/                  # 物流服務
```

---

## 3. 資料庫設計

### 3.1 交易狀態資料表 DDL

```sql
-- Transaction Log Table (Event Sourcing Style)
CREATE TABLE IF NOT EXISTS transaction_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_id           VARCHAR(36) NOT NULL,
    order_id        VARCHAR(36) NOT NULL,
    service_name    VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    error_message   VARCHAR(500),
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notified_at     TIMESTAMP,
    
    CONSTRAINT chk_status CHECK (status IN (
        'Pending', 'Success', 'Fail', 
        'Rollback', 'RollbackDone', 'RollbackFail', 'Skipped'
    ))
);

-- Indexes for query optimization
CREATE INDEX idx_tx_service_status ON transaction_log (tx_id, service_name, status);
CREATE INDEX idx_status_created ON transaction_log (status, created_at);
CREATE INDEX idx_order_id ON transaction_log (order_id);
CREATE INDEX idx_tx_id ON transaction_log (tx_id);

-- Outbox Table (for Outbox Pattern)
CREATE TABLE IF NOT EXISTS outbox_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_id           VARCHAR(36) NOT NULL,
    order_id        VARCHAR(36) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         CLOB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed       BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at    TIMESTAMP NULL
);

CREATE INDEX idx_outbox_processed ON outbox_event (processed, created_at);

-- Saga Config Table (for dynamic configuration)
CREATE TABLE IF NOT EXISTS saga_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_type     VARCHAR(50) NOT NULL,
    config_key      VARCHAR(100) NOT NULL,
    config_value    CLOB NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    is_pending      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_config_type_key_active ON saga_config (config_type, config_key, is_active);

-- Transaction Service Snapshot (記錄每筆交易當時的參與服務)
CREATE TABLE IF NOT EXISTS transaction_service_snapshot (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_id           VARCHAR(36) NOT NULL,
    service_order   CLOB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshot_tx ON transaction_service_snapshot (tx_id);
```

### 3.2 狀態碼定義

```java
public enum TransactionStatus {
    Pending("Pending"),           // 已呼叫，等待回應
    Success("Success"),           // 處理成功
    Fail("Fail"),                 // 處理失敗
    Rollback("Rollback"),         // 回滾中
    RollbackDone("RollbackDone"), // 回滾完成
    RollbackFail("RollbackFail"), // 回滾失敗
    Skipped("Skipped");           // 被跳過
    
    private final String value;
    
    TransactionStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}
```

### 3.3 Entity 設計

```java
// TransactionLogEntity.java
@Entity
@Table(name = "transaction_log")
@Getter
@NoArgsConstructor
public class TransactionLogEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "tx_id", nullable = false, length = 36)
    private String txId;
    
    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;
    
    @Column(name = "service_name", nullable = false, length = 50)
    private String serviceName;
    
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    
    @Column(name = "retry_count")
    private Integer retryCount;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;
    
    public static TransactionLogEntity create(String txId, String orderId, 
            String serviceName, TransactionStatus status) {
        TransactionLogEntity entity = new TransactionLogEntity();
        entity.txId = txId;
        entity.orderId = orderId;
        entity.serviceName = serviceName;
        entity.status = status.getValue();
        entity.createdAt = LocalDateTime.now();
        return entity;
    }
    
    public static TransactionLogEntity createWithError(String txId, String orderId,
            String serviceName, TransactionStatus status, String errorMessage) {
        TransactionLogEntity entity = create(txId, orderId, serviceName, status);
        entity.errorMessage = errorMessage;
        return entity;
    }
}
```

### 3.4 狀態查詢 SQL

```sql
-- 透過 Order ID 查詢所有相關交易
SELECT DISTINCT tx_id, order_id, MIN(created_at) as tx_created_at
FROM transaction_log
WHERE order_id = ?
GROUP BY tx_id, order_id
ORDER BY tx_created_at DESC;

-- 透過 TxID 查詢各服務最新狀態
SELECT tl.* FROM transaction_log tl
INNER JOIN (
    SELECT tx_id, service_name, MAX(created_at) as max_created
    FROM transaction_log
    WHERE tx_id = ?
    GROUP BY tx_id, service_name
) latest ON tl.tx_id = latest.tx_id 
        AND tl.service_name = latest.service_name 
        AND tl.created_at = latest.max_created;

-- 查詢未完成的交易（非終態）
SELECT DISTINCT t1.tx_id, t1.order_id FROM transaction_log t1
WHERE NOT EXISTS (
    -- 排除全部成功的交易
    SELECT 1 FROM transaction_service_snapshot snap
    INNER JOIN transaction_log t2 ON t2.tx_id = snap.tx_id
    WHERE snap.tx_id = t1.tx_id
    GROUP BY snap.tx_id
    HAVING COUNT(DISTINCT CASE WHEN t2.status = 'Success' THEN t2.service_name END) = 
           (SELECT COUNT(*) FROM JSON_TABLE(snap.service_order, '$[*]' COLUMNS (name VARCHAR(50) PATH '$.name')))
)
AND NOT EXISTS (
    -- 排除全部回滾完成的交易
    SELECT 1 FROM transaction_service_snapshot snap
    INNER JOIN transaction_log t2 ON t2.tx_id = snap.tx_id
    WHERE snap.tx_id = t1.tx_id
    GROUP BY snap.tx_id
    HAVING COUNT(DISTINCT CASE WHEN t2.status IN ('RollbackDone', 'Skipped') THEN t2.service_name END) = 
           (SELECT COUNT(*) FROM JSON_TABLE(snap.service_order, '$[*]' COLUMNS (name VARCHAR(50) PATH '$.name')))
)
AND NOT EXISTS (
    -- 排除有回滾失敗的交易
    SELECT 1 FROM transaction_log t3
    WHERE t3.tx_id = t1.tx_id AND t3.status = 'RollbackFail'
);
```

---

## 4. Circuit Breaker 實作

### 4.1 Resilience4j 設定

```java
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)                    // 失敗率閾值 50%
            .slowCallRateThreshold(50)                   // 慢呼叫率閾值 50%
            .slowCallDurationThreshold(Duration.ofSeconds(10)) // 慢呼叫定義
            .waitDurationInOpenState(Duration.ofSeconds(30))   // Open 等待時間
            .permittedNumberOfCallsInHalfOpenState(3)    // Half-Open 允許呼叫數
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)                       // 滑動視窗大小
            .minimumNumberOfCalls(5)                     // 最小呼叫數
            .build();
        
        return CircuitBreakerRegistry.of(config);
    }
}
```

### 4.2 Service Circuit Breaker Wrapper

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceCircuitBreaker {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public CircuitBreaker getOrCreate(String serviceName) {
        return breakers.computeIfAbsent(serviceName, 
            name -> circuitBreakerRegistry.circuitBreaker(name));
    }

    public <T> T executeWithCircuitBreaker(String serviceName, 
            Supplier<T> supplier, Supplier<T> fallback) {
        CircuitBreaker breaker = getOrCreate(serviceName);
        
        try {
            return breaker.executeSupplier(supplier);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for service: {}", serviceName);
            return fallback.get();
        } catch (Exception e) {
            log.error("Service call failed: {}", serviceName, e);
            throw e;
        }
    }

    public CircuitBreaker.State getState(String serviceName) {
        return getOrCreate(serviceName).getState();
    }

    public CircuitBreaker.Metrics getMetrics(String serviceName) {
        return getOrCreate(serviceName).getMetrics();
    }
}
```

### 4.3 Camel 整合 Circuit Breaker

```java
@Component
@RequiredArgsConstructor
public class CircuitBreakerProcessor implements Processor {

    private final ServiceCircuitBreaker serviceCircuitBreaker;
    private final TransactionLogPort transactionLogPort;

    @Override
    public void process(Exchange exchange) throws Exception {
        String serviceName = exchange.getProperty("currentService", String.class);
        String txId = exchange.getProperty("txId", String.class);
        
        CircuitBreaker.State state = serviceCircuitBreaker.getState(serviceName);
        
        if (state == CircuitBreaker.State.OPEN) {
            // 斷路器開啟，直接記錄失敗
            transactionLogPort.recordStatus(txId, serviceName, 
                TransactionStatus.Fail, "Circuit breaker is OPEN");
            
            exchange.setProperty("circuitBreakerOpen", true);
            exchange.setProperty("serviceFailed", true);
        }
    }
}
```

---

## 5. Apache Camel Route 設計

### 5.1 動態服務順序 Route（含 Circuit Breaker）

```java
@Component
@RequiredArgsConstructor
public class OrderSagaRoute extends RouteBuilder {

    private final SagaConfigService sagaConfigService;
    private final TransactionLogPort transactionLogPort;
    private final WebSocketPort webSocketPort;
    private final ServiceCircuitBreaker serviceCircuitBreaker;

    @Override
    public void configure() throws Exception {
        
        // Error Handler
        onException(Exception.class)
            .handled(true)
            .process(exchange -> {
                String txId = exchange.getProperty("txId", String.class);
                String serviceName = exchange.getProperty("currentService", String.class);
                String errorMsg = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, 
                    Exception.class).getMessage();
                
                // 記錄失敗狀態
                transactionLogPort.recordStatus(txId, serviceName, 
                    TransactionStatus.Fail, errorMsg);
                
                // 推送 WebSocket
                webSocketPort.sendStatus(txId, "FAILED", serviceName, 
                    "服務呼叫失敗: " + errorMsg);
                
                exchange.setProperty("serviceFailed", true);
            })
            .to("direct:triggerRollback");

        // Main Saga Route
        from("direct:startSaga")
            .routeId("saga-start")
            .process(exchange -> {
                String txId = exchange.getIn().getHeader("txId", String.class);
                String orderId = exchange.getIn().getHeader("orderId", String.class);
                
                // 取得當前生效的服務順序並建立快照
                List<ServiceConfig> services = sagaConfigService.getActiveServiceOrder();
                sagaConfigService.createServiceSnapshot(txId, services);
                
                exchange.setProperty("txId", txId);
                exchange.setProperty("orderId", orderId);
                exchange.setProperty("serviceList", services);
                exchange.setProperty("serviceIndex", 0);
                exchange.setProperty("serviceFailed", false);
            })
            .to("direct:processNextService");

        // 動態處理下一個服務
        from("direct:processNextService")
            .routeId("process-next-service")
            .choice()
                .when(simple("${exchangeProperty.serviceFailed} == true"))
                    .to("direct:triggerRollback")
                .when(exchange -> {
                    int index = exchange.getProperty("serviceIndex", Integer.class);
                    List<?> services = exchange.getProperty("serviceList", List.class);
                    return index < services.size();
                })
                    .to("direct:callService")
                .otherwise()
                    .to("direct:sagaComplete")
            .end();

        // 呼叫單一服務（含 Circuit Breaker 檢查）
        from("direct:callService")
            .routeId("call-service")
            .process(exchange -> {
                int index = exchange.getProperty("serviceIndex", Integer.class);
                List<ServiceConfig> services = exchange.getProperty("serviceList", List.class);
                ServiceConfig config = services.get(index);
                
                String txId = exchange.getProperty("txId", String.class);
                String orderId = exchange.getProperty("orderId", String.class);
                
                exchange.setProperty("currentService", config.getName());
                exchange.setProperty("notifyUrl", config.getNotifyUrl());
                
                // 檢查 Circuit Breaker 狀態
                CircuitBreaker.State cbState = serviceCircuitBreaker.getState(config.getName());
                if (cbState == CircuitBreaker.State.OPEN) {
                    // 直接標記失敗
                    transactionLogPort.recordStatus(txId, config.getName(), 
                        TransactionStatus.Fail, "Circuit breaker is OPEN");
                    webSocketPort.sendStatus(txId, "FAILED", config.getName(),
                        "服務不可用 (Circuit Breaker Open)");
                    exchange.setProperty("serviceFailed", true);
                    return;
                }
                
                // 記錄 Pending 狀態
                transactionLogPort.recordStatus(txId, config.getName(), 
                    TransactionStatus.Pending, null);
                
                // 推送 WebSocket
                webSocketPort.sendStatus(txId, "PROCESSING", config.getName(), 
                    "正在處理: " + config.getName());
            })
            .choice()
                .when(simple("${exchangeProperty.serviceFailed} == true"))
                    .to("direct:processNextService")
                .otherwise()
                    .toD("${exchangeProperty.notifyUrl}")
                    .process(exchange -> {
                        String txId = exchange.getProperty("txId", String.class);
                        String serviceName = exchange.getProperty("currentService", String.class);
                        int index = exchange.getProperty("serviceIndex", Integer.class);
                        
                        // 記錄 Success 狀態
                        transactionLogPort.recordStatus(txId, serviceName, 
                            TransactionStatus.Success, null);
                        
                        // 推送 WebSocket
                        webSocketPort.sendStatus(txId, "PROCESSING", serviceName, 
                            serviceName + " 處理成功");
                        
                        // 移動到下一個服務
                        exchange.setProperty("serviceIndex", index + 1);
                    })
                    .to("direct:processNextService")
            .end();

        // 觸發回滾
        from("direct:triggerRollback")
            .routeId("trigger-rollback")
            .to("direct:rollback");

        // Saga 完成
        from("direct:sagaComplete")
            .routeId("saga-complete")
            .process(exchange -> {
                String txId = exchange.getProperty("txId", String.class);
                webSocketPort.sendStatus(txId, "COMPLETED", null, "訂單交易完成");
            });
    }
}
```

### 5.2 Rollback Route

```java
@Component
@RequiredArgsConstructor
public class RollbackRoute extends RouteBuilder {

    private final TransactionLogPort transactionLogPort;
    private final SagaConfigService sagaConfigService;
    private final WebSocketPort webSocketPort;
    private final NotificationPort notificationPort;
    
    @Value("${saga.rollback.max-retries:5}")
    private int maxRetries;

    @Override
    public void configure() throws Exception {
        
        from("direct:rollback")
            .routeId("rollback-route")
            .process(exchange -> {
                String txId = exchange.getProperty("txId", String.class);
                
                // 取得需回滾的服務（狀態為 Success，反向順序）
                List<String> successfulServices = 
                    transactionLogPort.findSuccessfulServices(txId);
                Collections.reverse(successfulServices);
                
                exchange.setProperty("rollbackServices", successfulServices);
                exchange.setProperty("rollbackIndex", 0);
                
                webSocketPort.sendStatus(txId, "ROLLING_BACK", null, "開始回滾");
            })
            .to("direct:processNextRollback");

        from("direct:processNextRollback")
            .routeId("process-next-rollback")
            .choice()
                .when(exchange -> {
                    int index = exchange.getProperty("rollbackIndex", Integer.class);
                    List<?> services = exchange.getProperty("rollbackServices", List.class);
                    return index < services.size();
                })
                    .to("direct:rollbackService")
                .otherwise()
                    .to("direct:rollbackComplete")
            .end();

        from("direct:rollbackService")
            .routeId("rollback-service")
            .process(exchange -> {
                int index = exchange.getProperty("rollbackIndex", Integer.class);
                List<String> services = exchange.getProperty("rollbackServices", List.class);
                String serviceName = services.get(index);
                String txId = exchange.getProperty("txId", String.class);
                
                ServiceConfig config = sagaConfigService.getServiceConfig(serviceName);
                exchange.setProperty("currentRollbackService", serviceName);
                exchange.setProperty("rollbackUrl", config.getRollbackUrl());
                exchange.setProperty("retryCount", 0);
                
                // 記錄 Rollback 狀態
                transactionLogPort.recordStatus(txId, serviceName, 
                    TransactionStatus.Rollback, null);
            })
            .to("direct:executeRollback");

        from("direct:executeRollback")
            .routeId("execute-rollback")
            .doTry()
                .toD("${exchangeProperty.rollbackUrl}")
                .process(exchange -> {
                    String txId = exchange.getProperty("txId", String.class);
                    String serviceName = exchange.getProperty("currentRollbackService", String.class);
                    int index = exchange.getProperty("rollbackIndex", Integer.class);
                    
                    // 記錄 RollbackDone 狀態
                    transactionLogPort.recordStatus(txId, serviceName, 
                        TransactionStatus.RollbackDone, null);
                    
                    webSocketPort.sendStatus(txId, "ROLLING_BACK", serviceName, 
                        serviceName + " 回滾成功");
                    
                    exchange.setProperty("rollbackIndex", index + 1);
                })
                .to("direct:processNextRollback")
            .doCatch(Exception.class)
                .process(exchange -> {
                    int retryCount = exchange.getProperty("retryCount", Integer.class);
                    String serviceName = exchange.getProperty("currentRollbackService", String.class);
                    
                    if (retryCount < maxRetries - 1) {
                        exchange.setProperty("retryCount", retryCount + 1);
                        // 指數退避
                        Thread.sleep(1000L * (1L << retryCount));
                    } else {
                        // 達到最大重試次數
                        String txId = exchange.getProperty("txId", String.class);
                        String errorMsg = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, 
                            Exception.class).getMessage();
                        
                        transactionLogPort.recordRollbackFailed(
                            txId, serviceName, errorMsg, maxRetries);
                        
                        // 發送通知
                        notificationPort.sendRollbackFailedNotification(txId, serviceName, errorMsg);
                        
                        webSocketPort.sendStatus(txId, "ROLLBACK_FAILED", serviceName, 
                            serviceName + " 回滾失敗，需人工介入");
                        
                        exchange.setProperty("rollbackFailed", true);
                    }
                })
                .choice()
                    .when(simple("${exchangeProperty.rollbackFailed} != true"))
                        .to("direct:executeRollback")
                    .otherwise()
                        .stop()
                .end()
            .end();

        from("direct:rollbackComplete")
            .routeId("rollback-complete")
            .process(exchange -> {
                String txId = exchange.getProperty("txId", String.class);
                webSocketPort.sendStatus(txId, "ROLLED_BACK", null, "交易已回滾完成");
            });
    }
}
```

---

## 6. Checker Thread 實作

### 6.1 Checker Thread Manager

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckerThreadManager {

    private final Map<String, TransactionCheckerThread> activeThreads = 
        new ConcurrentHashMap<>();
    private final TransactionLogPort transactionLogPort;
    private final SagaConfigService sagaConfigService;
    private final ProducerTemplate producerTemplate;
    private final WebSocketPort webSocketPort;

    public void startChecker(String txId, String orderId) {
        TransactionCheckerThread checker = new TransactionCheckerThread(
            txId, orderId, 
            transactionLogPort, 
            sagaConfigService,
            producerTemplate,
            webSocketPort,
            this::removeChecker
        );
        activeThreads.put(txId, checker);
        checker.start();
        log.info("Started checker thread for txId: {}", txId);
    }

    public void removeChecker(String txId) {
        TransactionCheckerThread removed = activeThreads.remove(txId);
        if (removed != null) {
            log.info("Removed checker thread for txId: {}", txId);
        }
    }

    public Set<String> getActiveCheckers() {
        return activeThreads.keySet();
    }
}
```

### 6.2 Transaction Checker Thread

```java
@Slf4j
public class TransactionCheckerThread extends Thread {

    private final String txId;
    private final String orderId;
    private final TransactionLogPort transactionLogPort;
    private final SagaConfigService sagaConfigService;
    private final ProducerTemplate producerTemplate;
    private final WebSocketPort webSocketPort;
    private final Consumer<String> onComplete;
    
    private volatile boolean running = true;
    private static final long CHECK_INTERVAL = 1000; // 1 秒

    public TransactionCheckerThread(String txId, String orderId,
            TransactionLogPort transactionLogPort,
            SagaConfigService sagaConfigService,
            ProducerTemplate producerTemplate,
            WebSocketPort webSocketPort,
            Consumer<String> onComplete) {
        super("checker-" + txId);
        setDaemon(true); // 設為 Daemon Thread
        this.txId = txId;
        this.orderId = orderId;
        this.transactionLogPort = transactionLogPort;
        this.sagaConfigService = sagaConfigService;
        this.producerTemplate = producerTemplate;
        this.webSocketPort = webSocketPort;
        this.onComplete = onComplete;
    }

    @Override
    public void run() {
        log.info("Checker thread started for txId: {}", txId);
        
        while (running) {
            try {
                CheckResult result = checkTransaction();
                
                switch (result) {
                    case ALL_SUCCESS:
                        log.info("Transaction {} completed successfully", txId);
                        stopChecker();
                        break;
                        
                    case ALL_ROLLBACK_DONE:
                        log.info("Transaction {} rollback completed", txId);
                        stopChecker();
                        break;
                        
                    case HAS_ROLLBACK_FAIL:
                        log.error("Transaction {} has rollback failure", txId);
                        stopChecker();
                        break;
                        
                    case HAS_FAILURE:
                        log.warn("Transaction {} has failure, triggering rollback", txId);
                        triggerRollback();
                        // 不停止，繼續監控回滾進度
                        Thread.sleep(CHECK_INTERVAL);
                        break;
                        
                    case HAS_TIMEOUT:
                        log.warn("Transaction {} has timeout, triggering rollback", txId);
                        markTimeoutAndTriggerRollback();
                        // 不停止，繼續監控回滾進度
                        Thread.sleep(CHECK_INTERVAL);
                        break;
                        
                    case IN_PROGRESS:
                        // 繼續監控
                        Thread.sleep(CHECK_INTERVAL);
                        break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in checker thread for txId: {}", txId, e);
                try {
                    Thread.sleep(CHECK_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        onComplete.accept(txId);
        log.info("Checker thread stopped for txId: {}", txId);
    }

    private CheckResult checkTransaction() {
        // 取得該交易的服務快照
        List<String> participatingServices = sagaConfigService.getServiceSnapshot(txId);
        Map<String, TransactionStatus> latestStatuses = 
            transactionLogPort.getLatestStatuses(txId);
        
        // 檢查是否有 RollbackFail 狀態（終態）
        if (latestStatuses.values().stream()
                .anyMatch(s -> s == TransactionStatus.RollbackFail)) {
            return CheckResult.HAS_ROLLBACK_FAIL;
        }
        
        // 檢查是否全部成功（終態）
        boolean allSuccess = participatingServices.stream()
            .allMatch(name -> latestStatuses.get(name) == TransactionStatus.Success);
        if (allSuccess) {
            return CheckResult.ALL_SUCCESS;
        }
        
        // 檢查是否全部回滾完成（終態）
        boolean allRollbackDone = participatingServices.stream()
            .allMatch(name -> {
                TransactionStatus status = latestStatuses.get(name);
                return status == TransactionStatus.RollbackDone 
                    || status == TransactionStatus.Skipped;
            });
        if (allRollbackDone) {
            return CheckResult.ALL_ROLLBACK_DONE;
        }
        
        // 檢查是否有失敗（需觸發回滾）
        boolean hasFail = latestStatuses.values().stream()
            .anyMatch(s -> s == TransactionStatus.Fail);
        boolean hasRollbackInProgress = latestStatuses.values().stream()
            .anyMatch(s -> s == TransactionStatus.Rollback);
        
        if (hasFail && !hasRollbackInProgress) {
            return CheckResult.HAS_FAILURE;
        }
        
        // 檢查是否有超時
        Map<String, Integer> timeouts = sagaConfigService.getActiveTimeouts();
        for (Map.Entry<String, TransactionStatus> entry : latestStatuses.entrySet()) {
            if (entry.getValue() == TransactionStatus.Pending) {
                LocalDateTime createdAt = transactionLogPort
                    .getStatusCreatedAt(txId, entry.getKey(), TransactionStatus.Pending);
                int timeout = timeouts.getOrDefault(entry.getKey(), 60);
                
                if (createdAt.plusSeconds(timeout).isBefore(LocalDateTime.now())) {
                    return CheckResult.HAS_TIMEOUT;
                }
            }
        }
        
        return CheckResult.IN_PROGRESS;
    }

    private void triggerRollback() {
        Map<String, Object> headers = Map.of("txId", txId, "orderId", orderId);
        producerTemplate.sendBodyAndHeaders("direct:rollback", null, headers);
    }

    private void markTimeoutAndTriggerRollback() {
        // 標記超時的服務為 Fail
        Map<String, TransactionStatus> latestStatuses = 
            transactionLogPort.getLatestStatuses(txId);
        Map<String, Integer> timeouts = sagaConfigService.getActiveTimeouts();
        
        for (Map.Entry<String, TransactionStatus> entry : latestStatuses.entrySet()) {
            if (entry.getValue() == TransactionStatus.Pending) {
                LocalDateTime createdAt = transactionLogPort
                    .getStatusCreatedAt(txId, entry.getKey(), TransactionStatus.Pending);
                int timeout = timeouts.getOrDefault(entry.getKey(), 60);
                
                if (createdAt.plusSeconds(timeout).isBefore(LocalDateTime.now())) {
                    transactionLogPort.recordStatus(txId, entry.getKey(), 
                        TransactionStatus.Fail, "Timeout after " + timeout + " seconds");
                    webSocketPort.sendStatus(txId, "FAILED", entry.getKey(), 
                        entry.getKey() + " 超時");
                }
            }
        }
        
        triggerRollback();
    }

    public void stopChecker() {
        this.running = false;
    }

    private enum CheckResult {
        ALL_SUCCESS,           // 終態：全部成功
        ALL_ROLLBACK_DONE,     // 終態：全部回滾完成
        HAS_ROLLBACK_FAIL,     // 終態：有回滾失敗
        HAS_FAILURE,           // 有失敗，需觸發回滾
        HAS_TIMEOUT,           // 有超時，需觸發回滾
        IN_PROGRESS            // 進行中，繼續監控
    }
}
```

---

## 7. 交易查詢 API 實作

### 7.1 Transaction Query Service

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionQueryService implements TransactionQueryUseCase {

    private final TransactionLogRepository transactionLogRepository;
    private final TransactionServiceSnapshotRepository snapshotRepository;

    @Override
    public TransactionQueryResponse queryByOrderId(String orderId) {
        // 查詢該 Order ID 的所有 TxID
        List<String> txIds = transactionLogRepository.findDistinctTxIdsByOrderId(orderId);
        
        List<TransactionDetail> transactions = txIds.stream()
            .map(this::buildTransactionDetail)
            .collect(Collectors.toList());
        
        return TransactionQueryResponse.builder()
            .orderId(orderId)
            .transactions(transactions)
            .build();
    }

    @Override
    public TransactionDetail queryByTxId(String txId) {
        return buildTransactionDetail(txId);
    }

    private TransactionDetail buildTransactionDetail(String txId) {
        // 取得各服務最新狀態
        List<TransactionLogEntity> latestLogs = 
            transactionLogRepository.findLatestByTxId(txId);
        
        List<ServiceStatus> serviceStatuses = latestLogs.stream()
            .map(log -> ServiceStatus.builder()
                .name(log.getServiceName())
                .status(log.getStatus())
                .updatedAt(log.getCreatedAt())
                .errorMessage(log.getErrorMessage())
                .build())
            .collect(Collectors.toList());
        
        // 計算整體狀態
        String overallStatus = calculateOverallStatus(serviceStatuses);
        
        // 取得 Order ID
        String orderId = latestLogs.isEmpty() ? null : latestLogs.get(0).getOrderId();
        
        // 取得建立時間
        LocalDateTime createdAt = transactionLogRepository
            .findFirstByTxIdOrderByCreatedAtAsc(txId)
            .map(TransactionLogEntity::getCreatedAt)
            .orElse(null);
        
        return TransactionDetail.builder()
            .txId(txId)
            .orderId(orderId)
            .createdAt(createdAt)
            .services(serviceStatuses)
            .overallStatus(overallStatus)
            .build();
    }

    private String calculateOverallStatus(List<ServiceStatus> serviceStatuses) {
        Set<String> statuses = serviceStatuses.stream()
            .map(ServiceStatus::getStatus)
            .collect(Collectors.toSet());
        
        if (statuses.contains("RollbackFail")) {
            return "RollbackFailed";
        }
        if (statuses.stream().allMatch(s -> s.equals("Success"))) {
            return "Completed";
        }
        if (statuses.stream().allMatch(s -> 
                s.equals("RollbackDone") || s.equals("Skipped"))) {
            return "RolledBack";
        }
        if (statuses.contains("Rollback")) {
            return "RollingBack";
        }
        if (statuses.contains("Fail")) {
            return "Failed";
        }
        return "Processing";
    }
}
```

### 7.2 Transaction Controller

```java
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction", description = "交易查詢 API")
public class TransactionController {

    private final TransactionQueryUseCase transactionQueryUseCase;

    @GetMapping
    @Operation(summary = "查詢交易狀態",
        description = "透過 orderId 或 txId 查詢交易狀態（二擇一）")
    public ResponseEntity<?> queryTransaction(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String txId) {
        
        if (orderId != null && txId != null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "請只提供 orderId 或 txId 其中之一"));
        }
        
        if (orderId == null && txId == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "請提供 orderId 或 txId"));
        }
        
        if (orderId != null) {
            TransactionQueryResponse response = 
                transactionQueryUseCase.queryByOrderId(orderId);
            return ResponseEntity.ok(response);
        } else {
            TransactionDetail detail = 
                transactionQueryUseCase.queryByTxId(txId);
            return ResponseEntity.ok(detail);
        }
    }
}
```

---

## 8. 服務管理 API 實作

### 8.1 Service Management Service

```java
@Service
@RequiredArgsConstructor
@Transactional
public class ServiceManagementService implements ServiceManagementUseCase {

    private final SagaConfigRepository configRepository;
    private final ObjectMapper objectMapper;
    
    // 記憶體快取
    private volatile List<ServiceConfig> activeServices;
    private volatile List<ServiceConfig> pendingAddedServices = new ArrayList<>();
    private volatile Set<String> pendingRemovedServices = new HashSet<>();

    @PostConstruct
    public void loadActiveServices() {
        reloadActiveServices();
    }

    @Override
    public ServiceListResponse getServices() {
        return ServiceListResponse.builder()
            .active(activeServices)
            .pending(PendingChanges.builder()
                .added(pendingAddedServices)
                .removed(new ArrayList<>(pendingRemovedServices))
                .build())
            .build();
    }

    @Override
    public void addService(ServiceRegistrationRequest request) {
        // 驗證服務名稱不重複
        boolean exists = activeServices.stream()
            .anyMatch(s -> s.getName().equals(request.getName()));
        boolean pendingExists = pendingAddedServices.stream()
            .anyMatch(s -> s.getName().equals(request.getName()));
        
        if (exists || pendingExists) {
            throw new IllegalArgumentException("Service already exists: " + request.getName());
        }
        
        ServiceConfig newService = ServiceConfig.builder()
            .name(request.getName())
            .notifyUrl(request.getNotifyUrl())
            .rollbackUrl(request.getRollbackUrl())
            .timeout(request.getTimeout())
            .order(request.getOrder())
            .build();
        
        pendingAddedServices.add(newService);
        savePendingChanges();
    }

    @Override
    public void removeService(String serviceName) {
        // 驗證服務存在
        boolean exists = activeServices.stream()
            .anyMatch(s -> s.getName().equals(serviceName));
        
        if (!exists) {
            throw new IllegalArgumentException("Service not found: " + serviceName);
        }
        
        // 從待新增中移除（如果有的話）
        pendingAddedServices.removeIf(s -> s.getName().equals(serviceName));
        
        // 加入待移除
        pendingRemovedServices.add(serviceName);
        savePendingChanges();
    }

    @Override
    public void applyChanges() {
        // 計算新的服務清單
        List<ServiceConfig> newServices = new ArrayList<>(activeServices);
        
        // 移除
        newServices.removeIf(s -> pendingRemovedServices.contains(s.getName()));
        
        // 新增
        newServices.addAll(pendingAddedServices);
        
        // 依 order 排序
        newServices.sort(Comparator.comparingInt(ServiceConfig::getOrder));
        
        // 儲存並設為 active
        SagaConfigEntity config = new SagaConfigEntity();
        config.setConfigType("SERVICES");
        config.setConfigKey("active");
        config.setConfigValue(toJson(newServices));
        config.setIsActive(true);
        config.setIsPending(false);
        
        configRepository.deactivateByType("SERVICES");
        configRepository.save(config);
        
        // 清除待變更
        pendingAddedServices.clear();
        pendingRemovedServices.clear();
        configRepository.deletePendingByType("SERVICES");
        
        // 重新載入
        reloadActiveServices();
    }

    private void reloadActiveServices() {
        Optional<SagaConfigEntity> config = 
            configRepository.findByConfigTypeAndIsActiveTrue("SERVICES");
        activeServices = config
            .map(c -> fromJson(c.getConfigValue(), new TypeReference<List<ServiceConfig>>(){}))
            .orElse(getDefaultServices());
    }

    private void savePendingChanges() {
        PendingChanges changes = PendingChanges.builder()
            .added(pendingAddedServices)
            .removed(new ArrayList<>(pendingRemovedServices))
            .build();
        
        configRepository.deletePendingByType("SERVICES");
        
        SagaConfigEntity config = new SagaConfigEntity();
        config.setConfigType("SERVICES");
        config.setConfigKey("pending");
        config.setConfigValue(toJson(changes));
        config.setIsActive(false);
        config.setIsPending(true);
        configRepository.save(config);
    }

    private List<ServiceConfig> getDefaultServices() {
        return List.of(
            ServiceConfig.builder()
                .order(1).name("CREDIT_CARD")
                .notifyUrl("http://localhost:8081/api/v1/credit-card/notify")
                .rollbackUrl("http://localhost:8081/api/v1/credit-card/rollback")
                .timeout(30).build(),
            ServiceConfig.builder()
                .order(2).name("INVENTORY")
                .notifyUrl("http://localhost:8082/api/v1/inventory/notify")
                .rollbackUrl("http://localhost:8082/api/v1/inventory/rollback")
                .timeout(60).build(),
            ServiceConfig.builder()
                .order(3).name("LOGISTICS")
                .notifyUrl("http://localhost:8083/api/v1/logistics/notify")
                .rollbackUrl("http://localhost:8083/api/v1/logistics/rollback")
                .timeout(120).build()
        );
    }
    
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
    
    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }
}
```

### 8.2 Admin Controller（完整版）

```java
@RestController
@RequestMapping("/api/v1/admin/saga")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Saga 管理 API")
public class AdminController {

    private final SagaConfigUseCase sagaConfigUseCase;
    private final ServiceManagementUseCase serviceManagementUseCase;

    // === Service Order ===
    
    @GetMapping("/service-order")
    @Operation(summary = "查詢服務順序")
    public ResponseEntity<ServiceOrderResponse> getServiceOrder() {
        return ResponseEntity.ok(new ServiceOrderResponse(
            sagaConfigUseCase.getActiveServiceOrder(),
            sagaConfigUseCase.getPendingServiceOrder()
        ));
    }

    @PutMapping("/service-order")
    @Operation(summary = "修改服務順序（暫存）")
    public ResponseEntity<Void> updateServiceOrder(
            @RequestBody ServiceOrderRequest request) {
        sagaConfigUseCase.updateServiceOrder(request.getServices());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/service-order/apply")
    @Operation(summary = "觸發服務順序生效")
    public ResponseEntity<Void> applyServiceOrder() {
        sagaConfigUseCase.applyServiceOrder();
        return ResponseEntity.ok().build();
    }

    // === Timeout ===
    
    @GetMapping("/timeout")
    @Operation(summary = "查詢超時設定")
    public ResponseEntity<TimeoutResponse> getTimeout() {
        return ResponseEntity.ok(new TimeoutResponse(
            sagaConfigUseCase.getActiveTimeouts(),
            sagaConfigUseCase.getPendingTimeouts()
        ));
    }

    @PutMapping("/timeout")
    @Operation(summary = "修改超時設定（暫存）")
    public ResponseEntity<Void> updateTimeout(
            @RequestBody TimeoutRequest request) {
        sagaConfigUseCase.updateTimeouts(request.getTimeouts());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/timeout/apply")
    @Operation(summary = "觸發超時設定生效")
    public ResponseEntity<Void> applyTimeout() {
        sagaConfigUseCase.applyTimeouts();
        return ResponseEntity.ok().build();
    }

    // === Services (參與服務管理) ===
    
    @GetMapping("/services")
    @Operation(summary = "查詢參與服務清單")
    public ResponseEntity<ServiceListResponse> getServices() {
        return ResponseEntity.ok(serviceManagementUseCase.getServices());
    }

    @PostMapping("/services")
    @Operation(summary = "加入微服務（暫存）")
    public ResponseEntity<Void> addService(
            @RequestBody @Valid ServiceRegistrationRequest request) {
        serviceManagementUseCase.addService(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/services/{serviceName}")
    @Operation(summary = "移出微服務（暫存）")
    public ResponseEntity<Void> removeService(
            @PathVariable String serviceName) {
        serviceManagementUseCase.removeService(serviceName);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/services/apply")
    @Operation(summary = "觸發參與服務變更生效")
    public ResponseEntity<Void> applyServiceChanges() {
        serviceManagementUseCase.applyChanges();
        return ResponseEntity.ok().build();
    }
}
```

---

## 9. 可觀測性實作

### 9.1 Saga Metrics（含 Circuit Breaker）

```java
@Component
@RequiredArgsConstructor
public class SagaMetrics {

    private final MeterRegistry meterRegistry;
    private final ServiceCircuitBreaker serviceCircuitBreaker;
    
    private Counter sagaStartedCounter;
    private Counter sagaCompletedCounter;
    private Counter sagaFailedCounter;
    private Counter sagaRolledBackCounter;
    private Counter sagaRollbackFailedCounter;
    private Timer sagaDurationTimer;

    @PostConstruct
    public void init() {
        sagaStartedCounter = Counter.builder("saga.started")
            .description("Number of sagas started")
            .register(meterRegistry);
            
        sagaCompletedCounter = Counter.builder("saga.completed")
            .description("Number of sagas completed successfully")
            .register(meterRegistry);
            
        sagaFailedCounter = Counter.builder("saga.failed")
            .description("Number of sagas failed")
            .register(meterRegistry);
            
        sagaRolledBackCounter = Counter.builder("saga.rolledback")
            .description("Number of sagas rolled back")
            .register(meterRegistry);
            
        sagaRollbackFailedCounter = Counter.builder("saga.rollback.failed")
            .description("Number of saga rollbacks that failed")
            .register(meterRegistry);
            
        sagaDurationTimer = Timer.builder("saga.duration")
            .description("Duration of saga execution")
            .register(meterRegistry);
        
        // Circuit Breaker Gauges
        registerCircuitBreakerMetrics("CREDIT_CARD");
        registerCircuitBreakerMetrics("INVENTORY");
        registerCircuitBreakerMetrics("LOGISTICS");
    }

    private void registerCircuitBreakerMetrics(String serviceName) {
        Gauge.builder("circuitbreaker.state", serviceCircuitBreaker, 
                cb -> cb.getState(serviceName).ordinal())
            .tag("service", serviceName)
            .description("Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
            .register(meterRegistry);
        
        Gauge.builder("circuitbreaker.failure_rate", serviceCircuitBreaker,
                cb -> cb.getMetrics(serviceName).getFailureRate())
            .tag("service", serviceName)
            .description("Circuit breaker failure rate")
            .register(meterRegistry);
    }

    public void recordSagaStarted() { sagaStartedCounter.increment(); }
    public void recordSagaCompleted() { sagaCompletedCounter.increment(); }
    public void recordSagaFailed() { sagaFailedCounter.increment(); }
    public void recordSagaRolledBack() { sagaRolledBackCounter.increment(); }
    public void recordSagaRollbackFailed() { sagaRollbackFailedCounter.increment(); }
    public Timer.Sample startTimer() { return Timer.start(meterRegistry); }
    public void stopTimer(Timer.Sample sample) { sample.stop(sagaDurationTimer); }
}
```

---

## 10. 設定檔

### 10.1 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: order-service
  profiles:
    active: dev
  datasource:
    url: jdbc:h2:mem:orderdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
    properties:
      hibernate:
        format_sql: true

# Outbox Poller
outbox:
  poll:
    interval: 500

# Saga Configuration
saga:
  rollback:
    max-retries: 5
  default:
    timeout:
      credit-card: 30
      inventory: 60
      logistics: 120

# Circuit Breaker Configuration
resilience4j:
  circuitbreaker:
    instances:
      CREDIT_CARD:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 10s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
      INVENTORY:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
      LOGISTICS:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10

# Service URLs
service:
  creditcard:
    url: http://localhost:8081
  inventory:
    url: http://localhost:8082
  logistics:
    url: http://localhost:8083

# Notification (dev profile uses mock)
notification:
  email:
    to: admin@example.com
    from: saga-system@example.com

# Swagger
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    operationsSorter: method

# Actuator & Metrics
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}

# Tracing
tracing:
  sampling:
    probability: 1.0
```

---

## 11. Gradle 建置設定

### 11.1 Root build.gradle.kts

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.2.1" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
}

allprojects {
    group = "com.ecommerce"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        implementation("org.springframework.boot:spring-boot-starter-validation")
        implementation("org.springframework.boot:spring-boot-starter-websocket")
        implementation("org.springframework.boot:spring-boot-starter-actuator")
        
        // Apache Camel
        implementation("org.apache.camel.springboot:camel-spring-boot-starter:4.3.0")
        implementation("org.apache.camel.springboot:camel-http-starter:4.3.0")
        
        // Resilience4j (Circuit Breaker)
        implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
        implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
        
        // Database
        runtimeOnly("com.h2database:h2")
        
        // Swagger
        implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
        
        // Observability
        implementation("io.micrometer:micrometer-registry-prometheus")
        implementation("io.micrometer:micrometer-tracing-bridge-brave")
        
        // Lombok
        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
        
        // Test
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.apache.camel:camel-test-spring-junit5:4.3.0")
        testImplementation("org.mockito:mockito-core")
        testImplementation("org.awaitility:awaitility")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
```

---

## 12. 開發任務清單

### Phase 1: 基礎建設 (2 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 1.1 | 建立 Monorepo 專案結構 | P0 |
| 1.2 | 設定 Gradle 多模組建置 | P0 |
| 1.3 | 建立 common 模組（含新狀態碼） | P0 |
| 1.4 | 設定 H2 資料庫與 schema | P0 |

### Phase 2: 核心服務 (3 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 2.1 | 實作 TransactionLog Entity & Repository | P0 |
| 2.2 | 實作 Outbox Pattern（寫入 + Poller） | P0 |
| 2.3 | 實作 Order Service 六角形架構 | P0 |
| 2.4 | 實作交易查詢 Service & Controller | P0 |
| 2.5 | 實作 Credit Card Service（含冪等 rollback） | P1 |
| 2.6 | 實作 Inventory Service（含冪等 rollback） | P1 |
| 2.7 | 實作 Logistics Service（含冪等 rollback） | P1 |

### Phase 3: Circuit Breaker & Camel Route (3 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 3.1 | 設定 Resilience4j Circuit Breaker | P0 |
| 3.2 | 實作 ServiceCircuitBreaker Wrapper | P0 |
| 3.3 | 實作動態 OrderSagaRoute（含 CB 整合） | P0 |
| 3.4 | 實作 RollbackRoute（含重試機制） | P0 |
| 3.5 | 整合測試 Camel Route | P0 |

### Phase 4: Checker Thread & WebSocket (2 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 4.1 | 實作 CheckerThreadManager | P0 |
| 4.2 | 實作 TransactionCheckerThread（新終態邏輯） | P0 |
| 4.3 | 實作 WebSocket Handler | P0 |
| 4.4 | 測試超時與回滾機制 | P0 |

### Phase 5: 管理 API & 恢復機制 (3 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 5.1 | 實作 SagaConfigService | P0 |
| 5.2 | 實作 ServiceManagementService | P0 |
| 5.3 | 實作 AdminController（10 支 API） | P0 |
| 5.4 | 實作 SagaRecoveryRunner | P0 |
| 5.5 | 實作 Email 通知（Mock + Real） | P1 |

### Phase 6: 可觀測性 & 文件 (2 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 6.1 | 實作 SagaMetrics（含 CB 指標） | P1 |
| 6.2 | 設定 Tracing | P1 |
| 6.3 | 設定 Swagger 文件 | P1 |
| 6.4 | 撰寫 Unit Tests | P0 |
| 6.5 | 撰寫 Integration Tests | P1 |
| 6.6 | 撰寫 README.md | P2 |

---

## 13. 附錄

### 13.1 狀態碼參照

| 狀態碼 | 說明 | 是否終態 |
|--------|------|----------|
| Pending | 已呼叫，等待回應 | 否 |
| Success | 處理成功 | 是（全部 Success） |
| Fail | 處理失敗 | 否（需觸發回滾） |
| Rollback | 回滾中 | 否 |
| RollbackDone | 回滾完成 | 是（全部 RollbackDone） |
| RollbackFail | 回滾失敗 | 是（需人工介入） |
| Skipped | 被跳過 | 是（視同 RollbackDone） |

### 13.2 API Endpoints 總覽

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/orders/confirm` | 確認訂單，啟動 Saga |
| GET | `/api/v1/transactions?orderId=xxx` | 透過 Order ID 查詢交易狀態 |
| GET | `/api/v1/transactions?txId=xxx` | 透過 TxID 查詢交易狀態 |
| WS | `/ws/orders/{txId}` | WebSocket 狀態推送 |
| GET | `/api/v1/admin/saga/service-order` | 查詢服務順序 |
| PUT | `/api/v1/admin/saga/service-order` | 修改服務順序 |
| POST | `/api/v1/admin/saga/service-order/apply` | 觸發服務順序生效 |
| GET | `/api/v1/admin/saga/timeout` | 查詢超時設定 |
| PUT | `/api/v1/admin/saga/timeout` | 修改超時設定 |
| POST | `/api/v1/admin/saga/timeout/apply` | 觸發超時設定生效 |
| GET | `/api/v1/admin/saga/services` | 查詢參與服務 |
| POST | `/api/v1/admin/saga/services` | 加入微服務 |
| DELETE | `/api/v1/admin/saga/services/{name}` | 移出微服務 |
| POST | `/api/v1/admin/saga/services/apply` | 觸發服務變更生效 |
| POST | `/api/v1/credit-card/notify` | 信用卡交易通知 |
| POST | `/api/v1/credit-card/rollback` | 信用卡交易回滾（冪等） |
| POST | `/api/v1/inventory/notify` | 倉管預留通知 |
| POST | `/api/v1/inventory/rollback` | 倉管預留回滾（冪等） |
| POST | `/api/v1/logistics/notify` | 物流排程通知 |
| POST | `/api/v1/logistics/rollback` | 物流排程回滾（冪等） |

### 13.3 Circuit Breaker 狀態

| 狀態 | 說明 |
|------|------|
| CLOSED | 正常，允許所有呼叫 |
| OPEN | 熔斷，直接快速失敗 |
| HALF_OPEN | 半開，允許少量探測呼叫 |
