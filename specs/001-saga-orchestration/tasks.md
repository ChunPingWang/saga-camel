# Tasks: E-Commerce Saga Orchestration System

**Input**: Design documents from `/specs/001-saga-orchestration/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/
**Branch**: `001-saga-orchestration`

**Tests**: Required per Constitution (TDD is NON-NEGOTIABLE - ≥80% coverage for domain/application)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Based on plan.md, this is a **Monorepo with 4 microservices**:
- `common/src/main/java/com/ecommerce/common/` - Shared module
- `order-service/src/main/java/com/ecommerce/order/` - Orchestrator
- `credit-card-service/src/main/java/com/ecommerce/creditcard/` - Payment
- `inventory-service/src/main/java/com/ecommerce/inventory/` - Inventory
- `logistics-service/src/main/java/com/ecommerce/logistics/` - Logistics

---

## Phase 1: Setup (Project Infrastructure)

**Purpose**: Initialize Gradle multi-module project and basic structure

- [ ] T001 Create root `build.gradle.kts` with Spring Boot 3.2.x, Java 21, and shared dependencies
- [ ] T002 Create `settings.gradle.kts` with module includes (common, order-service, credit-card-service, inventory-service, logistics-service)
- [ ] T003 [P] Create `gradle.properties` with shared version properties
- [ ] T004 [P] Create `.gitignore` for Gradle/Java projects
- [ ] T005 [P] Create `common/build.gradle.kts` for shared module
- [ ] T006 [P] Create `order-service/build.gradle.kts` with Camel, WebSocket, H2 dependencies
- [ ] T007 [P] Create `credit-card-service/build.gradle.kts`
- [ ] T008 [P] Create `inventory-service/build.gradle.kts`
- [ ] T009 [P] Create `logistics-service/build.gradle.kts`

**Checkpoint**: `./gradlew clean build` should succeed with empty modules

---

## Phase 2: Foundational (Shared Infrastructure)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Common Module Domain

- [ ] T010 [P] Create `TransactionStatus` enum in `common/src/main/java/com/ecommerce/common/domain/TransactionStatus.java` (U, S, F, R, D, RF)
- [ ] T011 [P] Create `ServiceName` enum in `common/src/main/java/com/ecommerce/common/domain/ServiceName.java` (CREDIT_CARD, INVENTORY, LOGISTICS, SAGA)

### Common Module DTOs

- [ ] T012 [P] Create `NotifyRequest` DTO in `common/src/main/java/com/ecommerce/common/dto/NotifyRequest.java`
- [ ] T013 [P] Create `NotifyResponse` DTO in `common/src/main/java/com/ecommerce/common/dto/NotifyResponse.java`
- [ ] T014 [P] Create `RollbackRequest` DTO in `common/src/main/java/com/ecommerce/common/dto/RollbackRequest.java`
- [ ] T015 [P] Create `RollbackResponse` DTO in `common/src/main/java/com/ecommerce/common/dto/RollbackResponse.java`

### Common Module Exception

- [ ] T016 Create `ServiceException` in `common/src/main/java/com/ecommerce/common/exception/ServiceException.java`

### Order Service Database Schema

- [ ] T017 Create `schema.sql` in `order-service/src/main/resources/schema.sql` with transaction_log, outbox_event, saga_config tables

### Order Service Domain Layer (No Framework Imports)

- [ ] T018 Write unit test for `Order` domain model in `order-service/src/test/java/com/ecommerce/order/domain/model/OrderTest.java`
- [ ] T019 Create `Order` domain entity in `order-service/src/main/java/com/ecommerce/order/domain/model/Order.java`
- [ ] T020 [P] Write unit test for `TransactionLog` domain model in `order-service/src/test/java/com/ecommerce/order/domain/model/TransactionLogTest.java`
- [ ] T021 [P] Create `TransactionLog` domain entity in `order-service/src/main/java/com/ecommerce/order/domain/model/TransactionLog.java`
- [ ] T022 [P] Write unit test for `ServiceConfig` value object in `order-service/src/test/java/com/ecommerce/order/domain/model/ServiceConfigTest.java`
- [ ] T023 [P] Create `ServiceConfig` value object in `order-service/src/main/java/com/ecommerce/order/domain/model/ServiceConfig.java`
- [ ] T024 [P] Create `TransactionEvent` domain event in `order-service/src/main/java/com/ecommerce/order/domain/event/TransactionEvent.java`

### Order Service Application Layer Ports

- [ ] T025 [P] Create `TransactionLogPort` interface in `order-service/src/main/java/com/ecommerce/order/application/port/out/TransactionLogPort.java`
- [ ] T026 [P] Create `OutboxPort` interface in `order-service/src/main/java/com/ecommerce/order/application/port/out/OutboxPort.java`
- [ ] T027 [P] Create `ServiceClientPort` interface in `order-service/src/main/java/com/ecommerce/order/application/port/out/ServiceClientPort.java`
- [ ] T028 [P] Create `WebSocketPort` interface in `order-service/src/main/java/com/ecommerce/order/application/port/out/WebSocketPort.java`
- [ ] T029 [P] Create `NotificationPort` interface in `order-service/src/main/java/com/ecommerce/order/application/port/out/NotificationPort.java`

### Order Service Persistence Adapters

- [ ] T030 [P] Create `TransactionLogEntity` JPA entity in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/TransactionLogEntity.java`
- [ ] T031 [P] Create `OutboxEventEntity` JPA entity in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/OutboxEventEntity.java`
- [ ] T032 [P] Create `SagaConfigEntity` JPA entity in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/SagaConfigEntity.java`
- [ ] T033 [P] Create `TransactionLogRepository` in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/TransactionLogRepository.java`
- [ ] T034 [P] Create `OutboxEventRepository` in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/OutboxEventRepository.java`
- [ ] T035 [P] Create `SagaConfigRepository` in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/SagaConfigRepository.java`
- [ ] T036 Write integration test for `TransactionLogPersistenceAdapter` in `order-service/src/test/java/com/ecommerce/order/adapter/out/persistence/TransactionLogPersistenceAdapterTest.java`
- [ ] T037 Create `TransactionLogPersistenceAdapter` implementing `TransactionLogPort` in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/TransactionLogPersistenceAdapter.java`

### Order Service Infrastructure Config

- [ ] T038 [P] Create `application.yml` in `order-service/src/main/resources/application.yml` with H2, WebSocket, Camel config
- [ ] T039 [P] Create `DataSourceConfig` in `order-service/src/main/java/com/ecommerce/order/infrastructure/config/DataSourceConfig.java`
- [ ] T040 [P] Create `CamelConfig` in `order-service/src/main/java/com/ecommerce/order/infrastructure/config/CamelConfig.java`
- [ ] T041 [P] Create `SwaggerConfig` in `order-service/src/main/java/com/ecommerce/order/infrastructure/config/SwaggerConfig.java`
- [ ] T042 Create `OrderServiceApplication` main class in `order-service/src/main/java/com/ecommerce/order/OrderServiceApplication.java`

### Downstream Services Setup

- [ ] T043 [P] Create `CreditCardServiceApplication` in `credit-card-service/src/main/java/com/ecommerce/creditcard/CreditCardServiceApplication.java`
- [ ] T044 [P] Create `InventoryServiceApplication` in `inventory-service/src/main/java/com/ecommerce/inventory/InventoryServiceApplication.java`
- [ ] T045 [P] Create `LogisticsServiceApplication` in `logistics-service/src/main/java/com/ecommerce/logistics/LogisticsServiceApplication.java`
- [ ] T046 [P] Create `application.yml` for credit-card-service (port 8081)
- [ ] T047 [P] Create `application.yml` for inventory-service (port 8082)
- [ ] T048 [P] Create `application.yml` for logistics-service (port 8083)

**Checkpoint**: All services start without errors. `./gradlew bootRun` for each service works.

---

## Phase 3: User Story 1 - Order Confirmation with Real-time Progress (Priority: P1) 🎯 MVP

**Goal**: Customer confirms order, receives immediate acknowledgment with txId, and gets real-time WebSocket notifications for each service step (payment, inventory, logistics).

**Independent Test**: Submit an order via POST /api/v1/orders/confirm, connect to WebSocket, verify all 3 services complete with status notifications pushed.

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation (TDD)**

- [ ] T049 [P] [US1] Write contract test for POST /api/v1/orders/confirm in `order-service/src/test/java/com/ecommerce/order/adapter/in/web/OrderControllerContractTest.java`
- [ ] T050 [P] [US1] Write contract test for GET /api/v1/transactions/{txId} in `order-service/src/test/java/com/ecommerce/order/adapter/in/web/TransactionControllerContractTest.java`
- [ ] T051 [P] [US1] Write unit test for `OrderSagaService` in `order-service/src/test/java/com/ecommerce/order/application/service/OrderSagaServiceTest.java`
- [ ] T052 [P] [US1] Write Camel route test for `OrderSagaRoute` in `order-service/src/test/java/com/ecommerce/order/infrastructure/camel/OrderSagaRouteTest.java`
- [ ] T053 [P] [US1] Write integration test for WebSocket handler in `order-service/src/test/java/com/ecommerce/order/adapter/in/websocket/OrderWebSocketHandlerTest.java`

### Implementation for User Story 1

#### Downstream Services (Idempotent APIs)

- [ ] T054 [P] [US1] Create `Payment` domain model in `credit-card-service/src/main/java/com/ecommerce/creditcard/domain/model/Payment.java`
- [ ] T055 [P] [US1] Create `ProcessPaymentUseCase` port in `credit-card-service/src/main/java/com/ecommerce/creditcard/application/port/in/ProcessPaymentUseCase.java`
- [ ] T056 [P] [US1] Create `CreditCardService` in `credit-card-service/src/main/java/com/ecommerce/creditcard/application/service/CreditCardService.java`
- [ ] T057 [P] [US1] Create `CreditCardController` with POST /notify endpoint in `credit-card-service/src/main/java/com/ecommerce/creditcard/adapter/in/web/CreditCardController.java`

- [ ] T058 [P] [US1] Create `Reservation` domain model in `inventory-service/src/main/java/com/ecommerce/inventory/domain/model/Reservation.java`
- [ ] T059 [P] [US1] Create `ReserveInventoryUseCase` port in `inventory-service/src/main/java/com/ecommerce/inventory/application/port/in/ReserveInventoryUseCase.java`
- [ ] T060 [P] [US1] Create `InventoryService` in `inventory-service/src/main/java/com/ecommerce/inventory/application/service/InventoryService.java`
- [ ] T061 [P] [US1] Create `InventoryController` with POST /notify endpoint in `inventory-service/src/main/java/com/ecommerce/inventory/adapter/in/web/InventoryController.java`

- [ ] T062 [P] [US1] Create `Shipment` domain model in `logistics-service/src/main/java/com/ecommerce/logistics/domain/model/Shipment.java`
- [ ] T063 [P] [US1] Create `ScheduleShipmentUseCase` port in `logistics-service/src/main/java/com/ecommerce/logistics/application/port/in/ScheduleShipmentUseCase.java`
- [ ] T064 [P] [US1] Create `LogisticsService` in `logistics-service/src/main/java/com/ecommerce/logistics/application/service/LogisticsService.java`
- [ ] T065 [P] [US1] Create `LogisticsController` with POST /notify endpoint in `logistics-service/src/main/java/com/ecommerce/logistics/adapter/in/web/LogisticsController.java`

#### Order Service Application Layer

- [ ] T066 [US1] Create `OrderConfirmUseCase` port in `order-service/src/main/java/com/ecommerce/order/application/port/in/OrderConfirmUseCase.java`
- [ ] T067 [US1] Create `TransactionQueryUseCase` port in `order-service/src/main/java/com/ecommerce/order/application/port/in/TransactionQueryUseCase.java`
- [ ] T068 [US1] Implement `OrderSagaService` in `order-service/src/main/java/com/ecommerce/order/application/service/OrderSagaService.java`

#### Order Service Adapters - Outbound

- [ ] T069 [US1] Write integration test for `OutboxPersistenceAdapter` in `order-service/src/test/java/com/ecommerce/order/adapter/out/persistence/OutboxPersistenceAdapterTest.java`
- [ ] T070 [US1] Create `OutboxPersistenceAdapter` implementing `OutboxPort` in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/OutboxPersistenceAdapter.java`
- [ ] T071 [P] [US1] Create `CreditCardServiceClient` in `order-service/src/main/java/com/ecommerce/order/adapter/out/service/CreditCardServiceClient.java`
- [ ] T072 [P] [US1] Create `InventoryServiceClient` in `order-service/src/main/java/com/ecommerce/order/adapter/out/service/InventoryServiceClient.java`
- [ ] T073 [P] [US1] Create `LogisticsServiceClient` in `order-service/src/main/java/com/ecommerce/order/adapter/out/service/LogisticsServiceClient.java`

#### Order Service Adapters - Inbound

- [ ] T074 [US1] Create `OrderController` with POST /api/v1/orders/confirm in `order-service/src/main/java/com/ecommerce/order/adapter/in/web/OrderController.java`
- [ ] T075 [US1] Create `TransactionController` with GET /api/v1/transactions/{txId} in `order-service/src/main/java/com/ecommerce/order/adapter/in/web/TransactionController.java`
- [ ] T076 [US1] Create request/response DTOs in `order-service/src/main/java/com/ecommerce/order/adapter/in/web/dto/`

#### Order Service WebSocket

- [ ] T077 [US1] Create `WebSocketConfig` in `order-service/src/main/java/com/ecommerce/order/infrastructure/config/WebSocketConfig.java`
- [ ] T078 [US1] Create `OrderWebSocketHandler` implementing `WebSocketPort` in `order-service/src/main/java/com/ecommerce/order/adapter/in/websocket/OrderWebSocketHandler.java`
- [ ] T079 [US1] Create `WebSocketMessage` DTO in `order-service/src/main/java/com/ecommerce/order/adapter/in/websocket/WebSocketMessage.java`

#### Order Service Camel Routes

- [ ] T080 [US1] Create `OrderSagaRoute` with dynamic service ordering in `order-service/src/main/java/com/ecommerce/order/infrastructure/camel/OrderSagaRoute.java`
- [ ] T081 [P] [US1] Create `PreNotifyProcessor` in `order-service/src/main/java/com/ecommerce/order/infrastructure/camel/processor/PreNotifyProcessor.java`
- [ ] T082 [P] [US1] Create `PostNotifyProcessor` in `order-service/src/main/java/com/ecommerce/order/infrastructure/camel/processor/PostNotifyProcessor.java`

#### Outbox Poller

- [ ] T083 [US1] Write unit test for `OutboxPoller` in `order-service/src/test/java/com/ecommerce/order/infrastructure/poller/OutboxPollerTest.java`
- [ ] T084 [US1] Create `OutboxPoller` with @Scheduled polling in `order-service/src/main/java/com/ecommerce/order/infrastructure/poller/OutboxPoller.java`

**Checkpoint**: Happy path order confirmation works. POST order → get txId → connect WebSocket → receive PROCESSING/COMPLETED notifications for each service.

---

## Phase 4: User Story 2 - Automatic Rollback on Service Failure (Priority: P2)

**Goal**: When any downstream service fails, automatically rollback all previously successful services in reverse order with customer notification.

**Independent Test**: Configure inventory service to fail, submit order, verify payment is rolled back and customer receives failure notification.

### Tests for User Story 2

- [ ] T085 [P] [US2] Write Camel route test for `RollbackRoute` in `order-service/src/test/java/com/ecommerce/order/infrastructure/camel/RollbackRouteTest.java`
- [ ] T086 [P] [US2] Write unit test for `RollbackService` in `order-service/src/test/java/com/ecommerce/order/application/service/RollbackServiceTest.java`
- [ ] T087 [P] [US2] Write integration test for rollback scenario in `order-service/src/test/java/com/ecommerce/order/integration/RollbackIntegrationTest.java`

### Implementation for User Story 2

#### Downstream Rollback APIs (Idempotent)

- [ ] T088 [P] [US2] Create `RollbackPaymentUseCase` port in `credit-card-service/src/main/java/com/ecommerce/creditcard/application/port/in/RollbackPaymentUseCase.java`
- [ ] T089 [P] [US2] Add rollback method to `CreditCardService` in `credit-card-service/src/main/java/com/ecommerce/creditcard/application/service/CreditCardService.java`
- [ ] T090 [P] [US2] Add POST /rollback endpoint to `CreditCardController`

- [ ] T091 [P] [US2] Create `RollbackReservationUseCase` port in `inventory-service/src/main/java/com/ecommerce/inventory/application/port/in/RollbackReservationUseCase.java`
- [ ] T092 [P] [US2] Add rollback method to `InventoryService`
- [ ] T093 [P] [US2] Add POST /rollback endpoint to `InventoryController`

- [ ] T094 [P] [US2] Create `CancelShipmentUseCase` port in `logistics-service/src/main/java/com/ecommerce/logistics/application/port/in/CancelShipmentUseCase.java`
- [ ] T095 [P] [US2] Add rollback method to `LogisticsService`
- [ ] T096 [P] [US2] Add POST /rollback endpoint to `LogisticsController`

#### Order Service Rollback Logic

- [ ] T097 [US2] Create `RollbackService` in `order-service/src/main/java/com/ecommerce/order/application/service/RollbackService.java`
- [ ] T098 [US2] Create `RollbackRoute` with reverse-order compensation in `order-service/src/main/java/com/ecommerce/order/infrastructure/camel/RollbackRoute.java`
- [ ] T099 [P] [US2] Create `RollbackProcessor` in `order-service/src/main/java/com/ecommerce/order/infrastructure/camel/processor/RollbackProcessor.java`
- [ ] T100 [US2] Update `OrderSagaRoute` to trigger rollback on exception via onException handler

**Checkpoint**: Service failure triggers automatic rollback. Inventory fails → payment rolled back → customer notified of failure.

---

## Phase 5: User Story 3 - Timeout Detection and Automatic Compensation (Priority: P3)

**Goal**: Monitor transactions for timeout, automatically trigger rollback when service exceeds configured timeout threshold.

**Independent Test**: Set short timeout (5s), simulate slow service, verify timeout detection triggers rollback.

### Tests for User Story 3

- [ ] T101 [P] [US3] Write unit test for `TransactionCheckerThread` in `order-service/src/test/java/com/ecommerce/order/infrastructure/checker/TransactionCheckerThreadTest.java`
- [ ] T102 [P] [US3] Write unit test for `CheckerThreadManager` in `order-service/src/test/java/com/ecommerce/order/infrastructure/checker/CheckerThreadManagerTest.java`
- [ ] T103 [P] [US3] Write integration test for timeout scenario in `order-service/src/test/java/com/ecommerce/order/integration/TimeoutIntegrationTest.java`

### Implementation for User Story 3

- [ ] T104 [US3] Create `TransactionCheckerThread` in `order-service/src/main/java/com/ecommerce/order/infrastructure/checker/TransactionCheckerThread.java`
- [ ] T105 [US3] Create `CheckerThreadManager` in `order-service/src/main/java/com/ecommerce/order/infrastructure/checker/CheckerThreadManager.java`
- [ ] T106 [US3] Integrate checker thread startup in `OrderSagaService` after outbox event creation
- [ ] T107 [US3] Add timeout configuration loading from `SagaConfigService`

**Checkpoint**: Timeout detection works. Slow service → checker detects timeout → triggers rollback → stops checker.

---

## Phase 6: User Story 4 - Rollback Failure Escalation (Priority: P4)

**Goal**: When rollback fails after max retries (5), notify administrator via email with transaction details.

**Independent Test**: Force rollback failures (5x), verify admin notification sent with txId, service name, error message.

### Tests for User Story 4

- [ ] T108 [P] [US4] Write unit test for `NotificationPort` implementations in `order-service/src/test/java/com/ecommerce/order/adapter/out/notification/NotificationAdapterTest.java`
- [ ] T109 [P] [US4] Write integration test for rollback failure escalation in `order-service/src/test/java/com/ecommerce/order/integration/EscalationIntegrationTest.java`

### Implementation for User Story 4

- [ ] T110 [P] [US4] Create `MockEmailNotificationAdapter` for dev profile in `order-service/src/main/java/com/ecommerce/order/adapter/out/notification/MockEmailNotificationAdapter.java`
- [ ] T111 [P] [US4] Create `EmailNotificationAdapter` for prod profile in `order-service/src/main/java/com/ecommerce/order/adapter/out/notification/EmailNotificationAdapter.java`
- [ ] T112 [US4] Update `RollbackRoute` to call `NotificationPort` after max retries exceeded
- [ ] T113 [US4] Add notifiedAt timestamp recording in `TransactionLogPort`

**Checkpoint**: Rollback failure triggers notification. 5 failures → admin email sent → notifiedAt recorded.

---

## Phase 7: User Story 5 - Service Recovery After Restart (Priority: P5)

**Goal**: On service startup, scan for incomplete transactions and resume monitoring/processing.

**Independent Test**: Create incomplete transactions in DB, restart service, verify all resumed with checker threads.

### Tests for User Story 5

- [ ] T114 [P] [US5] Write unit test for `SagaRecoveryService` in `order-service/src/test/java/com/ecommerce/order/application/service/SagaRecoveryServiceTest.java`
- [ ] T115 [P] [US5] Write integration test for recovery scenario in `order-service/src/test/java/com/ecommerce/order/integration/RecoveryIntegrationTest.java`

### Implementation for User Story 5

- [ ] T116 [US5] Create `SagaRecoveryService` in `order-service/src/main/java/com/ecommerce/order/application/service/SagaRecoveryService.java`
- [ ] T117 [US5] Create `SagaRecoveryRunner` implementing `ApplicationRunner` in `order-service/src/main/java/com/ecommerce/order/infrastructure/recovery/SagaRecoveryRunner.java`
- [ ] T118 [US5] Add `findUnfinishedTransactions()` query to `TransactionLogPort`

**Checkpoint**: Recovery works. Incomplete txs exist → restart → all resumed within 30 seconds.

---

## Phase 8: User Story 6 - Dynamic Service Configuration (Priority: P6)

**Goal**: Admin can configure service order and timeouts via API without restart.

**Independent Test**: Change service order via API, apply, verify new orders use updated config while existing continue with original.

### Tests for User Story 6

- [ ] T119 [P] [US6] Write contract tests for Admin API endpoints in `order-service/src/test/java/com/ecommerce/order/adapter/in/web/AdminControllerContractTest.java`
- [ ] T120 [P] [US6] Write unit test for `SagaConfigService` in `order-service/src/test/java/com/ecommerce/order/application/service/SagaConfigServiceTest.java`

### Implementation for User Story 6

- [ ] T121 [US6] Create `SagaConfigUseCase` port in `order-service/src/main/java/com/ecommerce/order/application/port/in/SagaConfigUseCase.java`
- [ ] T122 [US6] Create `SagaConfigService` with active/pending config management in `order-service/src/main/java/com/ecommerce/order/application/service/SagaConfigService.java`
- [ ] T123 [US6] Create `SagaConfigPersistenceAdapter` in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/SagaConfigPersistenceAdapter.java`
- [ ] T124 [US6] Create `AdminController` with 6 config endpoints in `order-service/src/main/java/com/ecommerce/order/adapter/in/web/AdminController.java`
- [ ] T125 [US6] Create admin request/response DTOs in `order-service/src/main/java/com/ecommerce/order/adapter/in/web/dto/`

**Checkpoint**: Config API works. PUT config → POST apply → new orders use new config.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Observability, security hardening, and final validation

### Observability

- [ ] T126 [P] Create `SagaMetrics` with Micrometer counters/timers in `order-service/src/main/java/com/ecommerce/order/infrastructure/observability/SagaMetrics.java`
- [ ] T127 [P] Create `TracingConfig` for distributed tracing in `order-service/src/main/java/com/ecommerce/order/infrastructure/observability/TracingConfig.java`
- [ ] T128 [P] Add structured logging with txId correlation across all services

### Documentation & Validation

- [ ] T129 [P] Validate all OpenAPI specs match implemented endpoints
- [ ] T130 Run quickstart.md validation - all commands work
- [ ] T131 Verify test coverage ≥80% for domain/application layers

### BDD Acceptance Tests

- [ ] T132 [P] Write BDD acceptance test for happy path scenario in `order-service/src/test/java/com/ecommerce/order/acceptance/HappyPathAcceptanceTest.java`
- [ ] T133 [P] Write BDD acceptance test for rollback scenario in `order-service/src/test/java/com/ecommerce/order/acceptance/RollbackAcceptanceTest.java`
- [ ] T134 [P] Write BDD acceptance test for timeout scenario in `order-service/src/test/java/com/ecommerce/order/acceptance/TimeoutAcceptanceTest.java`

### Load Testing (SC-002)

- [ ] T135 [P] Add Gatling dependency to `order-service/build.gradle.kts` for load testing
- [ ] T136 [P] Create Gatling load test for 100 orders/sec in `order-service/src/test/scala/com/ecommerce/order/loadtest/OrderLoadTest.scala`
- [ ] T137 Run load test and document results in `specs/001-saga-orchestration/load-test-results.md`

### Availability Monitoring (SC-009)

- [ ] T138 [P] Configure Spring Boot Actuator health endpoints with detailed checks in `order-service/src/main/resources/application.yml`
- [ ] T139 [P] Add availability metric (uptime counter) to `SagaMetrics` in `order-service/src/main/java/com/ecommerce/order/infrastructure/observability/SagaMetrics.java`

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1: Setup (No dependencies)
    │
    ▼
Phase 2: Foundational (BLOCKS all user stories)
    │
    ├──► Phase 3: US1 - Order Confirmation (P1) 🎯 MVP
    │         │
    │         ▼
    ├──► Phase 4: US2 - Rollback (P2) [depends on US1 Camel routes]
    │         │
    │         ▼
    ├──► Phase 5: US3 - Timeout (P3) [depends on US2 rollback]
    │         │
    │         ▼
    ├──► Phase 6: US4 - Escalation (P4) [depends on US2 rollback]
    │
    ├──► Phase 7: US5 - Recovery (P5) [depends on US1 checker thread]
    │
    └──► Phase 8: US6 - Config API (P6) [independent after Foundational]
              │
              ▼
         Phase 9: Polish (depends on all stories)
```

### User Story Dependencies

| Story | Can Start After | Dependencies |
|-------|-----------------|--------------|
| US1 (P1) | Phase 2 | None - MVP |
| US2 (P2) | US1 complete | Needs Camel routes from US1 |
| US3 (P3) | US2 complete | Uses rollback from US2 |
| US4 (P4) | US2 complete | Uses rollback retry from US2 |
| US5 (P5) | US1 complete | Uses checker thread from US1 |
| US6 (P6) | Phase 2 | Independent, config-only |

### Parallel Opportunities

**Within Phase 2 (Foundational)**:
- T010-T016: All common module tasks
- T030-T035: All JPA entities and repositories
- T038-T041: All config files

**Within User Story Phases**:
- All downstream service tasks (T054-T065 for US1)
- All test tasks marked [P]
- All processor tasks marked [P]

---

## Parallel Example: User Story 1

```bash
# Launch all US1 tests in parallel:
Task: "T049 [US1] Contract test for POST /api/v1/orders/confirm"
Task: "T050 [US1] Contract test for GET /api/v1/transactions/{txId}"
Task: "T051 [US1] Unit test for OrderSagaService"
Task: "T052 [US1] Camel route test for OrderSagaRoute"
Task: "T053 [US1] Integration test for WebSocket handler"

# Launch all downstream service implementations in parallel:
Task: "T054-T057 [US1] Credit Card Service"
Task: "T058-T061 [US1] Inventory Service"
Task: "T062-T065 [US1] Logistics Service"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test happy path independently
5. Deploy/demo if ready

### Incremental Delivery

| Increment | Stories | Value Delivered |
|-----------|---------|-----------------|
| MVP | US1 | Order processing with real-time notifications |
| +Reliability | US1 + US2 | Automatic rollback on failure |
| +Resilience | US1-3 | Timeout detection |
| +Operations | US1-4 | Admin escalation |
| +Recovery | US1-5 | Crash recovery |
| Complete | US1-6 | Dynamic configuration |

---

## Summary

| Metric | Count |
|--------|-------|
| **Total Tasks** | 139 |
| **Phase 1 (Setup)** | 9 |
| **Phase 2 (Foundational)** | 39 |
| **US1 (P1)** | 36 |
| **US2 (P2)** | 16 |
| **US3 (P3)** | 7 |
| **US4 (P4)** | 6 |
| **US5 (P5)** | 5 |
| **US6 (P6)** | 7 |
| **Phase 9 (Polish)** | 14 |

---

## Notes

- [P] tasks = different files, no dependencies, can run in parallel
- [Story] label maps task to specific user story for traceability
- Tests marked with ⚠️ MUST be written and FAIL before implementation (TDD)
- Constitution requires ≥80% test coverage for domain/application layers
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
