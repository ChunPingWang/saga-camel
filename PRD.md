# 電子商務微服務交易編排系統 - 產品需求文件 (PRD)

> **版本**: 3.0  
> **建立日期**: 2026-01-01  
> **狀態**: Draft

---

## 1. 產品概述

### 1.1 背景

本系統為電子商務平台的核心交易處理系統，採用微服務架構處理訂單流程。當客戶下單購買商品（如 iPhone）時，系統需依序協調多個微服務完成交易，並確保分散式事務的一致性。

### 1.2 目標

- 實現可靠的分散式交易處理機制（Saga Pattern + Outbox Pattern）
- 確保跨微服務的資料一致性
- 提供完整的交易狀態追蹤與可觀測性
- 支援自動化的失敗補償與回滾機制
- 提供即時狀態通知（WebSocket）
- 支援動態配置服務順序與超時時間
- 支援動態加入/移出參與 Saga 的微服務
- 透過 Circuit Breaker 提升系統韌性

### 1.3 範圍

本系統涵蓋以下核心微服務的交易編排（可動態配置）：

| 微服務 | 職責 |
|--------|------|
| 訂單服務 (Order Service) | 接收客戶訂單，作為 Saga Orchestrator |
| 信用卡服務 (Credit Card Service) | 處理付款授權與扣款 |
| 倉管服務 (Inventory Service) | 處理庫存預留與扣減 |
| 物流服務 (Logistics Service) | 處理出貨排程與配送 |

### 1.4 ID 設計

| ID 類型 | 來源 | 格式 | 關係 | 用途 |
|---------|------|------|------|------|
| Order ID | 業務系統傳入 | `ORD-20260101-001` | 1 | 訂單業務編號，客戶可見 |
| TxID | 系統自動產生 | UUID | N | Saga 執行追蹤識別碼 |

**關係說明**：一個 Order ID 可對應多個 TxID（1:N），當訂單 Saga 執行失敗回滾後，可重新發起新的 Saga 執行。

---

## 2. 系統架構模式

### 2.1 核心設計模式

| 模式 | 說明 |
|------|------|
| Saga Pattern (Orchestration) | 由訂單服務作為編排器，控制交易流程 |
| Outbox Pattern | 確保 DB 寫入與訊息處理的原子性，採用異步模式 |
| Event Sourcing | 所有狀態變更以 INSERT 方式記錄，不做 UPDATE |
| Circuit Breaker | 防止級聯故障，提供快速失敗與自動恢復機制 |

### 2.2 異步處理流程

```
┌──────────┐    POST /confirm    ┌──────────────────────────────────────┐
│  Client  │ ──────────────────► │           Order Service              │
└──────────┘                     │  ┌─────────────────────────────────┐ │
     ▲                           │  │ 1. 產生 TxID                    │ │
     │                           │  │ 2. 寫入 Outbox (DB Transaction) │ │
     │ 202 Accepted              │  │ 3. 啟動 Checker Thread          │ │
     │ + WebSocket 連線          │  └─────────────────────────────────┘ │
     │                           └──────────────────────────────────────┘
     │                                          │
     │                                          ▼
     │                           ┌──────────────────────────────────────┐
     │   WebSocket 推送          │        Outbox Poller (單一)          │
     │   狀態變更通知            │  ┌─────────────────────────────────┐ │
     ◄───────────────────────────│  │ 依序呼叫（含 Circuit Breaker）：│ │
                                 │  │ 信用卡 → 倉管 → 物流            │ │
                                 │  └─────────────────────────────────┘ │
                                 └──────────────────────────────────────┘
```

### 2.3 Circuit Breaker 狀態

```
┌────────┐  失敗率 > 閾值   ┌────────┐  等待時間結束  ┌───────────┐
│ Closed │ ───────────────► │  Open  │ ─────────────► │ Half-Open │
└────────┘                  └────────┘                └───────────┘
     ▲                           │                         │
     │                           │ 直接快速失敗            │
     │                           ▼                         │
     │                      ┌─────────┐                    │
     │                      │ Fallback│                    │
     │                      └─────────┘                    │
     │                                                     │
     │  探測成功                                           │
     └─────────────────────────────────────────────────────┘
```

---

## 3. 使用者故事

### 3.1 主要流程 - 訂單成功

```
作為一個客戶
當我確認訂購 iPhone
我希望系統能依序完成付款、庫存預留、出貨排程
並透過 WebSocket 即時通知我每個步驟的進度
```

**驗收條件 (Acceptance Criteria)**:

```gherkin
Scenario: 訂單交易全部成功
  Given 客戶已登入系統
  And 購物車中有 1 台 iPhone
  And 客戶已建立 WebSocket 連線
  When 客戶確認訂購
  Then 系統回應 202 Accepted 並回傳 TxID
  And 系統寫入 Outbox 事件
  And 系統啟動專屬 Checker Thread 監控此交易
  And Outbox Poller 依序呼叫信用卡服務
  And WebSocket 推送「信用卡扣款成功」
  And Outbox Poller 呼叫倉管服務
  And WebSocket 推送「庫存預留成功」
  And Outbox Poller 呼叫物流服務
  And WebSocket 推送「出貨排程成功」
  And 交易狀態資料表記錄三個微服務狀態皆為 Success
  And Checker Thread 停止
  And WebSocket 推送「訂單交易完成」
```

### 3.2 補償流程 - 部分失敗需回滾

```
作為系統
當交易流程中任一微服務失敗
我需要自動回滾已成功的微服務
並透過 WebSocket 通知客戶交易失敗
```

**驗收條件 (Acceptance Criteria)**:

```gherkin
Scenario: 倉管服務失敗觸發回滾
  Given 訂單已建立，TxID 為 "TX-001"
  And 信用卡服務已成功扣款（狀態 Success）
  And 客戶已建立 WebSocket 連線
  When 倉管服務回應失敗
  Then 系統記錄倉管服務狀態為 Fail
  And WebSocket 推送「庫存預留失敗」
  And 系統記錄信用卡服務狀態為 Rollback
  And 系統呼叫信用卡服務的回滾 API（反向順序）
  And 系統記錄信用卡服務狀態為 RollbackDone
  And Checker Thread 確認所有參與服務皆為 RollbackDone 後停止
  And WebSocket 推送「訂單交易失敗，已回滾」
```

### 3.3 超時處理 - 在途狀態超時

```
作為系統
當任一微服務處於在途狀態(Pending)超過設定時間
我需要主動觸發回滾機制
```

**驗收條件 (Acceptance Criteria)**:

```gherkin
Scenario: 在途狀態超時觸發回滾
  Given 訂單 TxID "TX-002" 的信用卡服務狀態為 Success
  And 倉管服務狀態為 Pending
  And 倉管服務超時設定為 60 秒
  And 時間戳記已超過 60 秒
  When Checker Thread 偵測到超時
  Then 系統記錄倉管服務狀態為 Rollback
  And 系統呼叫倉管服務的回滾 API
  And 系統記錄倉管服務狀態為 RollbackDone
  And 系統記錄信用卡服務狀態為 Rollback
  And 系統呼叫信用卡服務的回滾 API（反向順序）
  And 系統記錄信用卡服務狀態為 RollbackDone
  And Checker Thread 確認所有參與服務皆為 RollbackDone 後停止
```

### 3.4 回滾失敗處理

```
作為系統
當回滾 API 呼叫失敗超過重試次數
我需要記錄錯誤並通知管理人員
```

**驗收條件 (Acceptance Criteria)**:

```gherkin
Scenario: 回滾失敗通知
  Given 訂單 TxID "TX-003" 需要回滾信用卡服務
  And 回滾 API 連續失敗 5 次
  When 重試次數達到上限
  Then 系統記錄信用卡服務狀態為 RollbackFail
  And 系統記錄錯誤訊息
  And 系統發送 Email 通知管理人員
  And 系統記錄通知時間 (notified_at)
  And Checker Thread 停止（需人工介入）
```

### 3.5 服務恢復處理

```
作為系統
當訂單服務重啟
我需要自動恢復未完成的交易
```

**驗收條件 (Acceptance Criteria)**:

```gherkin
Scenario: 服務重啟後恢復 Saga
  Given 系統中有未完成的交易（非終態）
  When 訂單服務重新啟動
  Then 系統掃描所有未完成交易
  And 為每筆交易重新啟動 Checker Thread
  And 繼續執行剩餘流程或回滾
```

### 3.6 Circuit Breaker 觸發

```
作為系統
當微服務連續失敗達到閾值
我需要透過 Circuit Breaker 快速失敗
避免持續呼叫不可用的服務
```

**驗收條件 (Acceptance Criteria)**:

```gherkin
Scenario: Circuit Breaker 開啟
  Given 信用卡服務連續失敗 5 次
  And Circuit Breaker 失敗率閾值為 50%
  When 下一筆訂單嘗試呼叫信用卡服務
  Then Circuit Breaker 狀態為 Open
  And 系統直接回傳失敗（不實際呼叫）
  And 系統記錄信用卡服務狀態為 Fail
  And 觸發回滾流程
```

### 3.7 交易狀態查詢

```
作為管理人員
我需要透過 Order ID 或 TxID 查詢交易狀態
以便追蹤訂單處理進度
```

**驗收條件 (Acceptance Criteria)**:

```gherkin
Scenario: 透過 Order ID 查詢交易狀態
  Given 訂單 "ORD-20260101-001" 有兩次 Saga 執行
  When 管理人員以 Order ID 查詢
  Then 系統回傳該訂單所有 TxID 的交易狀態
  And 每個 TxID 顯示各微服務的最新狀態

Scenario: 透過 TxID 查詢交易狀態
  Given TxID "550e8400-e29b-41d4-a716-446655440000" 存在
  When 管理人員以 TxID 查詢
  Then 系統回傳該 TxID 各微服務的最新狀態
```

### 3.8 動態管理參與服務

```
作為管理人員
我需要動態加入或移出參與 Saga 的微服務
以便因應業務需求調整交易流程
```

**驗收條件 (Acceptance Criteria)**:

```gherkin
Scenario: 加入新微服務
  Given 目前 Saga 參與服務為：信用卡、倉管、物流
  When 管理人員加入「紅利點數服務」至暫存區
  And 管理人員觸發 Apply
  Then 新訂單的 Saga 流程包含紅利點數服務
  And 進行中的訂單不受影響

Scenario: 移出微服務
  Given 目前 Saga 參與服務為：信用卡、倉管、物流
  When 管理人員移出「物流服務」至暫存區
  And 管理人員觸發 Apply
  Then 新訂單的 Saga 流程不包含物流服務
  And 進行中的訂單繼續使用原有配置
```

---

## 4. 功能需求

### 4.1 交易編排功能 (Apache Camel Route)

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-001 | 訂單確認 API | 接收客戶訂單確認請求，產生唯一 TxID，回應 202 Accepted |
| FR-002 | Outbox 寫入 | 將交易事件寫入 Outbox 表（與業務資料同一 Transaction） |
| FR-003 | Outbox Poller | 單一 Poller 從訂單服務讀取並處理 Outbox 事件 |
| FR-004 | 順序呼叫控制 | 依可配置順序呼叫微服務（預設：信用卡 → 倉管 → 物流） |
| FR-005 | 回應等待機制 | 每個微服務正確回應後才呼叫下一個 |
| FR-006 | 失敗回滾觸發 | 任一微服務失敗時，觸發補償流程 |

### 4.2 Circuit Breaker 功能

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-007 | 失敗率監控 | 監控各微服務的失敗率 |
| FR-008 | 斷路器開啟 | 失敗率超過閾值時開啟斷路器 |
| FR-009 | 快速失敗 | 斷路器開啟時直接回傳失敗 |
| FR-010 | 自動恢復探測 | 等待時間後進入 Half-Open 狀態探測 |
| FR-011 | 斷路器關閉 | 探測成功後關閉斷路器恢復正常 |

### 4.3 Checker Thread 功能

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-012 | Thread 啟動 | 每筆訂單觸發時啟動專屬 Checker Thread |
| FR-013 | 成功監控 | 所有參與服務皆為 Success 時，Thread 停止 |
| FR-014 | 回滾完成監控 | 所有參與服務皆為 RollbackDone 時，Thread 停止 |
| FR-015 | 回滾失敗監控 | 任一服務為 RollbackFail 時，Thread 停止 |
| FR-016 | 超時監控 | 任一微服務 Pending 狀態超過設定時間，觸發回滾 |
| FR-017 | 持續處理 | 非終態時持續監控與處理 |

### 4.4 交易狀態記錄功能

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-018 | Pending 狀態記錄 | 微服務被呼叫時，記錄狀態 Pending |
| FR-019 | Success 狀態記錄 | 微服務正確回應時，記錄狀態 Success |
| FR-020 | Fail 狀態記錄 | 微服務回應失敗時，記錄狀態 Fail |
| FR-021 | Rollback 狀態記錄 | 開始執行回滾 API 時，記錄狀態 Rollback |
| FR-022 | RollbackDone 狀態記錄 | 回滾 API 成功後，記錄狀態 RollbackDone |
| FR-023 | RollbackFail 狀態記錄 | 回滾重試失敗，記錄狀態 RollbackFail |
| FR-024 | Skipped 狀態記錄 | 服務被跳過時，記錄狀態 Skipped |

### 4.5 補償機制功能

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-025 | 即時失敗回滾 | 收到失敗回應時，立即回滾已成功(Success)的微服務 |
| FR-026 | 超時回滾 | Pending 狀態超過設定時間觸發回滾 |
| FR-027 | 回滾順序控制 | 回滾順序為成功順序的反向 |
| FR-028 | 回滾重試機制 | 回滾失敗時重試 5 次（可配置） |
| FR-029 | 回滾失敗通知 | 重試失敗後發送 Email 通知（開發階段模擬） |
| FR-030 | Rollback 冪等 | 各微服務 rollback API 支援冪等，無對應交易也回傳成功 |

### 4.6 WebSocket 通知功能

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-031 | WebSocket 連線 | 支援客戶端建立 WebSocket 連線 |
| FR-032 | 狀態推送 | 每個步驟狀態變更時推送通知 |
| FR-033 | 推送內容 | 包含 txId, orderId, status, currentStep, message, timestamp |

### 4.7 Saga 恢復功能

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-034 | 啟動掃描 | 服務啟動時掃描未完成交易 |
| FR-035 | Thread 恢復 | 為未完成交易重新啟動 Checker Thread |

### 4.8 交易狀態查詢功能

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-036 | Order ID 查詢 | 透過 Order ID 查詢所有相關 TxID 的交易狀態 |
| FR-037 | TxID 查詢 | 透過 TxID 查詢該交易各微服務的狀態 |

### 4.9 管理 API 功能 - 服務順序

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-038 | 查詢服務順序 | GET API 查詢目前生效的服務呼叫順序 |
| FR-039 | 修改服務順序 | PUT API 儲存新順序（暫存，未生效） |
| FR-040 | 觸發順序生效 | POST API 觸發暫存順序生效 |

### 4.10 管理 API 功能 - 超時設定

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-041 | 查詢超時設定 | GET API 查詢各服務超時時間 |
| FR-042 | 修改超時設定 | PUT API 儲存新超時設定（暫存，未生效） |
| FR-043 | 觸發超時生效 | POST API 觸發暫存超時設定生效 |

### 4.11 管理 API 功能 - 參與服務

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-044 | 查詢參與服務 | GET API 查詢目前參與 Saga 的微服務清單 |
| FR-045 | 加入微服務 | POST API 加入新微服務（暫存，未生效） |
| FR-046 | 移出微服務 | DELETE API 移出微服務（暫存，未生效） |
| FR-047 | 觸發服務生效 | POST API 觸發暫存服務清單生效 |

### 4.12 資料一致性功能

| 功能編號 | 功能名稱 | 說明 |
|----------|----------|------|
| FR-048 | Outbox Pattern | 業務資料與 Outbox 事件在同一 DB Transaction 完成 |
| FR-049 | Event Sourcing | 所有狀態變更以 INSERT 方式記錄，不做 UPDATE |

---

## 5. 交易狀態定義

### 5.1 狀態碼

| 狀態碼 | 全名 | 說明 |
|--------|------|------|
| Pending | Pending | 已呼叫微服務，等待回應 |
| Success | Success | 微服務處理成功 |
| Fail | Fail | 微服務處理失敗 |
| Rollback | Rollback | 正在執行回滾 API |
| RollbackDone | RollbackDone | 回滾 API 執行成功 |
| RollbackFail | RollbackFail | 回滾重試失敗，需人工介入 |
| Skipped | Skipped | 該服務在此交易中被跳過 |

### 5.2 狀態轉換規則

```
                    成功回應
┌─────────┐ ──────────────────────────────► ┌─────────┐
│ Pending │                                 │ Success │
└─────────┘                                 └─────────┘
     │                                           │
     │ 失敗回應                                  │ 後續服務失敗/超時
     ▼                                           ▼
┌─────────┐                                 ┌──────────┐
│  Fail   │                                 │ Rollback │
└─────────┘                                 └──────────┘
                                                 │
                              ┌──────────────────┼──────────────────┐
                              │ 回滾成功         │                  │ 回滾失敗(5次)
                              ▼                  │                  ▼
                       ┌──────────────┐          │          ┌──────────────┐
                       │ RollbackDone │          │          │ RollbackFail │
                       └──────────────┘          │          └──────────────┘
                                                 │
                              服務被移出/跳過    │
                              ┌──────────────────┘
                              ▼
                        ┌─────────┐
                        │ Skipped │
                        └─────────┘
```

### 5.3 Checker Thread 停止條件（終態）

| 條件 | 最終狀態 | 說明 |
|------|----------|------|
| 所有參與服務皆為 Success | 交易成功 | Thread 停止 |
| 所有參與服務皆為 RollbackDone (或 Skipped) | 回滾完成 | Thread 停止 |
| 任一服務為 RollbackFail | 需人工介入 | Thread 停止 |

**非終態**：除上述三種情況外，Checker Thread 持續監控與處理。

---

## 6. 交易狀態資料表規格

### 6.1 欄位定義

| 欄位名稱 | 資料型態 | 必填 | 說明 |
|----------|----------|------|------|
| id | BIGINT | Y | 主鍵，自動遞增 |
| tx_id | VARCHAR(36) | Y | 交易識別碼 (UUID) |
| order_id | VARCHAR(36) | Y | 關聯原始訂單編號 |
| service_name | VARCHAR(50) | Y | 微服務名稱 |
| status | VARCHAR(20) | Y | 執行狀態 |
| error_message | VARCHAR(500) | N | Fail / RollbackFail 狀態的錯誤訊息 |
| retry_count | INT | N | 回滾重試次數 |
| created_at | TIMESTAMP | Y | 記錄建立時間戳記 |
| notified_at | TIMESTAMP | N | RollbackFail 狀態發送通知的時間 |

### 6.2 狀態欄位值

```
Pending | Success | Fail | Rollback | RollbackDone | RollbackFail | Skipped
```

### 6.3 索引設計

- 主鍵索引：`id`
- 複合索引：`(tx_id, service_name, status)`
- 時間索引：`(status, created_at)` - 用於超時查詢
- 訂單索引：`(order_id)`

---

## 7. API 規格

### 7.1 訂單確認 API

```
POST /api/v1/orders/confirm
```

**Request Body**:
```json
{
  "orderId": "ORD-20260101-001",
  "customerId": "CUST-001",
  "items": [
    {
      "productId": "IPHONE-15-PRO",
      "quantity": 1,
      "unitPrice": 36900
    }
  ],
  "totalAmount": 36900
}
```

**Response (202 Accepted)**:
```json
{
  "txId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": "ORD-20260101-001",
  "status": "PROCESSING",
  "message": "訂單已受理，處理中",
  "websocketUrl": "/ws/orders/550e8400-e29b-41d4-a716-446655440000"
}
```

### 7.2 交易狀態查詢 API

```
GET /api/v1/transactions?orderId={orderId}
GET /api/v1/transactions?txId={txId}
```

**Response (透過 Order ID 查詢)**:
```json
{
  "orderId": "ORD-20260101-001",
  "transactions": [
    {
      "txId": "550e8400-e29b-41d4-a716-446655440000",
      "createdAt": "2026-01-01T10:00:00Z",
      "services": [
        { "name": "CREDIT_CARD", "status": "Success", "updatedAt": "..." },
        { "name": "INVENTORY", "status": "Success", "updatedAt": "..." },
        { "name": "LOGISTICS", "status": "Fail", "updatedAt": "...", "errorMessage": "..." }
      ],
      "overallStatus": "RollingBack"
    },
    {
      "txId": "660e8400-e29b-41d4-a716-446655440001",
      "createdAt": "2026-01-01T11:00:00Z",
      "services": [
        { "name": "CREDIT_CARD", "status": "Success", "updatedAt": "..." },
        { "name": "INVENTORY", "status": "Success", "updatedAt": "..." },
        { "name": "LOGISTICS", "status": "Success", "updatedAt": "..." }
      ],
      "overallStatus": "Completed"
    }
  ]
}
```

**Response (透過 TxID 查詢)**:
```json
{
  "txId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": "ORD-20260101-001",
  "createdAt": "2026-01-01T10:00:00Z",
  "services": [
    { "name": "CREDIT_CARD", "status": "RollbackDone", "updatedAt": "..." },
    { "name": "INVENTORY", "status": "RollbackDone", "updatedAt": "..." },
    { "name": "LOGISTICS", "status": "Fail", "updatedAt": "...", "errorMessage": "..." }
  ],
  "overallStatus": "RolledBack"
}
```

**overallStatus 可能值**:

| 值 | 說明 |
|----|------|
| Processing | 處理中 |
| Completed | 所有服務皆 Success |
| Failed | 有服務 Fail，尚未開始回滾 |
| RollingBack | 回滾進行中 |
| RolledBack | 所有服務皆 RollbackDone |
| RollbackFailed | 有服務 RollbackFail |

### 7.3 WebSocket 推送格式

**Endpoint**: `/ws/orders/{txId}`

**推送訊息格式**:
```json
{
  "txId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": "ORD-20260101-001",
  "status": "PROCESSING",
  "currentStep": "CREDIT_CARD",
  "message": "信用卡扣款成功",
  "timestamp": "2026-01-01T10:30:00Z"
}
```

### 7.4 微服務交易通知 API

每個微服務需實作以下 API：

**交易通知 API**:
```
POST /api/v1/{service}/notify
```

**回滾 API**（需支援冪等，無對應交易也回傳成功）:
```
POST /api/v1/{service}/rollback
```

| 微服務 | 交易通知 Endpoint | 回滾 Endpoint |
|--------|-------------------|---------------|
| 信用卡 | `/api/v1/credit-card/notify` | `/api/v1/credit-card/rollback` |
| 倉管 | `/api/v1/inventory/notify` | `/api/v1/inventory/rollback` |
| 物流 | `/api/v1/logistics/notify` | `/api/v1/logistics/rollback` |

### 7.5 管理 API - 服務順序

**查詢服務順序**:
```
GET /api/v1/admin/saga/service-order
```

**Response**:
```json
{
  "active": [
    { "order": 1, "name": "CREDIT_CARD", "notifyUrl": "...", "rollbackUrl": "..." },
    { "order": 2, "name": "INVENTORY", "notifyUrl": "...", "rollbackUrl": "..." },
    { "order": 3, "name": "LOGISTICS", "notifyUrl": "...", "rollbackUrl": "..." }
  ],
  "pending": null
}
```

**修改服務順序**:
```
PUT /api/v1/admin/saga/service-order
```

**觸發生效**:
```
POST /api/v1/admin/saga/service-order/apply
```

### 7.6 管理 API - 超時設定

**查詢超時設定**:
```
GET /api/v1/admin/saga/timeout
```

**Response**:
```json
{
  "active": {
    "CREDIT_CARD": 30,
    "INVENTORY": 60,
    "LOGISTICS": 120
  },
  "pending": null
}
```

**修改超時設定**:
```
PUT /api/v1/admin/saga/timeout
```

**觸發生效**:
```
POST /api/v1/admin/saga/timeout/apply
```

### 7.7 管理 API - 參與服務

**查詢參與服務**:
```
GET /api/v1/admin/saga/services
```

**Response**:
```json
{
  "active": [
    { "name": "CREDIT_CARD", "notifyUrl": "...", "rollbackUrl": "...", "timeout": 30 },
    { "name": "INVENTORY", "notifyUrl": "...", "rollbackUrl": "...", "timeout": 60 },
    { "name": "LOGISTICS", "notifyUrl": "...", "rollbackUrl": "...", "timeout": 120 }
  ],
  "pending": {
    "added": [],
    "removed": []
  }
}
```

**加入微服務**:
```
POST /api/v1/admin/saga/services
```

**Request Body**:
```json
{
  "name": "BONUS_POINT",
  "notifyUrl": "http://localhost:8084/api/v1/bonus-point/notify",
  "rollbackUrl": "http://localhost:8084/api/v1/bonus-point/rollback",
  "timeout": 30,
  "order": 2
}
```

**移出微服務**:
```
DELETE /api/v1/admin/saga/services/{serviceName}
```

**觸發生效**:
```
POST /api/v1/admin/saga/services/apply
```

---

## 8. 業務規則

### 8.1 呼叫順序規則

1. 訂單服務收到確認請求後，產生 TxID
2. 寫入 Outbox 事件（同一 DB Transaction）
3. 回應 202 Accepted
4. 啟動專屬 Checker Thread
5. Outbox Poller（單一）依可配置順序呼叫微服務
6. 預設順序：信用卡 → 倉管 → 物流
7. 每個微服務呼叫前先檢查 Circuit Breaker 狀態
8. 每個微服務必須正確回應後，才能呼叫下一個
9. 全部成功後，所有參與服務狀態皆為 Success

### 8.2 回滾規則

1. 任一微服務回應失敗（Fail）或超時（Pending 過久），觸發回滾流程
2. 回滾對象：同一 TxID 且狀態為 Success 的微服務
3. 回滾前先記錄狀態 Rollback
4. 回滾順序：成功順序的反向（物流 → 倉管 → 信用卡）
5. 每個回滾呼叫成功後，記錄狀態 RollbackDone
6. 回滾 API 需支援冪等：無對應交易也回傳成功

### 8.3 回滾重試規則

1. 回滾 API 失敗時，最多重試 5 次（可配置）
2. 重試採用指數退避策略
3. 重試 5 次仍失敗，記錄狀態 RollbackFail
4. 記錄錯誤訊息至 error_message 欄位
5. 發送 Email 通知管理人員
6. 記錄通知時間至 notified_at 欄位
7. 開發階段：Email 以模擬方式實作，保留可配置介面

### 8.4 超時規則

1. 各微服務可分別設定超時時間
2. 預設超時時間：
   - 信用卡：30 秒
   - 倉管：60 秒
   - 物流：120 秒
3. Checker Thread 監控各服務的 Pending 狀態時間
4. 超時後觸發回滾流程

### 8.5 Checker Thread 規則

1. 每筆訂單啟動一個專屬 Checker Thread
2. 持續監控該 TxID 的交易狀態
3. **終態條件（Thread 停止）**：
   - 所有參與服務皆為 Success（交易成功）
   - 所有參與服務皆為 RollbackDone 或 Skipped（回滾完成）
   - 任一服務為 RollbackFail（需人工介入）
4. 非終態時持續處理，不可停止
5. 不需要分散式鎖（每訂單單一 Thread）

### 8.6 服務恢復規則

1. 訂單服務啟動時，掃描所有未完成交易
2. 未完成定義：非終態的交易
3. 為每筆未完成交易重新啟動 Checker Thread
4. 繼續執行剩餘流程或回滾

### 8.7 Circuit Breaker 規則

1. 每個微服務獨立的 Circuit Breaker
2. 預設配置：
   - 失敗率閾值：50%
   - 滑動視窗大小：10 次呼叫
   - Open 狀態等待時間：30 秒
   - Half-Open 允許呼叫次數：3 次
3. 斷路器 Open 時直接回傳失敗，視為服務 Fail
4. Half-Open 狀態下探測成功則關閉斷路器

### 8.8 動態服務管理規則

1. 加入/移出服務需先儲存至暫存區
2. 透過 Apply API 觸發生效
3. 新配置僅影響新訂單，進行中訂單不受影響
4. 每筆交易記錄當時參與的服務快照

---

## 9. 非功能需求

### 9.1 效能需求

| 項目 | 指標 |
|------|------|
| 訂單受理回應時間 | < 200ms（寫入 Outbox） |
| 單筆訂單處理時間 | < 3 秒（不含微服務回應時間） |
| 併發訂單處理 | 支援 100 筆/秒 |

### 9.2 可用性需求

| 項目 | 指標 |
|------|------|
| 系統可用性 | 99.9% |
| 交易狀態查詢 | 即時可查 |
| 服務重啟恢復 | 自動恢復未完成交易 |
| Circuit Breaker | 自動熔斷與恢復 |

### 9.3 可觀測性需求

| 項目 | 說明 |
|------|------|
| Metrics | Saga 成功率、失敗率、平均處理時間、超時率、Circuit Breaker 狀態 |
| Logging | 結構化日誌，記錄每個步驟的執行狀態 |
| Tracing | 分散式追蹤（Micrometer Tracing） |

---

## 10. 詞彙表

| 術語 | 定義 |
|------|------|
| TxID | Transaction ID，Saga 執行識別碼，使用 UUID 格式 |
| Order ID | 訂單業務編號，一個 Order ID 可對應多個 TxID |
| Saga Pattern | 分散式交易管理模式，透過補償機制確保最終一致性 |
| Orchestration | 編排模式，由中央協調器控制服務呼叫順序 |
| Outbox Pattern | 確保 DB 操作與訊息處理原子性的設計模式 |
| Event Sourcing | 以事件序列記錄狀態變更的設計模式 |
| Circuit Breaker | 斷路器模式，防止級聯故障的設計模式 |
| Checker Thread | 每筆訂單專屬的監控執行緒，負責監控交易狀態與超時 |
| Poller | 輪詢程式，定期讀取 Outbox 並處理事件 |
| 終態 | Checker Thread 可停止的狀態（全 Success / 全 RollbackDone / 有 RollbackFail） |

---

## 11. 附錄

### 11.1 異步交易流程圖

```
┌──────────┐    ┌────────────┐    ┌────────────┐    ┌────────────┐    ┌────────────┐
│  Client  │    │   Order    │    │ CreditCard │    │ Inventory  │    │ Logistics  │
└────┬─────┘    └─────┬──────┘    └─────┬──────┘    └─────┬──────┘    └─────┬──────┘
     │                │                 │                 │                 │
     │ POST /confirm  │                 │                 │                 │
     │───────────────►│                 │                 │                 │
     │                │                 │                 │                 │
     │                │ [寫入 Outbox]   │                 │                 │
     │                │ [啟動 Checker]  │                 │                 │
     │                │                 │                 │                 │
     │  202 Accepted  │                 │                 │                 │
     │◄───────────────│                 │                 │                 │
     │                │                 │                 │                 │
     │  [WebSocket]   │ ══════════════════════════════════════════════════ │
     │◄═══════════════│ Poller 處理    │                 │                 │
     │                │ [Circuit Check]│                 │                 │
     │                │ [記錄 Pending] │                 │                 │
     │                │ POST /notify    │                 │                 │
     │                │────────────────►│                 │                 │
     │                │    200 OK       │                 │                 │
     │                │◄────────────────│                 │                 │
     │  [推送: 扣款成功]                │                 │                 │
     │◄═══════════════│ [記錄 Success] │                 │                 │
     │                │                 │                 │                 │
     │                │ [Circuit Check]│                 │                 │
     │                │ [記錄 Pending] │ POST /notify    │                 │
     │                │─────────────────────────────────►│                 │
     │                │                 │    200 OK       │                 │
     │                │◄─────────────────────────────────│                 │
     │  [推送: 庫存成功]                │                 │                 │
     │◄═══════════════│ [記錄 Success] │                 │                 │
     │                │                 │                 │                 │
     │                │ [Circuit Check]│                 │ POST /notify    │
     │                │────────────────────────────────────────────────────►│
     │                │                 │                 │    200 OK       │
     │                │◄────────────────────────────────────────────────────│
     │  [推送: 出貨成功]                │                 │                 │
     │◄═══════════════│ [記錄 Success] │                 │                 │
     │                │ [Checker: 全Success, 停止]       │                 │
     │  [推送: 完成]  │                 │                 │                 │
     │◄═══════════════│                 │                 │                 │
```

### 11.2 回滾流程圖

```
┌────────────┐    ┌────────────┐    ┌────────────┐    ┌────────────┐
│   Order    │    │ CreditCard │    │ Inventory  │    │ Logistics  │
└─────┬──────┘    └─────┬──────┘    └─────┬──────┘    └─────┬──────┘
      │                 │                 │                 │
      │                 │ [狀態: Success] │ [狀態: Success] │
      │                 │                 │                 │
      │ POST /notify    │                 │                 │
      │─────────────────────────────────────────────────────►│
      │                 │                 │    500 Error    │
      │◄─────────────────────────────────────────────────────│
      │ [記錄 Fail]     │                 │                 │
      │                 │                 │                 │
      │ ══════════════ 反向回滾 ══════════════               │
      │                 │                 │                 │
      │ [記錄 Rollback] │                 │                 │
      │                 │ POST /rollback  │                 │
      │─────────────────────────────────►│                 │
      │                 │    200 OK       │                 │
      │◄─────────────────────────────────│                 │
      │ [記錄 RollbackDone]              │                 │
      │                 │                 │                 │
      │ [記錄 Rollback] │                 │                 │
      │ POST /rollback  │                 │                 │
      │────────────────►│                 │                 │
      │    200 OK       │                 │                 │
      │◄────────────────│                 │                 │
      │ [記錄 RollbackDone]              │                 │
      │ [Checker: 全RollbackDone, 停止]  │                 │
```

### 11.3 Circuit Breaker 流程

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Circuit Breaker 狀態機                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌──────────┐                                      ┌──────────┐        │
│   │  Closed  │ ─────失敗率 > 50%─────────────────► │   Open   │        │
│   │ (正常)   │                                      │ (熔斷)   │        │
│   └──────────┘                                      └──────────┘        │
│        ▲                                                  │             │
│        │                                                  │             │
│        │ 探測成功                              等待 30 秒 │             │
│        │                                                  │             │
│        │            ┌─────────────┐                       │             │
│        └────────────│  Half-Open  │◄──────────────────────┘             │
│                     │  (半開)     │                                     │
│                     └─────────────┘                                     │
│                           │                                             │
│                           │ 探測失敗                                    │
│                           ▼                                             │
│                     ┌──────────┐                                        │
│                     │   Open   │                                        │
│                     └──────────┘                                        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 11.4 管理 API 流程

```
┌─────────────────────────────────────────────────────────────────┐
│              服務順序 / 超時設定 / 參與服務 管理                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   GET (查詢)     POST/PUT/DELETE (修改)    POST /apply (生效)   │
│       │                   │                      │              │
│       ▼                   ▼                      ▼              │
│  ┌─────────┐         ┌─────────┐           ┌─────────┐         │
│  │ Active  │         │ Pending │ ────────► │ Active  │         │
│  │ 設定區  │         │ 暫存區  │   生效    │ 設定區  │         │
│  └─────────┘         └─────────┘           └─────────┘         │
│       │                                          │              │
│       └──────────────────────────────────────────┘              │
│                    新訂單使用 Active 設定                        │
│                    進行中訂單不受影響                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```
