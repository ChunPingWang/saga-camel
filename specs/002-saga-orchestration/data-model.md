# Data Model: E-Commerce Saga Orchestration System

**Feature**: 002-saga-orchestration
**Date**: 2026-01-01
**Status**: Complete

## Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Data Model Overview                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────┐                      ┌──────────────────┐             │
│  │     Order        │                      │ TransactionLog   │             │
│  │  (Order ID)      │ 1 ─────────────── N  │  (Event Sourced) │             │
│  ├──────────────────┤                      ├──────────────────┤             │
│  │ orderId (PK)     │                      │ id (PK)          │             │
│  │ customerId       │                      │ txId (FK)        │             │
│  │ totalAmount      │                      │ orderId          │             │
│  │ items[]          │                      │ serviceName      │             │
│  │ createdAt        │                      │ status           │             │
│  └──────────────────┘                      │ errorMessage     │             │
│           │                                │ retryCount       │             │
│           │                                │ createdAt        │             │
│           │ triggers                       │ notifiedAt       │             │
│           ▼                                └──────────────────┘             │
│  ┌──────────────────┐                                │                      │
│  │   OutboxEvent    │                                │ grouped by           │
│  ├──────────────────┤                                ▼                      │
│  │ id (PK)          │                      ┌──────────────────┐             │
│  │ txId             │                      │ Transaction      │             │
│  │ orderId          │                      │   (View)         │             │
│  │ eventType        │                      ├──────────────────┤             │
│  │ payload (JSON)   │                      │ txId             │             │
│  │ processed        │                      │ orderId          │             │
│  │ createdAt        │                      │ overallStatus    │             │
│  │ processedAt      │                      │ services[]       │             │
│  └──────────────────┘                      │ createdAt        │             │
│                                            └──────────────────┘             │
│                                                                              │
│  ┌──────────────────┐         ┌──────────────────────────────┐              │
│  │  SagaConfig      │         │ TransactionServiceSnapshot   │              │
│  ├──────────────────┤         ├──────────────────────────────┤              │
│  │ id (PK)          │         │ id (PK)                      │              │
│  │ configType       │         │ txId                         │              │
│  │ configKey        │         │ serviceOrder (JSON)          │              │
│  │ configValue(JSON)│         │ createdAt                    │              │
│  │ isActive         │         └──────────────────────────────┘              │
│  │ isPending        │                                                       │
│  │ createdAt        │                                                       │
│  │ updatedAt        │                                                       │
│  └──────────────────┘                                                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Entities

### 1. TransactionLog (Event Store)

Primary entity for Event Sourcing. Records all state transitions as immutable INSERT-only events.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Unique event identifier |
| txId | VARCHAR(36) | NOT NULL, INDEXED | Transaction UUID (saga execution ID) |
| orderId | VARCHAR(36) | NOT NULL, INDEXED | Business order identifier |
| serviceName | VARCHAR(50) | NOT NULL | Service name: CREDIT_CARD, INVENTORY, LOGISTICS |
| status | VARCHAR(20) | NOT NULL, CHECK | Transaction status enum |
| errorMessage | VARCHAR(500) | NULLABLE | Error details for Fail/RollbackFail |
| retryCount | INT | NULLABLE, DEFAULT 0 | Number of rollback retry attempts |
| createdAt | TIMESTAMP | NOT NULL, DEFAULT NOW | Event creation timestamp |
| notifiedAt | TIMESTAMP | NULLABLE | Admin notification timestamp for RollbackFail |

**Status Values** (TransactionStatus enum):
```java
public enum TransactionStatus {
    Pending,       // Service called, awaiting response
    Success,       // Service processed successfully
    Fail,          // Service processing failed
    Rollback,      // Rollback initiated
    RollbackDone,  // Rollback completed successfully
    RollbackFail,  // Rollback failed after max retries
    Skipped        // Service skipped (not called)
}
```

**Indexes**:
- `idx_tx_service_status`: (tx_id, service_name, status) - Latest status lookup
- `idx_status_created`: (status, created_at) - Timeout detection
- `idx_order_id`: (order_id) - Order ID queries
- `idx_tx_id`: (tx_id) - Transaction queries

---

### 2. OutboxEvent

Transactional outbox for reliable event publishing.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Event identifier |
| txId | VARCHAR(36) | NOT NULL | Transaction UUID |
| orderId | VARCHAR(36) | NOT NULL | Business order identifier |
| eventType | VARCHAR(100) | NOT NULL | Event type: ORDER_CONFIRMED, etc. |
| payload | CLOB | NOT NULL | JSON event payload |
| processed | BOOLEAN | NOT NULL, DEFAULT FALSE | Processing status |
| createdAt | TIMESTAMP | NOT NULL, DEFAULT NOW | Event creation time |
| processedAt | TIMESTAMP | NULLABLE | Processing completion time |

**Indexes**:
- `idx_outbox_processed`: (processed, created_at) - Poller query

---

### 3. SagaConfig

Dynamic configuration storage for service order, timeouts, and service registry.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Config identifier |
| configType | VARCHAR(50) | NOT NULL | Type: SERVICE_ORDER, TIMEOUT, SERVICES |
| configKey | VARCHAR(100) | NOT NULL | Key: active, pending |
| configValue | CLOB | NOT NULL | JSON configuration value |
| isActive | BOOLEAN | NOT NULL, DEFAULT FALSE | Active configuration flag |
| isPending | BOOLEAN | NOT NULL, DEFAULT FALSE | Pending configuration flag |
| createdAt | TIMESTAMP | NOT NULL, DEFAULT NOW | Creation timestamp |
| updatedAt | TIMESTAMP | NOT NULL, DEFAULT NOW | Last update timestamp |

**Indexes**:
- `idx_config_type_key_active`: UNIQUE (config_type, config_key, is_active)

**Config Types**:
- `SERVICE_ORDER`: Service execution sequence
- `TIMEOUT`: Per-service timeout settings
- `SERVICES`: Full service registry with URLs

---

### 4. TransactionServiceSnapshot

Captures service configuration at transaction initiation time.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Snapshot identifier |
| txId | VARCHAR(36) | NOT NULL, INDEXED | Transaction UUID |
| serviceOrder | CLOB | NOT NULL | JSON array of ServiceConfig |
| createdAt | TIMESTAMP | NOT NULL, DEFAULT NOW | Snapshot creation time |

**Indexes**:
- `idx_snapshot_tx`: (tx_id)

---

## Value Objects

### ServiceConfig

Configuration for a single participating service.

```java
public record ServiceConfig(
    int order,              // Execution order (1, 2, 3, ...)
    String name,            // Service identifier (CREDIT_CARD, etc.)
    String notifyUrl,       // Transaction notification endpoint
    String rollbackUrl,     // Rollback endpoint
    int timeoutSeconds,     // Timeout in seconds
    boolean active          // Whether service is active
) {}
```

### TransactionEvent (Domain Event)

Represents an order confirmation event.

```java
public record TransactionEvent(
    String txId,
    String orderId,
    String customerId,
    List<OrderItem> items,
    BigDecimal totalAmount,
    String paymentToken,
    LocalDateTime createdAt
) {}
```

### WebSocketMessage

Real-time notification payload.

```java
public record WebSocketMessage(
    String txId,
    String orderId,
    String status,          // PROCESSING, COMPLETED, ROLLING_BACK, etc.
    String currentStep,     // Current service name
    String message,         // Human-readable message
    LocalDateTime timestamp
) {}
```

---

## State Transitions

### Transaction Status State Machine

```
                     Success Response
┌─────────┐ ────────────────────────────────► ┌─────────┐
│ Pending │                                   │ Success │
└─────────┘                                   └─────────┘
     │                                             │
     │ Failure/Timeout                             │ Later service fails
     ▼                                             ▼
┌─────────┐                                   ┌──────────┐
│  Fail   │                                   │ Rollback │
└─────────┘                                   └──────────┘
                                                   │
                              ┌────────────────────┼────────────────────┐
                              │ Success            │                    │ Max retries
                              ▼                    │                    ▼
                       ┌──────────────┐            │          ┌──────────────┐
                       │ RollbackDone │            │          │ RollbackFail │
                       └──────────────┘            │          └──────────────┘
                                                   │
                                      Service removed/skipped
                                                   │
                                                   ▼
                                             ┌─────────┐
                                             │ Skipped │
                                             └─────────┘
```

### Terminal States (Checker Thread Stops)

| Condition | Terminal State | Description |
|-----------|----------------|-------------|
| All services Success | Transaction Complete | Happy path |
| All services RollbackDone or Skipped | Rollback Complete | Clean rollback |
| Any service RollbackFail | Intervention Required | Admin notified |

---

## Validation Rules

### TransactionLog
- `txId`: Must be valid UUID format
- `orderId`: Must be non-empty, max 36 characters
- `serviceName`: Must be one of registered service names
- `status`: Must be valid TransactionStatus enum value
- `errorMessage`: Max 500 characters

### OutboxEvent
- `txId`: Must be valid UUID format
- `payload`: Must be valid JSON

### SagaConfig
- `configValue`: Must be valid JSON matching expected schema per configType
- Cannot have both `isActive` and `isPending` true for same type/key

### ServiceConfig
- `order`: Must be >= 1
- `name`: Must be non-empty, unique within config
- `notifyUrl`: Must be valid URL format
- `rollbackUrl`: Must be valid URL format
- `timeoutSeconds`: Must be > 0

---

## Database Schema (DDL)

```sql
-- Transaction Log (Event Sourced)
CREATE TABLE transaction_log (
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

CREATE INDEX idx_tx_service_status ON transaction_log (tx_id, service_name, status);
CREATE INDEX idx_status_created ON transaction_log (status, created_at);
CREATE INDEX idx_order_id ON transaction_log (order_id);
CREATE INDEX idx_tx_id ON transaction_log (tx_id);

-- Outbox Events
CREATE TABLE outbox_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_id           VARCHAR(36) NOT NULL,
    order_id        VARCHAR(36) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         CLOB NOT NULL,
    processed       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at    TIMESTAMP
);

CREATE INDEX idx_outbox_processed ON outbox_event (processed, created_at);

-- Saga Configuration
CREATE TABLE saga_config (
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

-- Transaction Service Snapshot
CREATE TABLE transaction_service_snapshot (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_id           VARCHAR(36) NOT NULL,
    service_order   CLOB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshot_tx ON transaction_service_snapshot (tx_id);
```

---

## Query Patterns

### Get Latest Status per Service for Transaction
```sql
SELECT tl.* FROM transaction_log tl
INNER JOIN (
    SELECT tx_id, service_name, MAX(created_at) as max_created
    FROM transaction_log
    WHERE tx_id = ?
    GROUP BY tx_id, service_name
) latest ON tl.tx_id = latest.tx_id
        AND tl.service_name = latest.service_name
        AND tl.created_at = latest.max_created;
```

### Find Pending Services Exceeding Timeout
```sql
SELECT * FROM transaction_log
WHERE status = 'Pending'
  AND created_at < (CURRENT_TIMESTAMP - INTERVAL '?' SECOND);
```

### Get Incomplete Transactions (for Recovery)
```sql
SELECT DISTINCT tx_id FROM transaction_log t1
WHERE NOT EXISTS (
    SELECT 1 FROM transaction_log t2
    WHERE t2.tx_id = t1.tx_id
      AND t2.status IN ('RollbackFail')
)
AND tx_id NOT IN (
    -- Exclude completed transactions
    SELECT tx_id FROM transaction_service_snapshot snap
    WHERE (
        SELECT COUNT(DISTINCT service_name)
        FROM transaction_log
        WHERE tx_id = snap.tx_id AND status = 'Success'
    ) = JSON_LENGTH(snap.service_order)
);
```
