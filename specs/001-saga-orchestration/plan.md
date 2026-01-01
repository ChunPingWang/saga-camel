# Implementation Plan: E-Commerce Saga Orchestration System

**Branch**: `001-saga-orchestration` | **Date**: 2026-01-01 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-saga-orchestration/spec.md`

## Summary

Implement a distributed transaction orchestration system using the Saga pattern for e-commerce order processing. The system will coordinate three downstream services (payment, inventory, logistics) with automatic compensation (rollback) on failure or timeout, real-time customer notifications via WebSocket, and dynamic configuration management. Built following Hexagonal Architecture with Event Sourcing for audit trails.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.2.x, Apache Camel 4.x, Spring WebSocket, Springdoc OpenAPI
**Storage**: H2 Database (Embedded) with Event Sourcing pattern (append-only)
**Testing**: JUnit 5, Mockito, Camel Test, Awaitility
**Target Platform**: Linux server / Multi-module Spring Boot application
**Project Type**: Monorepo with 4 microservices (order-service, credit-card-service, inventory-service, logistics-service) + common module
**Performance Goals**: 100 orders/second, 200ms acknowledgment, 1s notification delivery
**Constraints**: <3s order processing (excluding downstream), 99.9% availability, 30s recovery after restart
**Scale/Scope**: Single instance per service, H2 embedded database, development/POC phase

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Hexagonal Architecture | ✅ PASS | TECH.md defines clear layer separation: domain/, application/, adapter/, infrastructure/ |
| II. Domain-Driven Design | ✅ PASS | Entities (Order, TransactionLog), Value Objects (TxId, Status), Domain Events defined |
| III. SOLID Principles | ✅ PASS | Port interfaces (TransactionLogPort, OutboxPort, etc.) enable dependency inversion |
| IV. Test-Driven Development | ⚠️ PENDING | TDD workflow will be enforced during implementation via tasks.md |
| V. Behavior-Driven Development | ⚠️ PENDING | BDD scenarios defined in spec.md, executable tests in Phase 6 |
| VI. Code Quality Standards | ✅ PASS | Structured logging with txId, named constants for status codes |
| VII. Dependency Inversion | ✅ PASS | Ports defined in application layer, adapters in outer layers |
| VIII. Testing Standards | ⚠️ PENDING | Test structure defined, coverage targets set (≥80% domain/application) |

**Gate Result**: PASS (pending items are implementation-phase concerns)

## Project Structure

### Documentation (this feature)

```text
specs/001-saga-orchestration/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (OpenAPI specs)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
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
├── order-service/                      # Orchestrator (main service)
│   └── src/
│       ├── main/java/com/ecommerce/order/
│       │   ├── domain/                 # Pure business logic (no framework imports)
│       │   │   ├── model/              # Order, TransactionLog, SagaConfig
│       │   │   └── event/              # TransactionEvent
│       │   ├── application/            # Use cases and ports
│       │   │   ├── port/
│       │   │   │   ├── in/             # OrderConfirmUseCase, SagaConfigUseCase
│       │   │   │   └── out/            # TransactionLogPort, OutboxPort, etc.
│       │   │   └── service/            # OrderSagaService, RollbackService
│       │   ├── adapter/                # External adapters
│       │   │   ├── in/
│       │   │   │   ├── web/            # OrderController, AdminController
│       │   │   │   └── websocket/      # OrderWebSocketHandler
│       │   │   └── out/
│       │   │       ├── persistence/    # JPA repositories
│       │   │       ├── service/        # HTTP clients for downstream
│       │   │       └── notification/   # Email adapters
│       │   └── infrastructure/         # Framework config
│       │       ├── camel/              # OrderSagaRoute, RollbackRoute
│       │       ├── config/             # CamelConfig, WebSocketConfig
│       │       ├── poller/             # OutboxPoller
│       │       ├── checker/            # CheckerThreadManager
│       │       ├── recovery/           # SagaRecoveryRunner
│       │       └── observability/      # SagaMetrics, TracingConfig
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── schema.sql
│       └── test/java/com/ecommerce/order/
│           ├── domain/                 # Unit tests (mocked dependencies)
│           ├── application/            # Use case tests
│           ├── adapter/                # Integration tests
│           └── infrastructure/         # Camel route tests
│
├── credit-card-service/                # Payment service
│   └── src/main/java/com/ecommerce/creditcard/
│       ├── domain/model/               # Payment
│       ├── application/
│       │   ├── port/in/                # ProcessPaymentUseCase, RollbackPaymentUseCase
│       │   └── service/                # CreditCardService
│       └── adapter/in/web/             # CreditCardController
│
├── inventory-service/                  # Inventory service
│   └── src/main/java/com/ecommerce/inventory/
│       ├── domain/model/               # Reservation
│       ├── application/
│       │   ├── port/in/                # ReserveInventoryUseCase, RollbackReservationUseCase
│       │   └── service/                # InventoryService
│       └── adapter/in/web/             # InventoryController
│
└── logistics-service/                  # Logistics service
    └── src/main/java/com/ecommerce/logistics/
        ├── domain/model/               # Shipment
        ├── application/
        │   ├── port/in/                # ScheduleShipmentUseCase, CancelShipmentUseCase
        │   └── service/                # LogisticsService
        └── adapter/in/web/             # LogisticsController

tests/
├── unit/                               # Unit tests (≥80% coverage for domain/application)
├── integration/                        # Adapter integration tests
├── contract/                           # API contract tests
└── acceptance/                         # BDD acceptance tests
```

**Structure Decision**: Monorepo with 4 Spring Boot microservices following Hexagonal Architecture. Each service has isolated domain layer. Common module shares DTOs and enums across services.

## Complexity Tracking

> No violations requiring justification. All design decisions align with constitution principles.

| Decision | Rationale | Constitution Alignment |
|----------|-----------|------------------------|
| 4 separate services | PRD requires independent microservices for demo purposes | Bounded Contexts (DDD) |
| Apache Camel for orchestration | Dynamic routing, error handling, retry logic built-in | Framework in Infrastructure layer |
| H2 embedded database | POC phase, easy setup, no external dependencies | Explicit scope constraint |
| Checker Thread per transaction | Simple monitoring without distributed scheduler | Single-service scope |
