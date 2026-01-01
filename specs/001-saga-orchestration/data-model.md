# Data Model: E-Commerce Saga Orchestration System

**Date**: 2026-01-01
**Branch**: `001-saga-orchestration`

## Overview

This document defines the domain entities, value objects, and database schema for the Saga Orchestration System. The model follows Domain-Driven Design principles with clear separation between domain concepts and persistence concerns.

---

## 1. Domain Model

### 1.1 Entities

#### Order (Domain Entity)

Represents a customer order being processed through the saga.

| Attribute | Type | Description | Validation |
|-----------|------|-------------|------------|
| orderId | UUID | Unique order identifier | Required, immutable |
| customerId | String | Customer reference | Required |
| items | List<OrderItem> | Order line items | Non-empty |
| totalAmount | BigDecimal | Order total | > 0 |
| createdAt | LocalDateTime | Order creation time | Required, immutable |

#### TransactionLog (Domain Entity)

Immutable record of a saga state transition (Event Sourcing).

| Attribute | Type | Description | Validation |
|-----------|------|-------------|------------|
| id | Long | Auto-generated ID | System-managed |
| txId | UUID | Transaction identifier | Required, immutable |
| orderId | UUID | Associated order | Required, immutable |
| serviceName | ServiceName | Service being processed | Required |
| status | TransactionStatus | Current status | Required |
| errorMessage | String | Error details (if failed) | Optional |
| retryCount | Integer | Rollback retry attempts | Default: 0 |
| createdAt | LocalDateTime | Record creation time | Required, immutable |
| notifiedAt | LocalDateTime | Admin notification time | Optional |

#### SagaConfig (Domain Entity)

Runtime configuration for saga behavior.

| Attribute | Type | Description | Validation |
|-----------|------|-------------|------------|
| id | Long | Auto-generated ID | System-managed |
| configType | ConfigType | Type of configuration | Required |
| configKey | String | Configuration identifier | Required |
| configValue | String (JSON) | Configuration payload | Required |
| isActive | Boolean | Currently in use | Default: false |
| isPending | Boolean | Staged for activation | Default: false |
| createdAt | LocalDateTime | Creation timestamp | Required |
| updatedAt | LocalDateTime | Last update timestamp | Required |

### 1.2 Value Objects

#### TransactionStatus (Enum)

| Value | Code | Description |
|-------|------|-------------|
| UNCOMMITTED | U | Service call initiated, awaiting response |
| SUCCESS | S | Service responded successfully |
| FAILED | F | Service responded with error |
| ROLLED_BACK | R | Compensation executed successfully |
| DONE | D | Entire rollback flow completed |
| ROLLBACK_FAILED | RF | Compensation failed after max retries |

#### ServiceName (Enum)

| Value | Description |
|-------|-------------|
| CREDIT_CARD | Payment processing service |
| INVENTORY | Inventory reservation service |
| LOGISTICS | Shipping scheduling service |
| SAGA | Saga orchestration marker |

#### ConfigType (Enum)

| Value | Description |
|-------|-------------|
| SERVICE_ORDER | Execution order of downstream services |
| TIMEOUT | Timeout thresholds per service |

#### ServiceConfig (Value Object)

| Attribute | Type | Description |
|-----------|------|-------------|
| order | Integer | Execution sequence (1, 2, 3...) |
| name | String | Service name |
| notifyUrl | String | URL for forward call |
| rollbackUrl | String | URL for compensation call |

### 1.3 Domain Events

#### TransactionEvent

Published when saga state changes.

| Attribute | Type | Description |
|-----------|------|-------------|
| eventId | UUID | Unique event ID |
| txId | UUID | Transaction ID |
| orderId | UUID | Order ID |
| eventType | String | Event type (SAGA_START, SERVICE_SUCCESS, etc.) |
| serviceName | ServiceName | Affected service |
| status | TransactionStatus | New status |
| timestamp | LocalDateTime | Event timestamp |
| payload | String | Additional event data (JSON) |

---

## 2. State Transitions

### 2.1 Happy Path Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Happy Path State Flow                         │
└─────────────────────────────────────────────────────────────────┘

Order Confirm
     │
     ▼
┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐
│SAGA: U  │──►│CC: U    │──►│CC: S    │──►│INV: U   │──►
└─────────┘   └─────────┘   └─────────┘   └─────────┘
                                               │
     ┌─────────────────────────────────────────┘
     ▼
┌─────────┐   ┌─────────┐   ┌─────────┐
│INV: S   │──►│LOG: U   │──►│LOG: S   │──► Complete (All S)
└─────────┘   └─────────┘   └─────────┘
```

### 2.2 Failure & Rollback Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                 Failure & Rollback State Flow                    │
└─────────────────────────────────────────────────────────────────┘

                    CC: S exists
                         │
                         ▼
CC: S ──► INV: U ──► INV: F (failure detected)
                         │
                         ▼
               ┌─────────────────┐
               │ Trigger Rollback │
               └────────┬────────┘
                        │
                        ▼
               CC: R (rollback success)
                        │
                        ▼
               SAGA: D (all rollbacks done)
```

### 2.3 Rollback Failed Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                  Rollback Failure Flow                           │
└─────────────────────────────────────────────────────────────────┘

CC: R attempt 1 ──► FAILED
       │
       ▼
CC: R attempt 2 ──► FAILED
       │
       ▼
CC: R attempt 3 ──► FAILED
       │
       ▼
CC: R attempt 4 ──► FAILED
       │
       ▼
CC: R attempt 5 ──► FAILED
       │
       ▼
CC: RF (rollback_failed, retryCount=5)
       │
       ▼
Admin Notification Sent (notifiedAt set)
```

---

## 3. Database Schema (H2)

### 3.1 transaction_log

```sql
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

-- Performance indexes
CREATE INDEX idx_tx_service_status ON transaction_log (tx_id, service_name, status);
CREATE INDEX idx_status_created ON transaction_log (status, created_at);
CREATE INDEX idx_order_id ON transaction_log (order_id);
```

### 3.2 outbox_event

```sql
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
```

### 3.3 saga_config

```sql
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

---

## 4. Relationships

### 4.1 Entity Relationships

```
┌──────────────────────────────────────────────────────────────────┐
│                    Entity Relationship Diagram                    │
└──────────────────────────────────────────────────────────────────┘

┌─────────────┐       1:N        ┌─────────────────┐
│    Order    │─────────────────►│ TransactionLog  │
│  (orderId)  │                  │  (orderId FK)   │
└─────────────┘                  └─────────────────┘
      │                                  │
      │ 1:N                              │ N:1
      ▼                                  ▼
┌─────────────┐                  ┌─────────────────┐
│ OutboxEvent │                  │   Transaction   │
│  (orderId)  │                  │     (txId)      │
└─────────────┘                  └─────────────────┘

┌─────────────────┐
│   SagaConfig    │  (Independent - system configuration)
└─────────────────┘
```

### 4.2 Query Patterns

| Query | Purpose | Index Used |
|-------|---------|------------|
| `SELECT * FROM transaction_log WHERE tx_id = ? ORDER BY created_at` | Get all events for transaction | idx_tx_service_status |
| `SELECT * FROM transaction_log WHERE tx_id = ? AND service_name = ? ORDER BY created_at DESC LIMIT 1` | Get latest status per service | idx_tx_service_status |
| `SELECT DISTINCT tx_id FROM transaction_log WHERE status = 'U' AND created_at < ?` | Find timed-out transactions | idx_status_created |
| `SELECT * FROM outbox_event WHERE processed = FALSE ORDER BY created_at` | Poll unprocessed events | idx_outbox_processed |

---

## 5. Validation Rules

### 5.1 Business Rules

| Rule | Entity | Validation |
|------|--------|------------|
| VR-001 | TransactionLog | Status transitions must follow valid state machine |
| VR-002 | TransactionLog | Cannot UPDATE existing records (append-only) |
| VR-003 | TransactionLog | retryCount increments only on rollback retry |
| VR-004 | OutboxEvent | processed=true only after successful Camel route trigger |
| VR-005 | SagaConfig | Only one active config per (config_type, config_key) |

### 5.2 Valid Status Transitions

| From | To | Trigger |
|------|----|---------|
| - | U | Service call initiated |
| U | S | Service success response |
| U | F | Service error response or timeout |
| S | R | Compensation executed |
| R | RF | Max rollback retries exceeded |
| - | D | All rollbacks completed |

### 5.3 Invalid Transitions (must be rejected)

- S → U (cannot uncommit success)
- F → S (cannot succeed after failure)
- D → * (terminal state)
- RF → * (terminal state)

---

## 6. Data Volume Estimates

| Table | Est. Rows/Day | Retention | Notes |
|-------|---------------|-----------|-------|
| transaction_log | 10,000 - 50,000 | 90 days | ~3 events per service × 3 services per order |
| outbox_event | 3,000 - 15,000 | 7 days | 1 event per order, cleaned after processing |
| saga_config | < 100 | Indefinite | Configuration changes only |

---

## 7. Hexagonal Architecture Mapping

### 7.1 Domain Layer (Pure)

```java
// com.ecommerce.order.domain.model
public class Order { ... }           // Entity
public class TransactionLog { ... }  // Entity
public record ServiceConfig(...) {}  // Value Object

// com.ecommerce.order.domain.event
public class TransactionEvent { ... } // Domain Event

// com.ecommerce.common.domain
public enum TransactionStatus { U, S, F, R, D, RF }
public enum ServiceName { CREDIT_CARD, INVENTORY, LOGISTICS, SAGA }
```

### 7.2 Application Layer (Ports)

```java
// com.ecommerce.order.application.port.out
public interface TransactionLogPort {
    void recordStatus(String txId, String serviceName, String status, String error);
    List<String> findSuccessfulServices(String txId);
    Map<String, TransactionStatus> getLatestStatuses(String txId);
    List<UnfinishedTransaction> findUnfinishedTransactions();
}

public interface OutboxPort {
    void createSagaEvent(String txId, String orderId, Order order);
    List<OutboxEvent> findUnprocessed();
    void markProcessed(Long eventId);
}
```

### 7.3 Adapter Layer (JPA Entities)

```java
// com.ecommerce.order.adapter.out.persistence
@Entity
@Table(name = "transaction_log")
public class TransactionLogEntity { ... }  // JPA mapping

@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity { ... }     // JPA mapping
```
