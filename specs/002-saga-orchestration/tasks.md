# Tasks: E-Commerce Saga Orchestration System

**Input**: Design documents from `/specs/002-saga-orchestration/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Tests are included per constitution mandate IV (TDD) and VIII (Testing Standards). All tests MUST be written FIRST and FAIL before implementation.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create Gradle multi-module project structure per plan.md
- [X] T002 [P] Configure root `build.gradle.kts` with Java 21, Spring Boot 3.2.x plugin
- [X] T003 [P] Configure `settings.gradle.kts` with modules: common, order-service, credit-card-service, inventory-service, logistics-service
- [X] T004 [P] Create `gradle.properties` with shared dependency versions
- [X] T005 [P] Setup `.gitignore` for Gradle, Java, IDE files

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Common Module

- [X] T006 Create `common/build.gradle.kts` with shared dependencies
- [X] T007 [P] Create `TransactionStatus` enum in `common/src/main/java/com/ecommerce/common/domain/TransactionStatus.java` (Pending, Success, Fail, Rollback, RollbackDone, RollbackFail, Skipped)
- [X] T008 [P] Create `ServiceName` enum in `common/src/main/java/com/ecommerce/common/domain/ServiceName.java` (CREDIT_CARD, INVENTORY, LOGISTICS)
- [X] T009 [P] Create `NotifyRequest` DTO in `common/src/main/java/com/ecommerce/common/dto/NotifyRequest.java`
- [X] T010 [P] Create `NotifyResponse` DTO in `common/src/main/java/com/ecommerce/common/dto/NotifyResponse.java`
- [X] T011 [P] Create `RollbackRequest` DTO in `common/src/main/java/com/ecommerce/common/dto/RollbackRequest.java`
- [X] T012 [P] Create `RollbackResponse` DTO in `common/src/main/java/com/ecommerce/common/dto/RollbackResponse.java`
- [X] T013 [P] Create `OrderItem` DTO in `common/src/main/java/com/ecommerce/common/dto/OrderItem.java`
- [X] T014 [P] Create `ServiceException` in `common/src/main/java/com/ecommerce/common/exception/ServiceException.java`

### Order Service Base Structure

- [X] T015 Create `order-service/build.gradle.kts` with Spring Boot, Camel, Resilience4j, WebSocket dependencies
- [X] T016 [P] Create `OrderServiceApplication.java` main class
- [X] T017 [P] Create `application.yml` with H2, Camel, Resilience4j configuration
- [X] T018 Create database schema `order-service/src/main/resources/schema.sql` per data-model.md (transaction_log, outbox_event, saga_config, transaction_service_snapshot tables)
- [X] T019 [P] Create `data.sql` with default service configuration (CREDIT_CARD order=1, INVENTORY order=2, LOGISTICS order=3)

### Domain Layer

- [X] T020 [P] Create `TransactionLog` entity in `order-service/src/main/java/com/ecommerce/order/domain/model/TransactionLog.java`
- [X] T021 [P] Create `OutboxEvent` entity in `order-service/src/main/java/com/ecommerce/order/domain/model/OutboxEvent.java`
- [X] T022 [P] Create `SagaConfig` entity in `order-service/src/main/java/com/ecommerce/order/domain/model/SagaConfig.java`
- [X] T023 [P] Create `TransactionServiceSnapshot` entity in `order-service/src/main/java/com/ecommerce/order/domain/model/TransactionServiceSnapshot.java`
- [X] T024 [P] Create `ServiceConfig` value object in `order-service/src/main/java/com/ecommerce/order/domain/model/ServiceConfig.java`
- [X] T025 [P] Create `TransactionEvent` domain event in `order-service/src/main/java/com/ecommerce/order/domain/event/TransactionEvent.java`

### Application Layer Ports

- [X] T026 [P] Create `OrderConfirmUseCase` port in `order-service/src/main/java/com/ecommerce/order/application/port/in/OrderConfirmUseCase.java`
- [X] T027 [P] Create `TransactionQueryUseCase` port in `order-service/src/main/java/com/ecommerce/order/application/port/in/TransactionQueryUseCase.java`
- [X] T028 [P] Create `SagaConfigUseCase` port in `order-service/src/main/java/com/ecommerce/order/application/port/in/SagaConfigUseCase.java`
- [X] T029 [P] Create `TransactionLogPort` port in `order-service/src/main/java/com/ecommerce/order/application/port/out/TransactionLogPort.java`
- [X] T030 [P] Create `OutboxPort` port in `order-service/src/main/java/com/ecommerce/order/application/port/out/OutboxPort.java`
- [X] T031 [P] Create `SagaConfigPort` port in `order-service/src/main/java/com/ecommerce/order/application/port/out/SagaConfigPort.java`
- [X] T032 [P] Create `WebSocketPort` port in `order-service/src/main/java/com/ecommerce/order/application/port/out/WebSocketPort.java`
- [X] T033 [P] Create `ServiceCallerPort` port in `order-service/src/main/java/com/ecommerce/order/application/port/out/ServiceCallerPort.java`

### Persistence Adapters

- [X] T034 [P] Create `TransactionLogRepository` JPA repository in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/TransactionLogRepository.java`
- [X] T035 [P] Create `OutboxEventRepository` JPA repository in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/OutboxEventRepository.java`
- [X] T036 [P] Create `SagaConfigRepository` JPA repository in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/SagaConfigRepository.java`
- [X] T037 [P] Create `TransactionServiceSnapshotRepository` JPA repository in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/TransactionServiceSnapshotRepository.java`
- [X] T038 Create `TransactionLogAdapter` implementing `TransactionLogPort` in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/TransactionLogAdapter.java`
- [X] T039 [P] Create `OutboxAdapter` implementing `OutboxPort` in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/OutboxAdapter.java`
- [X] T040 [P] Create `SagaConfigAdapter` implementing `SagaConfigPort` in `order-service/src/main/java/com/ecommerce/order/adapter/out/persistence/SagaConfigAdapter.java`

### Participant Services Base

- [X] T041 [P] Create `credit-card-service/build.gradle.kts` with Spring Boot Web dependencies
- [X] T042 [P] Create `inventory-service/build.gradle.kts` with Spring Boot Web dependencies
- [X] T043 [P] Create `logistics-service/build.gradle.kts` with Spring Boot Web dependencies
- [X] T044 [P] Create `CreditCardServiceApplication.java` main class (port 8081)
- [X] T045 [P] Create `InventoryServiceApplication.java` main class (port 8082)
- [X] T046 [P] Create `LogisticsServiceApplication.java` main class (port 8083)
- [X] T047 [P] Create participant `application.yml` files with actuator health endpoints

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Order Confirmation with Full Success (Priority: P1) 🎯 MVP

**Goal**: Implement the happy path flow where order confirmation processes through all services successfully with real-time WebSocket updates

**Independent Test**: Submit order, verify 202 response, verify WebSocket notifications for each service step, verify all services show Success status

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T048 [P] [US1] Contract test for POST /api/v1/orders/confirm in `order-service/src/test/java/com/ecommerce/order/contract/OrderConfirmContractTest.java`
- [X] T049 [P] [US1] Integration test for full happy path in `order-service/src/test/java/com/ecommerce/order/integration/OrderHappyPathIntegrationTest.java`
- [X] T050 [P] [US1] Unit test for `OrderSagaService` in `order-service/src/test/java/com/ecommerce/order/application/service/OrderSagaServiceTest.java`
- [X] T051 [P] [US1] WebSocket integration test in `order-service/src/test/java/com/ecommerce/order/integration/WebSocketNotificationTest.java`

### Implementation for User Story 1

#### Participant Services Notify Endpoints

- [X] T052 [P] [US1] Implement `/api/v1/credit-card/notify` endpoint in `credit-card-service/src/main/java/com/ecommerce/creditcard/controller/CreditCardController.java`
- [X] T053 [P] [US1] Implement `/api/v1/inventory/notify` endpoint in `inventory-service/src/main/java/com/ecommerce/inventory/controller/InventoryController.java`
- [X] T054 [P] [US1] Implement `/api/v1/logistics/notify` endpoint in `logistics-service/src/main/java/com/ecommerce/logistics/controller/LogisticsController.java`

#### Order Service Core

- [X] T055 [US1] Implement `OrderConfirmService` in `order-service/src/main/java/com/ecommerce/order/application/service/OrderConfirmService.java` (depends on T038-T040)
- [X] T056 [US1] Implement `OrderController` POST /api/v1/orders/confirm in `order-service/src/main/java/com/ecommerce/order/adapter/in/web/OrderController.java`
- [X] T057 [US1] Implement `OutboxPoller` with single Camel route in `order-service/src/main/java/com/ecommerce/order/infrastructure/poller/OutboxPoller.java`
- [X] T058 [US1] Implement `ServiceCallerAdapter` with RestTemplate in `order-service/src/main/java/com/ecommerce/order/adapter/out/service/ServiceCallerAdapter.java`
- [X] T059 [US1] Implement `OrderSagaRoute` Camel route for sequential service calls in `order-service/src/main/java/com/ecommerce/order/infrastructure/camel/OrderSagaRoute.java`
- [X] T060 [US1] Implement `OrderSagaService` orchestrating saga execution in `order-service/src/main/java/com/ecommerce/order/application/service/OrderSagaService.java`

#### WebSocket Notifications

- [X] T061 [US1] Configure WebSocket in `order-service/src/main/java/com/ecommerce/order/infrastructure/config/WebSocketConfig.java`
- [X] T062 [US1] Implement `OrderWebSocketHandler` in `order-service/src/main/java/com/ecommerce/order/adapter/in/websocket/OrderWebSocketHandler.java`
- [X] T063 [US1] Implement `WebSocketAdapter` implementing `WebSocketPort` in `order-service/src/main/java/com/ecommerce/order/adapter/out/websocket/WebSocketAdapter.java`
- [X] T064 [US1] Create `WebSocketMessage` DTO in `order-service/src/main/java/com/ecommerce/order/adapter/in/websocket/WebSocketMessage.java`

#### Checker Thread (Basic)

- [X] T065 [US1] Implement `CheckerThread` for transaction monitoring in `order-service/src/main/java/com/ecommerce/order/infrastructure/checker/CheckerThread.java`
- [X] T066 [US1] Implement `CheckerThreadManager` for thread lifecycle in `order-service/src/main/java/com/ecommerce/order/infrastructure/checker/CheckerThreadManager.java`
- [X] T067 [US1] Integrate CheckerThread startup in `OrderSagaService` (stops when all Success)

**Checkpoint**: Order confirmation happy path complete with WebSocket notifications

---

## Phase 4: User Story 2 - Automatic Rollback on Service Failure (Priority: P1) 🎯 MVP

**Goal**: Implement automatic rollback of successful services when any service fails, with reverse-order execution

**Independent Test**: Simulate inventory failure after payment success, verify payment rollback, verify RollbackDone status, verify WebSocket rollback notifications

### Tests for User Story 2 ⚠️

- [X] T068 [P] [US2] Integration test for rollback on failure in `order-service/src/test/java/com/ecommerce/order/integration/RollbackOnFailureIntegrationTest.java`
- [X] T069 [P] [US2] Unit test for `RollbackService` in `order-service/src/test/java/com/ecommerce/order/application/service/RollbackServiceTest.java`
- [X] T070 [P] [US2] Participant rollback idempotency test in `order-service/src/test/java/com/ecommerce/order/contract/ParticipantRollbackContractTest.java`

### Implementation for User Story 2

#### Participant Rollback Endpoints

- [X] T071 [P] [US2] Implement `/api/v1/credit-card/rollback` endpoint (idempotent) in `credit-card-service/src/main/java/com/ecommerce/creditcard/controller/CreditCardController.java`
- [X] T072 [P] [US2] Implement `/api/v1/inventory/rollback` endpoint (idempotent) in `inventory-service/src/main/java/com/ecommerce/inventory/controller/InventoryController.java`
- [X] T073 [P] [US2] Implement `/api/v1/logistics/rollback` endpoint (idempotent) in `logistics-service/src/main/java/com/ecommerce/logistics/controller/LogisticsController.java`

#### Participant Test Endpoints

- [X] T074 [P] [US2] Add `/test/fail-next` endpoint to credit-card-service for testing
- [X] T075 [P] [US2] Add `/test/fail-next` endpoint to inventory-service for testing
- [X] T076 [P] [US2] Add `/test/fail-next` endpoint to logistics-service for testing

#### Rollback Logic

- [X] T077 [US2] Implement `RollbackService` with reverse-order rollback in `order-service/src/main/java/com/ecommerce/order/application/service/RollbackService.java`
- [X] T078 [US2] Implement `RollbackRoute` Camel route in `order-service/src/main/java/com/ecommerce/order/infrastructure/camel/RollbackRoute.java`
- [X] T079 [US2] Integrate failure detection in `OrderSagaService` to trigger rollback
- [X] T080 [US2] Update `CheckerThread` to stop when all RollbackDone/Skipped
- [X] T081 [US2] Add rollback WebSocket notifications in `WebSocketAdapter`

**Checkpoint**: Automatic rollback on service failure complete

---

## Phase 5: User Story 3 - Timeout Detection and Automatic Recovery (Priority: P1) 🎯 MVP

**Goal**: Detect hanging services and trigger automatic rollback after configured timeout period

**Independent Test**: Simulate logistics service delay beyond timeout, verify timeout detection, verify automatic rollback of prior services

### Tests for User Story 3 ⚠️

- [X] T082 [P] [US3] Integration test for timeout detection in `order-service/src/test/java/com/ecommerce/order/integration/TimeoutDetectionIntegrationTest.java`
- [X] T083 [P] [US3] Unit test for CheckerThread timeout logic in `order-service/src/test/java/com/ecommerce/order/infrastructure/checker/CheckerThreadTest.java`

### Implementation for User Story 3

#### Participant Delay Endpoints

- [X] T084 [P] [US3] Add `/test/delay` endpoint to logistics-service for timeout testing
- [X] T085 [P] [US3] Add `/test/delay` endpoint to credit-card-service for timeout testing
- [X] T086 [P] [US3] Add `/test/delay` endpoint to inventory-service for timeout testing

#### Timeout Logic

- [X] T087 [US3] Enhance `CheckerThread` with per-service timeout detection based on snapshot config
- [X] T088 [US3] Implement timeout-triggered rollback in `CheckerThread`
- [X] T089 [US3] Record Fail status on timeout in `TransactionLogAdapter`
- [X] T090 [US3] Add timeout WebSocket notifications

**Checkpoint**: All P1 MVP stories complete - system handles success, failure, and timeout

---

## Phase 6: User Story 4 - Transaction Status Inquiry (Priority: P2)

**Goal**: Enable querying transaction status by order ID or transaction ID for customer support

**Independent Test**: Query by orderId returns all transaction attempts, query by txId returns detailed service statuses

### Tests for User Story 4 ⚠️

- [X] T091 [P] [US4] Contract test for GET /api/v1/transactions in `order-service/src/test/java/com/ecommerce/order/contract/TransactionQueryContractTest.java`
- [X] T092 [P] [US4] Integration test for query endpoints in `order-service/src/test/java/com/ecommerce/order/integration/TransactionQueryIntegrationTest.java`

### Implementation for User Story 4

- [X] T093 [US4] Implement `TransactionQueryService` in `order-service/src/main/java/com/ecommerce/order/application/service/TransactionQueryService.java`
- [X] T094 [US4] Add query methods to `TransactionLogRepository` (by orderId, by txId with latest status per service)
- [X] T095 [US4] Implement GET /api/v1/transactions endpoint in `order-service/src/main/java/com/ecommerce/order/adapter/in/web/TransactionController.java`
- [X] T096 [US4] Create `TransactionDetail` response DTO
- [X] T097 [US4] Create `TransactionQueryByOrderResponse` response DTO

**Checkpoint**: Transaction query capability complete

---

## Phase 7: User Story 5 - Dynamic Service Configuration (Priority: P2)

**Goal**: Enable runtime modification of service order, timeouts, and service registry with active/pending model

**Independent Test**: Modify service order via admin API, apply changes, verify new orders use updated configuration

### Tests for User Story 5 ⚠️

- [X] T098 [P] [US5] Contract tests for admin APIs in `order-service/src/test/java/com/ecommerce/order/contract/AdminApiContractTest.java`
- [X] T099 [P] [US5] Integration test for config changes in `order-service/src/test/java/com/ecommerce/order/integration/SagaConfigIntegrationTest.java`

### Implementation for User Story 5

- [X] T100 [US5] Implement `SagaConfigService` with active/pending model in `order-service/src/main/java/com/ecommerce/order/application/service/SagaConfigService.java`
- [X] T101 [US5] Implement `AdminController` with service-order endpoints in `order-service/src/main/java/com/ecommerce/order/adapter/in/web/AdminController.java`
- [X] T102 [US5] Add timeout configuration endpoints to `AdminController`
- [X] T103 [US5] Add service registry endpoints (add/remove services) to `AdminController`
- [X] T104 [US5] Implement `TransactionServiceSnapshotAdapter` for capturing config at transaction start
- [X] T105 [US5] Integrate config snapshot creation in `OrderConfirmService`

**Checkpoint**: Dynamic configuration management complete

---

## Phase 8: User Story 6 - Circuit Breaker Protection (Priority: P2)

**Goal**: Protect system from cascade failures by stopping calls to failing services temporarily

**Independent Test**: Fail credit card service 10 times, verify circuit opens, verify subsequent calls fail fast, verify recovery after half-open probe succeeds

### Tests for User Story 6 ⚠️

- [X] T106 [P] [US6] Integration test for circuit breaker behavior in `order-service/src/test/java/com/ecommerce/order/integration/CircuitBreakerIntegrationTest.java`
- [X] T107 [P] [US6] Unit test for ServiceCircuitBreaker in `order-service/src/test/java/com/ecommerce/order/infrastructure/circuitbreaker/ServiceCircuitBreakerTest.java`

### Implementation for User Story 6

- [X] T108 [US6] Configure Resilience4j circuit breaker in `application.yml` (50% threshold, 10-call window, 30s open duration)
- [X] T109 [US6] Implement `ServiceCircuitBreaker` wrapper in `order-service/src/main/java/com/ecommerce/order/infrastructure/circuitbreaker/ServiceCircuitBreaker.java`
- [X] T110 [US6] Integrate circuit breaker in `ServiceCallerAdapter`
- [X] T111 [US6] Add circuit breaker state actuator endpoint
- [X] T112 [US6] Handle circuit open state in `OrderSagaService` (immediate failure)

**Checkpoint**: Circuit breaker protection complete

---

## Phase 9: User Story 7 - Service Recovery After Restart (Priority: P3)

**Goal**: Automatically recover incomplete transactions when order service restarts

**Independent Test**: Stop service mid-transaction, restart, verify transaction continues or rolls back appropriately

### Tests for User Story 7 ⚠️

- [X] T113 [P] [US7] Integration test for recovery on startup in `order-service/src/test/java/com/ecommerce/order/integration/StartupRecoveryIntegrationTest.java`

### Implementation for User Story 7

- [X] T114 [US7] Implement `TransactionRecoveryService` in `order-service/src/main/java/com/ecommerce/order/application/service/TransactionRecoveryService.java`
- [X] T115 [US7] Add startup event listener to scan incomplete transactions
- [X] T116 [US7] Integrate recovery with `CheckerThreadManager` to resume monitoring
- [X] T117 [US7] Add recovery logging and metrics

**Checkpoint**: Service restart recovery complete

---

## Phase 10: User Story 8 - Rollback Failure Handling (Priority: P3)

**Goal**: Notify administrators when rollback fails after retry limit, providing details for manual intervention

**Independent Test**: Simulate rollback failure exceeding retry limit, verify RollbackFail status, verify admin notification

### Tests for User Story 8 ⚠️

- [X] T118 [P] [US8] Integration test for rollback failure notification in `order-service/src/test/java/com/ecommerce/order/integration/RollbackFailureNotificationTest.java`

### Implementation for User Story 8

- [X] T119 [US8] Implement rollback retry logic in `RollbackService` (max 5 retries with exponential backoff)
- [X] T120 [US8] Implement `AdminNotificationPort` in `order-service/src/main/java/com/ecommerce/order/application/port/out/AdminNotificationPort.java`
- [X] T121 [US8] Implement `EmailNotificationAdapter` (stub for development) in `order-service/src/main/java/com/ecommerce/order/adapter/out/notification/EmailNotificationAdapter.java`
- [X] T122 [US8] Record notifiedAt timestamp on RollbackFail notification
- [X] T123 [US8] Update `CheckerThread` to stop and notify on RollbackFail

**Checkpoint**: All user stories complete

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Final improvements affecting multiple user stories

- [X] T124 [P] Add Swagger/OpenAPI documentation configuration
- [X] T125 [P] Configure structured logging with correlation ID (txId)
- [X] T126 [P] Add Prometheus metrics for transactions, circuit breaker, timeouts
- [X] T127 [P] Create H2 console configuration for development
- [X] T128 [P] Add global exception handler with proper error responses
- [X] T129 Run full integration test suite
- [X] T130 Validate quickstart.md scenarios manually
- [X] T131 Code cleanup and final refactoring

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies - can start immediately
- **Phase 2 (Foundational)**: Depends on Setup - **BLOCKS all user stories**
- **Phases 3-5 (US1-US3, P1)**: Depend on Foundational, execute sequentially (US2 needs US1, US3 needs US2)
- **Phases 6-8 (US4-US6, P2)**: Depend on Foundational, can run in parallel after P1 complete
- **Phases 9-10 (US7-US8, P3)**: Depend on Foundational, can run in parallel after P2 complete
- **Phase 11 (Polish)**: Depends on all user stories complete

### User Story Dependencies

| Story | Depends On | Can Parallel With |
|-------|------------|-------------------|
| US1 (P1) | Foundational | - |
| US2 (P1) | US1 | - |
| US3 (P1) | US2 | - |
| US4 (P2) | Foundational | US5, US6 |
| US5 (P2) | Foundational | US4, US6 |
| US6 (P2) | Foundational | US4, US5 |
| US7 (P3) | Foundational | US8 |
| US8 (P3) | US2 (needs rollback) | US7 |

### Within Each User Story

1. Tests MUST be written and FAIL before implementation
2. Participant endpoints before orchestrator logic
3. Domain/Application layer before Adapter layer
4. Core implementation before integration
5. Verify all acceptance scenarios pass

---

## Task Summary

| Phase | Story | Priority | Task Count |
|-------|-------|----------|------------|
| 1 | Setup | - | 5 |
| 2 | Foundational | - | 42 |
| 3 | US1 - Happy Path | P1 | 20 |
| 4 | US2 - Rollback | P1 | 14 |
| 5 | US3 - Timeout | P1 | 9 |
| 6 | US4 - Query | P2 | 7 |
| 7 | US5 - Config | P2 | 8 |
| 8 | US6 - Circuit Breaker | P2 | 7 |
| 9 | US7 - Recovery | P3 | 5 |
| 10 | US8 - Rollback Fail | P3 | 6 |
| 11 | Polish | - | 8 |
| **Total** | | | **131** |

---

## MVP Delivery Strategy

### Minimum Viable Product (Phases 1-5)

Complete Phases 1-5 to deliver core functionality:
- Order confirmation with saga orchestration
- Automatic rollback on failure
- Timeout detection and recovery
- Real-time WebSocket notifications

**MVP Task Count**: 90 tasks

### Incremental Additions

1. **+Phase 6-8**: Add transaction queries, dynamic config, circuit breaker (+22 tasks)
2. **+Phase 9-10**: Add restart recovery, rollback failure handling (+11 tasks)
3. **+Phase 11**: Polish and production readiness (+8 tasks)
