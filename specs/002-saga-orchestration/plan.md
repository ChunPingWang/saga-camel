# Implementation Plan: E-Commerce Saga Orchestration System

**Branch**: `002-saga-orchestration` | **Date**: 2026-01-01 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-saga-orchestration/spec.md`

## Summary

Implement an E-Commerce Saga Orchestration System using the Saga Pattern with Outbox Pattern for distributed transaction management. The system coordinates payment, inventory, and logistics services with automatic rollback on failure, timeout detection, real-time WebSocket notifications, Circuit Breaker protection, and dynamic service configuration. Built following Hexagonal Architecture with TDD/BDD practices.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.2.x, Apache Camel 4.x, Resilience4j (Circuit Breaker), Spring WebSocket
**Storage**: H2 Database (Embedded, Event Sourcing with INSERT-only)
**Testing**: JUnit 5, Mockito, Apache Camel Test, Spring Boot Test
**Target Platform**: JVM (Linux/macOS server)
**Project Type**: Monorepo with multiple microservices
**Performance Goals**: <200ms order confirmation response, <3s total processing, 100 orders/sec
**Constraints**: 99.9% uptime, real-time WebSocket updates <1s, zero data inconsistency
**Scale/Scope**: 4 microservices (Order Orchestrator + 3 participant services)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Hexagonal Architecture | ✅ PASS | Project structure follows domain/application/adapter/infrastructure layers per TECH.md |
| II. Domain-Driven Design | ✅ PASS | Entities (Order, Transaction, ServiceConfig), domain events (TransactionEvent), repository pattern |
| III. SOLID Principles | ✅ PASS | Port/adapter pattern enforces DIP; small focused interfaces (SagaConfigPort, WebSocketPort) |
| IV. Test-Driven Development | ✅ GATE | All implementation must follow Red-Green-Refactor; tests precede production code |
| V. Behavior-Driven Development | ✅ PASS | Spec contains Given-When-Then acceptance scenarios for all user stories |
| VI. Code Quality Standards | ✅ PASS | Structured logging, correlation IDs (txId), domain exceptions, immutable value objects |
| VII. Dependency Inversion | ✅ PASS | Ports defined in application layer, adapters in infrastructure/adapter layers |
| VIII. Testing Standards | ✅ GATE | Unit/Integration/Contract/BDD tests required at specified coverage levels |

**Gate Result**: PASS - All gates satisfied. Constitution-compliant architecture defined in TECH.md.

## Project Structure

### Documentation (this feature)

```text
specs/002-saga-orchestration/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (OpenAPI specs)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
ecommerce-saga/
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Multi-module settings
├── gradle.properties                   # Shared properties
│
├── common/                             # Shared module
│   └── src/main/java/com/ecommerce/common/
│       ├── domain/                     # TransactionStatus, ServiceName
│       ├── dto/                        # NotifyRequest/Response, RollbackRequest/Response
│       ├── event/                      # SagaEvent
│       └── exception/                  # ServiceException
│
├── order-service/                      # Orchestrator (Hexagonal)
│   └── src/main/java/com/ecommerce/order/
│       ├── domain/                     # Pure business logic
│       │   ├── model/                  # Order, TransactionLog, ServiceConfig
│       │   └── event/                  # TransactionEvent
│       ├── application/                # Use cases
│       │   ├── port/
│       │   │   ├── in/                 # OrderConfirmUseCase, SagaConfigUseCase
│       │   │   └── out/                # TransactionLogPort, WebSocketPort, etc.
│       │   └── service/                # OrderSagaService, RollbackService, etc.
│       ├── adapter/                    # External adapters
│       │   ├── in/web/                 # OrderController, AdminController
│       │   ├── in/websocket/           # OrderWebSocketHandler
│       │   └── out/persistence/        # TransactionLogRepository, etc.
│       └── infrastructure/             # Framework configs
│           ├── camel/                  # OrderSagaRoute, RollbackRoute
│           ├── circuitbreaker/         # ServiceCircuitBreaker
│           ├── checker/                # CheckerThreadManager
│           ├── poller/                 # OutboxPoller
│           └── config/                 # Spring/Camel/WebSocket configs
│
├── credit-card-service/                # Participant service
├── inventory-service/                  # Participant service
└── logistics-service/                  # Participant service

tests/
├── unit/                               # Per-module unit tests
├── integration/                        # Adapter integration tests
├── contract/                           # API contract tests
└── acceptance/                         # BDD acceptance tests
```

**Structure Decision**: Monorepo with 4 microservice modules. Order-service uses full Hexagonal Architecture as the Saga Orchestrator. Participant services are simpler with notify/rollback endpoints supporting idempotency.

## Complexity Tracking

> No violations requiring justification. Architecture follows constitution constraints.

| Aspect | Decision | Rationale |
|--------|----------|-----------|
| 4 Microservices | Required by PRD | Order orchestrator + 3 participant services |
| Event Sourcing | INSERT-only per constitution | Audit trail, recoverability |
| Circuit Breaker | Resilience4j integration | Prevent cascade failures |
| Checker Thread per Transaction | Dedicated monitoring | Timeout detection, no distributed lock needed |
