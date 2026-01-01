# Research: E-Commerce Saga Orchestration System

**Date**: 2026-01-01
**Branch**: `001-saga-orchestration`
**Status**: Complete

## Overview

This document captures research findings and design decisions for the Saga Orchestration System. All technical choices align with the project constitution and TECH.md specifications.

---

## 1. Saga Pattern Implementation

### Decision: Orchestration over Choreography

**Rationale**:
- Central coordinator (Order Service) provides clear visibility into transaction state
- Easier to implement compensation logic in reverse order
- Single point of control for timeout detection and recovery
- Better suited for POC where debugging and monitoring are important

**Alternatives Considered**:
| Alternative | Rejected Because |
|-------------|------------------|
| Choreography (event-driven) | Complex to track distributed state, harder to implement ordered rollback |
| 2PC (Two-Phase Commit) | Blocking protocol, not suitable for long-running transactions |
| TCC (Try-Confirm-Cancel) | More complex, requires all services to support 3-phase protocol |

---

## 2. Event Sourcing for Transaction Log

### Decision: Append-only INSERT pattern (no UPDATE)

**Rationale**:
- Complete audit trail of all state transitions
- Enables recovery by replaying events
- Prevents accidental data loss or corruption
- Aligns with Constitution Principle II (Domain-Driven Design)

**Implementation**:
```sql
-- Each status change creates a new row
INSERT INTO transaction_log (tx_id, order_id, service_name, status, created_at)
VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP);
```

**Status Codes**:
| Code | Name | Description |
|------|------|-------------|
| U | Uncommitted | Service call initiated, awaiting response |
| S | Success | Service responded successfully |
| F | Failed | Service responded with error |
| R | Rolled back | Compensation executed successfully |
| D | Done | Entire rollback flow completed |
| RF | Rollback Failed | Compensation failed after max retries |

---

## 3. Outbox Pattern Implementation

### Decision: Single Poller with Scheduled Polling

**Rationale**:
- Ensures exactly-once processing without distributed locks
- Atomic write of business data + event in same DB transaction
- Simple implementation suitable for single-instance POC
- 500ms polling interval balances latency vs. DB load

**Alternatives Considered**:
| Alternative | Rejected Because |
|-------------|------------------|
| CDC (Change Data Capture) | Requires Debezium/Kafka infrastructure, overkill for POC |
| Multiple pollers with locks | Adds complexity for distributed lock management |
| Transactional outbox with message broker | Requires external broker (RabbitMQ/Kafka) |

**Flow**:
1. Order API writes to `outbox_event` table in same transaction as business data
2. Single `@Scheduled` poller fetches unprocessed events
3. Poller triggers Camel route and marks event as processed

---

## 4. Apache Camel Route Design

### Decision: Dynamic Route with Property-based Service Configuration

**Rationale**:
- Supports runtime configuration changes without restart
- Built-in error handling with `onException()`
- Easy to implement retry logic with exponential backoff
- `toD()` enables dynamic endpoint resolution

**Key Design Patterns**:

**4.1 Dynamic Service Ordering**:
```java
// Service list stored in exchange property
exchange.setProperty("serviceList", sagaConfigService.getActiveServiceOrder());
exchange.setProperty("serviceIndex", 0);
// Loop through services using direct:processNextService
```

**4.2 Error Handling**:
```java
onException(Exception.class)
    .handled(true)
    .process(/* record failure status */)
    .to("direct:rollback");
```

**4.3 Rollback with Retry**:
```java
// Retry up to 5 times with simple backoff
doTry()
    .toD("${exchangeProperty.rollbackUrl}")
.doCatch(Exception.class)
    .process(/* increment retry count, check max */)
    .to("direct:executeRollback")  // retry
.end()
```

---

## 5. Checker Thread Design

### Decision: One Thread per Active Transaction

**Rationale**:
- Simple isolation - each transaction monitored independently
- No complex scheduling or coordination needed
- Easy to start/stop on transaction lifecycle
- Suitable for POC scale (not 1000s of concurrent transactions)

**Implementation**:
- `CheckerThreadManager` maintains `ConcurrentHashMap<txId, Thread>`
- Thread polls transaction status every 1 second
- Thread stops on terminal states: ALL_SUCCESS, DONE, ROLLBACK_FAILED
- Thread triggers rollback on: HAS_FAILURE, HAS_TIMEOUT

**Timeout Detection**:
```java
// For each U (uncommitted) status, check against configured timeout
LocalDateTime createdAt = getStatusCreatedAt(txId, serviceName, "U");
int timeout = timeouts.get(serviceName);  // 30, 60, or 120 seconds
if (createdAt.plusSeconds(timeout).isBefore(LocalDateTime.now())) {
    triggerRollback();
}
```

---

## 6. WebSocket Implementation

### Decision: Spring WebSocket with Path-based Session Mapping

**Rationale**:
- Native Spring integration, no additional dependencies
- Path variable `/ws/orders/{txId}` enables session lookup
- Push-based updates without polling
- Graceful handling of disconnection/reconnection

**Session Management**:
```java
// Map sessions by txId for targeted message delivery
Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

// On connect: extract txId from path
String txId = session.getUri().getPath().substring(path.lastIndexOf('/') + 1);
sessions.put(txId, session);
```

**Message Format** (JSON):
```json
{
  "txId": "uuid",
  "status": "PROCESSING|COMPLETED|ROLLING_BACK|ROLLED_BACK|ROLLBACK_FAILED",
  "currentStep": "CREDIT_CARD|INVENTORY|LOGISTICS",
  "message": "Human-readable status message",
  "timestamp": "2026-01-01T10:00:00"
}
```

---

## 7. Recovery Mechanism

### Decision: ApplicationRunner-based Startup Recovery

**Rationale**:
- Runs after Spring context fully initialized
- One-time scan for incomplete transactions
- Reuses existing CheckerThread infrastructure
- Simple and reliable for single-instance deployment

**Recovery Logic**:
```sql
-- Find transactions that are neither:
-- 1. All services succeeded (3x S status)
-- 2. Rollback completed (D status exists)
-- 3. Rollback failed (RF status exists)
SELECT DISTINCT tx_id FROM transaction_log
WHERE tx_id NOT IN (/* completed or failed */)
```

---

## 8. Configuration Management

### Decision: Database-backed Configuration with Staged Apply

**Rationale**:
- Persists across restarts
- Supports pending/active states for safe transitions
- No downtime required for configuration changes
- In-progress transactions continue with original config

**Configuration Types**:

| Type | Key | Value Example |
|------|-----|---------------|
| SERVICE_ORDER | active/pending | `[{order:1,name:"CREDIT_CARD",...}]` |
| TIMEOUT | active/pending | `{"CREDIT_CARD":30,"INVENTORY":60,...}` |

**Apply Flow**:
1. PUT `/admin/saga/service-order` → saves to `saga_config` with `is_pending=true`
2. POST `/admin/saga/service-order/apply` → sets pending to active, reloads in-memory cache
3. New transactions use updated config; in-progress transactions continue with their original config

---

## 9. Idempotent Rollback APIs

### Decision: Rollback Returns Success Even for Non-existent Transactions

**Rationale**:
- Enables safe retry without side effects
- Handles race conditions gracefully
- Simplifies client logic (no need to track rollback state)
- Follows HTTP semantics: DELETE/rollback is idempotent by definition

**Implementation Pattern**:
```java
@PostMapping("/rollback")
public ResponseEntity<RollbackResponse> rollback(@RequestBody RollbackRequest request) {
    // Check if transaction exists
    Optional<Transaction> tx = repository.findByTxId(request.getTxId());

    if (tx.isEmpty()) {
        // Already rolled back or never existed - return success
        return ResponseEntity.ok(new RollbackResponse(true, "No action needed"));
    }

    // Perform actual rollback
    performRollback(tx.get());
    return ResponseEntity.ok(new RollbackResponse(true, "Rolled back"));
}
```

---

## 10. Observability Strategy

### Decision: Micrometer Metrics + Structured Logging

**Rationale**:
- Micrometer integrates natively with Spring Boot Actuator
- Prometheus endpoint for metrics scraping
- Structured JSON logging enables log aggregation
- txId as correlation ID for distributed tracing

**Metrics Captured**:
| Metric | Type | Description |
|--------|------|-------------|
| saga.started | Counter | Number of sagas initiated |
| saga.completed | Counter | Successful saga completions |
| saga.failed | Counter | Sagas that triggered rollback |
| saga.rolledback | Counter | Successful rollback completions |
| saga.rollback.failed | Counter | Rollbacks that exhausted retries |
| saga.duration | Timer | End-to-end saga execution time |

---

## 11. Hexagonal Architecture Alignment

### Layer Responsibility Summary

| Layer | Allowed Dependencies | Contains |
|-------|---------------------|----------|
| Domain | None (pure Java) | Order, TransactionLog, SagaConfig, TransactionEvent |
| Application | Domain only | Use case interfaces, Port interfaces, Service implementations |
| Adapter | Application + external libs | Controllers, Repositories, HTTP Clients, WebSocket Handler |
| Infrastructure | All layers | Camel Routes, Config, Poller, Checker, Recovery |

**Validation Rules** (enforced via architecture tests):
- Domain classes MUST NOT import `org.springframework.*`
- Domain classes MUST NOT import `org.apache.camel.*`
- Domain classes MUST NOT import `jakarta.persistence.*`
- Application layer MUST NOT import adapter/infrastructure packages

---

## 12. Testing Strategy

### Test Pyramid

| Level | Scope | Tools | Coverage Target |
|-------|-------|-------|-----------------|
| Unit | Domain + Application | JUnit 5, Mockito | ≥80% |
| Integration | Adapters | Spring Boot Test, H2 | All adapters |
| Contract | API schemas | Spring MockMvc | All endpoints |
| Acceptance | User scenarios | Cucumber/Gherkin | All PRD scenarios |
| Camel Route | Route logic | Camel Test | All routes |

**TDD Enforcement**:
- Each task in tasks.md will specify test requirements
- PR checklist includes TDD compliance verification
- Test commit should precede or accompany implementation

---

## Summary

All research items have been resolved. The technical decisions align with:
- Constitution principles (Hexagonal Architecture, DDD, SOLID, TDD)
- TECH.md specifications (Java 21, Spring Boot 3.2.x, Camel 4.x)
- PRD requirements (real-time notifications, rollback, recovery)

No remaining NEEDS CLARIFICATION items. Ready for Phase 1: Design & Contracts.
