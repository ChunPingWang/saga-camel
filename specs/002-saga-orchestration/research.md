# Research: E-Commerce Saga Orchestration System

**Feature**: 002-saga-orchestration
**Date**: 2026-01-01
**Status**: Complete

## Overview

This document consolidates research findings for implementing the Saga Orchestration System. All technical decisions are informed by the TECH.md specification, PRD requirements, and project constitution.

---

## 1. Saga Pattern Implementation

### Decision: Orchestration with Apache Camel

**Rationale**:
- Apache Camel provides mature EIP (Enterprise Integration Patterns) support
- Built-in error handling, retry mechanisms, and route composition
- Spring Boot integration with Camel Starter
- Dynamic routing capabilities for configurable service order

**Alternatives Considered**:
| Alternative | Reason Rejected |
|-------------|-----------------|
| Choreography (event-driven) | Requires message broker, harder to track transaction state |
| Custom orchestrator | Reinventing the wheel, less maintainable |
| Spring State Machine | More complex for sequential saga, overkill for this use case |

**Implementation Pattern**:
```
from("direct:startSaga")
    .process(initializeTransaction)
    .loop(serviceCount)
        .process(checkCircuitBreaker)
        .toD("${currentServiceUrl}")
        .process(recordSuccess)
    .end()
    .to("direct:sagaComplete");
```

---

## 2. Outbox Pattern Implementation

### Decision: Polling-based Outbox with Single Poller

**Rationale**:
- Ensures atomicity between business data and event publication
- Single poller prevents duplicate processing without distributed locks
- H2 database transactions guarantee consistency

**Alternatives Considered**:
| Alternative | Reason Rejected |
|-------------|-----------------|
| CDC (Change Data Capture) | Requires additional infrastructure (Debezium) |
| Transaction outbox with triggers | H2 trigger support limited |
| Immediate event dispatch | Loses atomicity guarantee |

**Implementation Details**:
- Outbox table stores pending events with `processed` flag
- Poller runs every 500ms (configurable)
- Mark-then-process pattern ensures at-least-once delivery
- Services must be idempotent for retry safety

---

## 3. Circuit Breaker Integration

### Decision: Resilience4j with Per-Service Breakers

**Rationale**:
- Native Spring Boot 3 support
- Lightweight, no external dependencies
- Configurable per service (different thresholds possible)
- Built-in metrics via Micrometer

**Configuration (from TECH.md)**:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      CREDIT_CARD:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
        minimum-number-of-calls: 5
```

**State Transitions**:
- CLOSED → OPEN: When failure rate > 50% in 10-call window
- OPEN → HALF_OPEN: After 30 seconds wait
- HALF_OPEN → CLOSED: If 3 probe calls succeed
- HALF_OPEN → OPEN: If any probe call fails

---

## 4. Checker Thread Design

### Decision: Dedicated Thread per Transaction

**Rationale**:
- Simple, no distributed coordination needed
- Natural fit for per-transaction timeout monitoring
- Daemon threads auto-terminate on JVM shutdown

**Alternatives Considered**:
| Alternative | Reason Rejected |
|-------------|-----------------|
| Scheduled job scanning all transactions | Higher latency, complex locking |
| Virtual threads (Project Loom) | Simpler for I/O-bound, but we need persistent monitoring |
| Actor model (Akka) | Overkill, adds major dependency |

**Thread Lifecycle**:
1. Created when order confirmed
2. Polls transaction state every 1 second
3. Terminates on terminal state: ALL_SUCCESS, ALL_ROLLBACK_DONE, or HAS_ROLLBACK_FAIL

---

## 5. WebSocket Real-time Updates

### Decision: Spring WebSocket with STOMP

**Rationale**:
- Spring Boot native support
- Simple message broker (in-memory for POC)
- Topic-based subscriptions per transaction

**Message Format**:
```json
{
  "txId": "uuid",
  "orderId": "ORD-xxx",
  "status": "PROCESSING",
  "currentStep": "INVENTORY",
  "message": "Inventory reserved",
  "timestamp": "2026-01-01T10:30:00Z"
}
```

**Endpoint**: `/ws/orders/{txId}`

---

## 6. Event Sourcing for Transaction Log

### Decision: INSERT-only State Recording

**Rationale**:
- Constitution mandates: "Transaction state changes MUST use INSERT (append-only)"
- Complete audit trail for debugging
- Recovery: replay events to reconstruct state

**Schema Design**:
```sql
CREATE TABLE transaction_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    service_name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,       -- Pending|Success|Fail|Rollback|RollbackDone|RollbackFail|Skipped
    error_message VARCHAR(500),
    retry_count INT,
    created_at TIMESTAMP NOT NULL,
    notified_at TIMESTAMP              -- For RollbackFail notification tracking
);
```

**State Query**: Get latest status per service by MAX(created_at) within tx_id grouping.

---

## 7. Dynamic Service Configuration

### Decision: Active/Pending Configuration Model

**Rationale**:
- Pending changes staged before activation
- In-progress orders unaffected by configuration changes
- Service snapshot per transaction preserves original configuration

**Tables**:
- `saga_config`: Stores JSON configuration with `is_active`/`is_pending` flags
- `transaction_service_snapshot`: Records service configuration at transaction start

**API Flow**:
1. PUT /admin/saga/services → Updates pending configuration
2. POST /admin/saga/services/apply → Activates pending as active
3. New transactions snapshot active configuration

---

## 8. Rollback Retry Strategy

### Decision: Exponential Backoff with 5 Max Retries

**Rationale**:
- Gradual back-off reduces load on recovering services
- 5 retries provides ~30 seconds total retry window
- RollbackFail triggers admin notification

**Retry Schedule**:
| Attempt | Delay | Cumulative |
|---------|-------|------------|
| 1 | 1s | 1s |
| 2 | 2s | 3s |
| 3 | 4s | 7s |
| 4 | 8s | 15s |
| 5 | 16s | 31s |

**Post-Failure**:
- Record RollbackFail status
- Store retry_count = 5
- Send email notification (mocked in dev)
- Record notified_at timestamp

---

## 9. Idempotency for Rollback APIs

### Decision: TxID-based Idempotency

**Rationale**:
- Network failures may cause duplicate rollback requests
- Rollback for non-existent transaction returns success (per PRD)
- Prevents double-refund scenarios

**Implementation**:
```java
@PostMapping("/rollback")
public ResponseEntity<RollbackResponse> rollback(@RequestBody RollbackRequest request) {
    Optional<Transaction> tx = repository.findByTxId(request.getTxId());
    if (tx.isEmpty()) {
        // Idempotent: already rolled back or never existed
        return ResponseEntity.ok(RollbackResponse.success(request.getTxId()));
    }
    // Perform actual rollback
    performRollback(tx.get());
    return ResponseEntity.ok(RollbackResponse.success(request.getTxId()));
}
```

---

## 10. Service Recovery on Restart

### Decision: Startup Scanner with Thread Recreation

**Rationale**:
- Incomplete transactions (non-terminal states) must resume
- ApplicationRunner triggers after context ready
- Checker threads re-created for each incomplete transaction

**Recovery Algorithm**:
1. Query all distinct tx_ids with non-terminal states
2. For each: recreate CheckerThread
3. Checker determines current state and continues/rollbacks

**Terminal States** (thread stops):
- All services: Success
- All services: RollbackDone or Skipped
- Any service: RollbackFail

---

## Summary of Technology Choices

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.x |
| Saga Orchestration | Apache Camel | 4.x |
| Circuit Breaker | Resilience4j | 2.2.x |
| WebSocket | Spring WebSocket | 3.2.x |
| Database | H2 (Embedded) | Latest |
| API Documentation | Springdoc OpenAPI | 2.3.x |
| Metrics | Micrometer | 1.12.x |
| Build | Gradle (Kotlin DSL) | 8.x |

---

## References

- [TECH.md](../../TECH.md) - Technical specification
- [PRD.md](../../PRD.md) - Product requirements
- [Constitution](../../.specify/memory/constitution.md) - Project principles
- [Apache Camel EIP](https://camel.apache.org/components/4.x/eips/enterprise-integration-patterns.html)
- [Resilience4j Circuit Breaker](https://resilience4j.readme.io/docs/circuitbreaker)
- [Saga Pattern - Microservices.io](https://microservices.io/patterns/data/saga.html)
