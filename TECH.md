# 電子商務微服務交易編排系統 - 技術規格文件 (TECH)

> **版本**: 2.0  
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
│  │  ┌─────────┐    ┌─────────┐    ┌─────────┐     │                   │    │
│  │  │CreditCard│───►│Inventory│───►│Logistics│◄────┘                   │    │
│  │  └─────────┘    └─────────┘    └─────────┘                          │    │
│  │       │              │              │                               │    │
│  │       └──────────────┴──────────────┘                               │    │
│  │                      │ On Failure / Timeout                         │    │
│  │                      ▼                                              │    │
│  │              ┌─────────────┐                                        │    │
│  │              │  Rollback   │──► 反向順序回滾                        │    │
│  │              │  Handler    │                                        │    │
│  │              └─────────────┘                                        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    Checker Thread (每訂單一個)                       │    │
│  │  - 監控 U 狀態超時                                                  │    │
│  │  - 偵測 F 狀態觸發回滾                                              │    │
│  │  - 全 S / D / RF 時停止                                             │    │
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
│       │   │   │   │   ├── AdminController.java
│       │   │   │   │   └── dto/
│       │   │   │   │       ├── OrderConfirmRequest.java
│       │   │   │   │       ├── OrderConfirmResponse.java
│       │   │   │   │       ├── ServiceOrderRequest.java
│       │   │   │   │       ├── TimeoutConfigRequest.java
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
│       │   │   │   │   ├── SagaConfigUseCase.java
│       │   │   │   │   └── TransactionQueryUseCase.java
│       │   │   │   └── out/
│       │   │   │       ├── TransactionLogPort.java
│       │   │   │       ├── OutboxPort.java
│       │   │   │       ├── ServiceClientPort.java
│       │   │   │       ├── WebSocketPort.java
│       │   │   │       └── NotificationPort.java
│       │   │   └── service/
│       │   │       ├── OrderSagaService.java
│       │   │       ├── RollbackService.java
│       │   │       ├── SagaConfigService.java
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
│       │       │   └── SwaggerConfig.java
│       │       ├── poller/
│       │       │   └── OutboxPoller.java
│       │       ├── checker/
│       │       │   ├── CheckerThreadManager.java
│       │       │   └── TransactionCheckerThread.java
│       │       ├── recovery/
│       │       │   └── SagaRecoveryRunner.java
│       │       └── observability/
│       │           ├── SagaMetrics.java
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
│   ├── build.gradle.kts
│   └── src/main/java/com/ecommerce/creditcard/
│       ├── CreditCardServiceApplication.java
│       ├── adapter/
│       │   └── in/web/
│       │       └── CreditCardController.java
│       ├── application/
│       │   ├── port/in/
│       │   │   ├── ProcessPaymentUseCase.java
│       │   │   └── RollbackPaymentUseCase.java
│       │   └── service/
│       │       └── CreditCardService.java
│       └── domain/
│           └── model/
│               └── Payment.java
│
├── inventory-service/                  # 倉管服務
│   ├── build.gradle.kts
│   └── src/main/java/com/ecommerce/inventory/
│       ├── InventoryServiceApplication.java
│       ├── adapter/
│       │   └── in/web/
│       │       └── InventoryController.java
│       ├── application/
│       │   ├── port/in/
│       │   │   ├── ReserveInventoryUseCase.java
│       │   │   └── RollbackReservationUseCase.java
│       │   └── service/
│       │       └── InventoryService.java
│       └── domain/
│           └── model/
│               └── Reservation.java
│
└── logistics-service/                  # 物流服務
    ├── build.gradle.kts
    └── src/main/java/com/ecommerce/logistics/
        ├── LogisticsServiceApplication.java
        ├── adapter/
        │   └── in/web/
        │       └── LogisticsController.java
        ├── application/
        │   ├── port/in/
        │   │   ├── ScheduleShipmentUseCase.java
        │   │   └── CancelShipmentUseCase.java
        │   └── service/
        │       └── LogisticsService.java
        └── domain/
            └── model/
                └── Shipment.java
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
    status          CHAR(2) NOT NULL,
    error_message   VARCHAR(500),
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notified_at     TIMESTAMP,
    
    CONSTRAINT chk_status CHECK (status IN ('U', 'S', 'F', 'R', 'D', 'RF'))
);

-- Indexes for query optimization
CREATE INDEX idx_tx_service_status ON transaction_log (tx_id, service_name, status);
CREATE INDEX idx_status_created ON transaction_log (status, created_at);
CREATE INDEX idx_order_id ON transaction_log (order_id);

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

CREATE UNIQUE INDEX idx_config_type_key ON saga_config (config_type, config_key, is_active);
```

### 3.2 Entity 設計

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
    
    @Column(name = "status", nullable = false, length = 2)
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
            String serviceName, String status) {
        TransactionLogEntity entity = new TransactionLogEntity();
        entity.txId = txId;
        entity.orderId = orderId;
        entity.serviceName = serviceName;
        entity.status = status;
        entity.createdAt = LocalDateTime.now();
        return entity;
    }
}

// OutboxEventEntity.java
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor
public class OutboxEventEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "tx_id", nullable = false, length = 36)
    private String txId;
    
    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;
    
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    
    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "processed", nullable = false)
    private Boolean processed = false;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
```

### 3.3 狀態查詢 SQL

```sql
-- 查詢特定 TxID 的所有狀態記錄
SELECT * FROM transaction_log 
WHERE tx_id = ? 
ORDER BY created_at ASC;

-- 查詢特定 TxID 的最新狀態（每個服務）
SELECT tl.* FROM transaction_log tl
INNER JOIN (
    SELECT tx_id, service_name, MAX(created_at) as max_created
    FROM transaction_log
    WHERE tx_id = ?
    GROUP BY tx_id, service_name
) latest ON tl.tx_id = latest.tx_id 
        AND tl.service_name = latest.service_name 
        AND tl.created_at = latest.max_created;

-- 查詢未完成的交易（服務重啟恢復用）
SELECT DISTINCT tx_id FROM transaction_log t1
WHERE NOT EXISTS (
    -- 排除全部成功的交易
    SELECT 1 FROM transaction_log t2
    WHERE t2.tx_id = t1.tx_id
    GROUP BY t2.tx_id
    HAVING COUNT(DISTINCT CASE WHEN t2.status = 'S' THEN t2.service_name END) = 3
)
AND NOT EXISTS (
    -- 排除已完成回滾的交易
    SELECT 1 FROM transaction_log t3
    WHERE t3.tx_id = t1.tx_id AND t3.status = 'D'
)
AND NOT EXISTS (
    -- 排除回滾失敗的交易
    SELECT 1 FROM transaction_log t4
    WHERE t4.tx_id = t1.tx_id AND t4.status = 'RF'
);
```

---

## 4. Outbox Pattern 實作

### 4.1 Outbox Service

```java
@Service
@RequiredArgsConstructor
@Transactional
public class OutboxService implements OutboxPort {

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void createSagaEvent(String txId, String orderId, Order order) {
        // 在同一個 Transaction 中：
        // 1. 寫入初始交易日誌
        TransactionLogEntity initLog = TransactionLogEntity.create(
            txId, orderId, "SAGA", "U"
        );
        transactionLogRepository.save(initLog);
        
        // 2. 寫入 Outbox 事件
        OutboxEventEntity event = new OutboxEventEntity();
        event.setTxId(txId);
        event.setOrderId(orderId);
        event.setEventType("SAGA_START");
        event.setPayload(toJson(order));
        event.setCreatedAt(LocalDateTime.now());
        event.setProcessed(false);
        outboxEventRepository.save(event);
    }
    
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
```

### 4.2 Outbox Poller（單一）

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.poll.interval:500}")
    @Transactional
    public void pollAndProcess() {
        List<OutboxEventEntity> events = outboxEventRepository
            .findByProcessedFalseOrderByCreatedAtAsc();
        
        for (OutboxEventEntity event : events) {
            try {
                processEvent(event);
                event.setProcessed(true);
                event.setProcessedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event: {}", event.getId(), e);
                // 不標記為 processed，下次繼續處理
            }
        }
    }

    private void processEvent(OutboxEventEntity event) {
        if ("SAGA_START".equals(event.getEventType())) {
            // 透過 Camel 啟動 Saga
            Map<String, Object> headers = Map.of(
                "txId", event.getTxId(),
                "orderId", event.getOrderId()
            );
            producerTemplate.sendBodyAndHeaders(
                "direct:startSaga", 
                event.getPayload(), 
                headers
            );
        }
    }
}
```

---

## 5. Apache Camel Route 設計

### 5.1 動態服務順序 Route

```java
@Component
@RequiredArgsConstructor
public class OrderSagaRoute extends RouteBuilder {

    private final SagaConfigService sagaConfigService;
    private final TransactionLogPort transactionLogPort;
    private final WebSocketPort webSocketPort;

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
                transactionLogPort.recordStatus(txId, serviceName, "F", errorMsg);
                
                // 推送 WebSocket
                webSocketPort.sendStatus(txId, "FAILED", serviceName, 
                    "服務呼叫失敗: " + errorMsg);
            })
            .to("direct:rollback");

        // Main Saga Route
        from("direct:startSaga")
            .routeId("saga-start")
            .process(exchange -> {
                String txId = exchange.getIn().getHeader("txId", String.class);
                List<ServiceConfig> services = sagaConfigService.getActiveServiceOrder();
                exchange.setProperty("txId", txId);
                exchange.setProperty("serviceList", services);
                exchange.setProperty("serviceIndex", 0);
            })
            .to("direct:processNextService");

        // 動態處理下一個服務
        from("direct:processNextService")
            .routeId("process-next-service")
            .choice()
                .when(exchange -> {
                    int index = exchange.getProperty("serviceIndex", Integer.class);
                    List<?> services = exchange.getProperty("serviceList", List.class);
                    return index < services.size();
                })
                    .to("direct:callService")
                .otherwise()
                    .to("direct:sagaComplete")
            .end();

        // 呼叫單一服務
        from("direct:callService")
            .routeId("call-service")
            .process(exchange -> {
                int index = exchange.getProperty("serviceIndex", Integer.class);
                List<ServiceConfig> services = exchange.getProperty("serviceList", List.class);
                ServiceConfig config = services.get(index);
                
                String txId = exchange.getProperty("txId", String.class);
                String orderId = exchange.getIn().getHeader("orderId", String.class);
                
                exchange.setProperty("currentService", config.getName());
                exchange.setProperty("notifyUrl", config.getNotifyUrl());
                
                // 記錄 U 狀態
                transactionLogPort.recordStatus(txId, config.getName(), "U", null);
                
                // 推送 WebSocket
                webSocketPort.sendStatus(txId, "PROCESSING", config.getName(), 
                    "正在處理: " + config.getName());
            })
            .toD("${exchangeProperty.notifyUrl}")
            .process(exchange -> {
                String txId = exchange.getProperty("txId", String.class);
                String serviceName = exchange.getProperty("currentService", String.class);
                int index = exchange.getProperty("serviceIndex", Integer.class);
                
                // 記錄 S 狀態
                transactionLogPort.recordStatus(txId, serviceName, "S", null);
                
                // 推送 WebSocket
                webSocketPort.sendStatus(txId, "PROCESSING", serviceName, 
                    serviceName + " 處理成功");
                
                // 移動到下一個服務
                exchange.setProperty("serviceIndex", index + 1);
            })
            .to("direct:processNextService");

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
                
                // 取得需回滾的服務（狀態為 S，反向順序）
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
                
                ServiceConfig config = sagaConfigService.getServiceConfig(serviceName);
                exchange.setProperty("currentRollbackService", serviceName);
                exchange.setProperty("rollbackUrl", config.getRollbackUrl());
                exchange.setProperty("retryCount", 0);
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
                    
                    // 記錄 R 狀態
                    transactionLogPort.recordStatus(txId, serviceName, "R", null);
                    
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
                        Thread.sleep(1000 * (retryCount + 1)); // 簡單退避
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
                
                // 記錄 D 狀態
                transactionLogPort.recordStatus(txId, "SAGA", "D", null);
                
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

    public void startChecker(String txId, String orderId) {
        TransactionCheckerThread checker = new TransactionCheckerThread(
            txId, orderId, 
            transactionLogPort, 
            sagaConfigService,
            producerTemplate,
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
    private final Consumer<String> onComplete;
    
    private volatile boolean running = true;
    private static final long CHECK_INTERVAL = 1000; // 1 秒

    public TransactionCheckerThread(String txId, String orderId,
            TransactionLogPort transactionLogPort,
            SagaConfigService sagaConfigService,
            ProducerTemplate producerTemplate,
            Consumer<String> onComplete) {
        super("checker-" + txId);
        this.txId = txId;
        this.orderId = orderId;
        this.transactionLogPort = transactionLogPort;
        this.sagaConfigService = sagaConfigService;
        this.producerTemplate = producerTemplate;
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
                        
                    case HAS_FAILURE:
                        log.warn("Transaction {} has failure, triggering rollback", txId);
                        triggerRollback();
                        stopChecker();
                        break;
                        
                    case HAS_TIMEOUT:
                        log.warn("Transaction {} has timeout, triggering rollback", txId);
                        triggerRollback();
                        stopChecker();
                        break;
                        
                    case DONE:
                        log.info("Transaction {} rollback completed", txId);
                        stopChecker();
                        break;
                        
                    case ROLLBACK_FAILED:
                        log.error("Transaction {} rollback failed", txId);
                        stopChecker();
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
            }
        }
        
        onComplete.accept(txId);
        log.info("Checker thread stopped for txId: {}", txId);
    }

    private CheckResult checkTransaction() {
        Map<String, TransactionStatus> latestStatuses = 
            transactionLogPort.getLatestStatuses(txId);
        
        // 檢查是否有 D 狀態
        if (latestStatuses.values().stream()
                .anyMatch(s -> s == TransactionStatus.DONE)) {
            return CheckResult.DONE;
        }
        
        // 檢查是否有 RF 狀態
        if (latestStatuses.values().stream()
                .anyMatch(s -> s == TransactionStatus.ROLLBACK_FAILED)) {
            return CheckResult.ROLLBACK_FAILED;
        }
        
        // 檢查是否全部成功
        List<String> serviceNames = sagaConfigService.getActiveServiceNames();
        boolean allSuccess = serviceNames.stream()
            .allMatch(name -> latestStatuses.get(name) == TransactionStatus.SUCCESS);
        if (allSuccess) {
            return CheckResult.ALL_SUCCESS;
        }
        
        // 檢查是否有失敗
        if (latestStatuses.values().stream()
                .anyMatch(s -> s == TransactionStatus.FAILED)) {
            return CheckResult.HAS_FAILURE;
        }
        
        // 檢查是否有超時
        Map<String, Integer> timeouts = sagaConfigService.getActiveTimeouts();
        for (Map.Entry<String, TransactionStatus> entry : latestStatuses.entrySet()) {
            if (entry.getValue() == TransactionStatus.UNCOMMITTED) {
                LocalDateTime createdAt = transactionLogPort
                    .getStatusCreatedAt(txId, entry.getKey(), TransactionStatus.UNCOMMITTED);
                int timeout = timeouts.getOrDefault(entry.getKey(), 60);
                
                if (createdAt.plusSeconds(timeout).isBefore(LocalDateTime.now())) {
                    return CheckResult.HAS_TIMEOUT;
                }
            }
        }
        
        return CheckResult.IN_PROGRESS;
    }

    private void triggerRollback() {
        producerTemplate.sendBodyAndHeader(
            "direct:rollback", 
            null, 
            "txId", txId
        );
    }

    public void stopChecker() {
        this.running = false;
    }

    private enum CheckResult {
        ALL_SUCCESS,
        HAS_FAILURE,
        HAS_TIMEOUT,
        DONE,
        ROLLBACK_FAILED,
        IN_PROGRESS
    }
}
```

---

## 7. WebSocket 實作

### 7.1 WebSocket Config

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private OrderWebSocketHandler orderWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderWebSocketHandler, "/ws/orders/{txId}")
                .setAllowedOrigins("*");
    }
}
```

### 7.2 WebSocket Handler

```java
@Component
@Slf4j
public class OrderWebSocketHandler extends TextWebSocketHandler implements WebSocketPort {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public OrderWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String txId = extractTxId(session);
        sessions.put(txId, session);
        log.info("WebSocket connected for txId: {}", txId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String txId = extractTxId(session);
        sessions.remove(txId);
        log.info("WebSocket disconnected for txId: {}", txId);
    }

    @Override
    public void sendStatus(String txId, String status, String currentStep, String message) {
        WebSocketSession session = sessions.get(txId);
        if (session != null && session.isOpen()) {
            try {
                WebSocketMessage wsMessage = new WebSocketMessage(
                    txId, null, status, currentStep, message, LocalDateTime.now()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(wsMessage)));
            } catch (Exception e) {
                log.error("Failed to send WebSocket message for txId: {}", txId, e);
            }
        }
    }

    private String extractTxId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}

// WebSocketMessage DTO
@Data
@AllArgsConstructor
public class WebSocketMessage {
    private String txId;
    private String orderId;
    private String status;
    private String currentStep;
    private String message;
    private LocalDateTime timestamp;
}
```

---

## 8. Saga 恢復機制

### 8.1 Recovery Runner

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaRecoveryRunner implements ApplicationRunner {

    private final TransactionLogPort transactionLogPort;
    private final CheckerThreadManager checkerThreadManager;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting Saga recovery...");
        
        List<UnfinishedTransaction> unfinishedTxs = 
            transactionLogPort.findUnfinishedTransactions();
        
        log.info("Found {} unfinished transactions", unfinishedTxs.size());
        
        for (UnfinishedTransaction tx : unfinishedTxs) {
            log.info("Recovering transaction: {}", tx.getTxId());
            checkerThreadManager.startChecker(tx.getTxId(), tx.getOrderId());
        }
        
        log.info("Saga recovery completed");
    }
}
```

---

## 9. Email 通知實作

### 9.1 Notification Port

```java
public interface NotificationPort {
    void sendRollbackFailedNotification(String txId, String serviceName, String errorMessage);
}
```

### 9.2 Mock Email Adapter（開發階段）

```java
@Component
@Profile("dev")
@Slf4j
public class MockEmailNotificationAdapter implements NotificationPort {

    @Override
    public void sendRollbackFailedNotification(String txId, String serviceName, 
            String errorMessage) {
        log.warn("=== [MOCK EMAIL] ===");
        log.warn("To: admin@example.com");
        log.warn("Subject: [ALERT] Saga Rollback Failed - {}", txId);
        log.warn("Body: ");
        log.warn("  Transaction ID: {}", txId);
        log.warn("  Failed Service: {}", serviceName);
        log.warn("  Error: {}", errorMessage);
        log.warn("  Action Required: Manual intervention needed");
        log.warn("=== [END MOCK EMAIL] ===");
    }
}
```

### 9.3 Real Email Adapter（生產環境）

```java
@Component
@Profile("prod")
@RequiredArgsConstructor
public class EmailNotificationAdapter implements NotificationPort {

    private final JavaMailSender mailSender;
    
    @Value("${notification.email.to}")
    private String toAddress;
    
    @Value("${notification.email.from}")
    private String fromAddress;

    @Override
    public void sendRollbackFailedNotification(String txId, String serviceName, 
            String errorMessage) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toAddress);
        message.setFrom(fromAddress);
        message.setSubject("[ALERT] Saga Rollback Failed - " + txId);
        message.setText(String.format(
            "Transaction ID: %s%n" +
            "Failed Service: %s%n" +
            "Error: %s%n" +
            "Action Required: Manual intervention needed",
            txId, serviceName, errorMessage
        ));
        mailSender.send(message);
    }
}
```

---

## 10. 管理 API 實作

### 10.1 Saga Config Service

```java
@Service
@RequiredArgsConstructor
@Transactional
public class SagaConfigService implements SagaConfigUseCase {

    private final SagaConfigRepository configRepository;
    
    // 記憶體快取
    private volatile List<ServiceConfig> activeServiceOrder;
    private volatile Map<String, Integer> activeTimeouts;

    @PostConstruct
    public void loadActiveConfig() {
        reloadServiceOrder();
        reloadTimeouts();
    }

    @Override
    public List<ServiceConfig> getActiveServiceOrder() {
        return activeServiceOrder;
    }

    @Override
    public Map<String, Integer> getActiveTimeouts() {
        return activeTimeouts;
    }

    @Override
    public void updateServiceOrder(List<ServiceConfig> services) {
        SagaConfigEntity config = new SagaConfigEntity();
        config.setConfigType("SERVICE_ORDER");
        config.setConfigKey("pending");
        config.setConfigValue(toJson(services));
        config.setIsActive(false);
        config.setIsPending(true);
        configRepository.save(config);
    }

    @Override
    public void applyServiceOrder() {
        // 將 pending 設為 active
        configRepository.deactivateByType("SERVICE_ORDER");
        configRepository.activatePendingByType("SERVICE_ORDER");
        reloadServiceOrder();
    }

    @Override
    public void updateTimeouts(Map<String, Integer> timeouts) {
        SagaConfigEntity config = new SagaConfigEntity();
        config.setConfigType("TIMEOUT");
        config.setConfigKey("pending");
        config.setConfigValue(toJson(timeouts));
        config.setIsActive(false);
        config.setIsPending(true);
        configRepository.save(config);
    }

    @Override
    public void applyTimeouts() {
        configRepository.deactivateByType("TIMEOUT");
        configRepository.activatePendingByType("TIMEOUT");
        reloadTimeouts();
    }

    private void reloadServiceOrder() {
        Optional<SagaConfigEntity> config = 
            configRepository.findByConfigTypeAndIsActiveTrue("SERVICE_ORDER");
        activeServiceOrder = config
            .map(c -> fromJson(c.getConfigValue(), new TypeReference<List<ServiceConfig>>(){}))
            .orElse(getDefaultServiceOrder());
    }

    private void reloadTimeouts() {
        Optional<SagaConfigEntity> config = 
            configRepository.findByConfigTypeAndIsActiveTrue("TIMEOUT");
        activeTimeouts = config
            .map(c -> fromJson(c.getConfigValue(), new TypeReference<Map<String, Integer>>(){}))
            .orElse(getDefaultTimeouts());
    }

    private List<ServiceConfig> getDefaultServiceOrder() {
        return List.of(
            new ServiceConfig(1, "CREDIT_CARD", 
                "http://localhost:8081/api/v1/credit-card/notify",
                "http://localhost:8081/api/v1/credit-card/rollback"),
            new ServiceConfig(2, "INVENTORY",
                "http://localhost:8082/api/v1/inventory/notify",
                "http://localhost:8082/api/v1/inventory/rollback"),
            new ServiceConfig(3, "LOGISTICS",
                "http://localhost:8083/api/v1/logistics/notify",
                "http://localhost:8083/api/v1/logistics/rollback")
        );
    }

    private Map<String, Integer> getDefaultTimeouts() {
        return Map.of(
            "CREDIT_CARD", 30,
            "INVENTORY", 60,
            "LOGISTICS", 120
        );
    }
}
```

### 10.2 Admin Controller

```java
@RestController
@RequestMapping("/api/v1/admin/saga")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Saga 管理 API")
public class AdminController {

    private final SagaConfigUseCase sagaConfigUseCase;

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
}
```

---

## 11. 可觀測性實作

### 11.1 Saga Metrics

```java
@Component
@RequiredArgsConstructor
public class SagaMetrics {

    private final MeterRegistry meterRegistry;
    
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
    }

    public void recordSagaStarted() {
        sagaStartedCounter.increment();
    }

    public void recordSagaCompleted() {
        sagaCompletedCounter.increment();
    }

    public void recordSagaFailed() {
        sagaFailedCounter.increment();
    }

    public void recordSagaRolledBack() {
        sagaRolledBackCounter.increment();
    }

    public void recordSagaRollbackFailed() {
        sagaRollbackFailedCounter.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(sagaDurationTimer);
    }
}
```

### 11.2 Tracing Config

```java
@Configuration
public class TracingConfig {

    @Bean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }
}
```

---

## 12. 設定檔

### 12.1 application.yml

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

## 13. Gradle 建置設定

### 13.1 settings.gradle.kts

```kotlin
rootProject.name = "ecommerce-saga"

include("common")
include("order-service")
include("credit-card-service")
include("inventory-service")
include("logistics-service")
```

### 13.2 Root build.gradle.kts

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

## 14. 開發任務清單

### Phase 1: 基礎建設 (2 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 1.1 | 建立 Monorepo 專案結構 | P0 |
| 1.2 | 設定 Gradle 多模組建置 | P0 |
| 1.3 | 建立 common 模組 | P0 |
| 1.4 | 設定 H2 資料庫與 schema | P0 |

### Phase 2: 核心服務 (3 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 2.1 | 實作 TransactionLog Entity & Repository | P0 |
| 2.2 | 實作 Outbox Pattern（寫入 + Poller） | P0 |
| 2.3 | 實作 Order Service 六角形架構 | P0 |
| 2.4 | 實作 Credit Card Service（含冪等 rollback） | P1 |
| 2.5 | 實作 Inventory Service（含冪等 rollback） | P1 |
| 2.6 | 實作 Logistics Service（含冪等 rollback） | P1 |

### Phase 3: Camel Route (2 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 3.1 | 實作動態 OrderSagaRoute | P0 |
| 3.2 | 實作 RollbackRoute（含重試機制） | P0 |
| 3.3 | 實作各 Processor | P0 |
| 3.4 | 整合測試 Camel Route | P0 |

### Phase 4: Checker Thread & WebSocket (2 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 4.1 | 實作 CheckerThreadManager | P0 |
| 4.2 | 實作 TransactionCheckerThread | P0 |
| 4.3 | 實作 WebSocket Handler | P0 |
| 4.4 | 測試超時與回滾機制 | P0 |

### Phase 5: 管理 API & 恢復機制 (2 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 5.1 | 實作 SagaConfigService | P0 |
| 5.2 | 實作 AdminController（6 支 API） | P0 |
| 5.3 | 實作 SagaRecoveryRunner | P0 |
| 5.4 | 實作 Email 通知（Mock + Real） | P1 |

### Phase 6: 可觀測性 & 文件 (2 天)

| Task | 說明 | 優先序 |
|------|------|--------|
| 6.1 | 實作 SagaMetrics | P1 |
| 6.2 | 設定 Tracing | P1 |
| 6.3 | 設定 Swagger 文件 | P1 |
| 6.4 | 撰寫 Unit Tests | P0 |
| 6.5 | 撰寫 Integration Tests | P1 |
| 6.6 | 撰寫 README.md | P2 |

---

## 15. 附錄

### 15.1 狀態碼參照

| 狀態碼 | 名稱 | 說明 |
|--------|------|------|
| U | Uncommitted | 在途 |
| S | Success | 成功 |
| F | Failed | 失敗 |
| R | Rolled back | 已回滾 |
| D | Done | 回滾流程完成 |
| RF | Rollback Failed | 回滾失敗 |

### 15.2 API Endpoints 總覽

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/orders/confirm` | 確認訂單，啟動 Saga |
| GET | `/api/v1/transactions/{txId}` | 查詢交易狀態 |
| WS | `/ws/orders/{txId}` | WebSocket 狀態推送 |
| GET | `/api/v1/admin/saga/service-order` | 查詢服務順序 |
| PUT | `/api/v1/admin/saga/service-order` | 修改服務順序 |
| POST | `/api/v1/admin/saga/service-order/apply` | 觸發服務順序生效 |
| GET | `/api/v1/admin/saga/timeout` | 查詢超時設定 |
| PUT | `/api/v1/admin/saga/timeout` | 修改超時設定 |
| POST | `/api/v1/admin/saga/timeout/apply` | 觸發超時設定生效 |
| POST | `/api/v1/credit-card/notify` | 信用卡交易通知 |
| POST | `/api/v1/credit-card/rollback` | 信用卡交易回滾（冪等） |
| POST | `/api/v1/inventory/notify` | 倉管預留通知 |
| POST | `/api/v1/inventory/rollback` | 倉管預留回滾（冪等） |
| POST | `/api/v1/logistics/notify` | 物流排程通知 |
| POST | `/api/v1/logistics/rollback` | 物流排程回滾（冪等） |
