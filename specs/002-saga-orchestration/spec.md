# Feature Specification: E-Commerce Saga Orchestration System

**Feature Branch**: `002-saga-orchestration`
**Created**: 2026-01-01
**Status**: Draft
**Input**: User description: "E-Commerce Saga Orchestration System with Outbox Pattern, Circuit Breaker, and dynamic service configuration for distributed transaction management"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Order Confirmation with Full Success (Priority: P1)

As a customer, when I confirm my purchase order, I want the system to reliably process payment, reserve inventory, and schedule shipping in sequence, with real-time status updates, so that I know my order is being processed successfully.

**Why this priority**: This is the core happy-path that delivers the primary business value. Without successful order processing, the e-commerce platform cannot generate revenue.

**Independent Test**: Can be fully tested by submitting an order and verifying all three services (credit card, inventory, logistics) complete successfully with WebSocket notifications received at each step.

**Acceptance Scenarios**:

1. **Given** customer has items in cart and valid payment method, **When** customer confirms the order, **Then** system responds with 202 Accepted and a unique transaction ID (TxID)
2. **Given** order confirmation received, **When** credit card payment succeeds, **Then** WebSocket pushes "Payment successful" notification
3. **Given** payment completed, **When** inventory reservation succeeds, **Then** WebSocket pushes "Inventory reserved" notification
4. **Given** inventory reserved, **When** shipping scheduled successfully, **Then** WebSocket pushes "Shipping scheduled" and "Order complete" notifications
5. **Given** all services complete successfully, **When** order processing finishes, **Then** all service statuses are recorded as Success

---

### User Story 2 - Automatic Rollback on Service Failure (Priority: P1)

As a customer, when any step in my order processing fails, I want the system to automatically reverse all previously completed steps, so that I am not charged for items that cannot be delivered.

**Why this priority**: Critical for data consistency and customer trust. Failed transactions without proper rollback lead to financial discrepancies and customer complaints.

**Independent Test**: Can be fully tested by simulating a service failure (e.g., inventory out of stock) and verifying that all prior successful steps (e.g., payment) are automatically reversed.

**Acceptance Scenarios**:

1. **Given** payment has succeeded, **When** inventory service returns failure, **Then** system initiates rollback of payment
2. **Given** rollback initiated, **When** payment rollback completes, **Then** rollback status is recorded as RollbackDone
3. **Given** rollback completes, **When** all rollbacks finish, **Then** WebSocket pushes "Order failed, refund processed" notification
4. **Given** a service fails, **When** rollback is triggered, **Then** rollback occurs in reverse order (last successful service first)

---

### User Story 3 - Timeout Detection and Automatic Recovery (Priority: P1)

As a system operator, I want services that hang without responding to be automatically detected and recovered, so that orders don't remain in limbo indefinitely.

**Why this priority**: Essential for system reliability. Without timeout handling, stuck transactions can accumulate and degrade system performance.

**Independent Test**: Can be fully tested by simulating a service that doesn't respond within the configured timeout and verifying automatic rollback is triggered.

**Acceptance Scenarios**:

1. **Given** a service is in Pending state, **When** configured timeout period elapses, **Then** system records the service as failed
2. **Given** timeout detected, **When** prior services have succeeded, **Then** system initiates rollback of all successful services
3. **Given** timeout-triggered rollback, **When** rollback completes, **Then** transaction is marked as rolled back

---

### User Story 4 - Transaction Status Inquiry (Priority: P2)

As a customer service representative, I want to look up order transaction status by order ID or transaction ID, so that I can assist customers with their order inquiries.

**Why this priority**: Important for customer support operations. Enables tracking and troubleshooting of order issues.

**Independent Test**: Can be fully tested by querying transactions by order ID and transaction ID, verifying correct status information is returned.

**Acceptance Scenarios**:

1. **Given** an order has multiple transaction attempts, **When** querying by order ID, **Then** system returns all transaction histories for that order
2. **Given** a specific transaction ID, **When** querying by TxID, **Then** system returns detailed status of each service in that transaction
3. **Given** a transaction in progress, **When** querying status, **Then** system shows real-time status of each service step

---

### User Story 5 - Dynamic Service Configuration (Priority: P2)

As a system administrator, I want to modify the order of services, add new services, or remove services from the transaction flow, so that I can adapt the system to changing business requirements without code changes.

**Why this priority**: Enables business agility. Different promotions or business rules may require different service configurations.

**Independent Test**: Can be fully tested by modifying service configuration through admin interface and verifying new orders use updated configuration.

**Acceptance Scenarios**:

1. **Given** current service order is Payment -> Inventory -> Logistics, **When** admin changes order to Inventory -> Payment -> Logistics, **Then** new orders follow the updated sequence
2. **Given** a new service is added to configuration, **When** new order is placed, **Then** the new service is included in the transaction flow
3. **Given** a service is removed from configuration, **When** new order is placed, **Then** the removed service is excluded from the transaction flow
4. **Given** configuration change is pending, **When** admin applies the change, **Then** only new orders use the new configuration; in-progress orders are unaffected

---

### User Story 6 - Circuit Breaker Protection (Priority: P2)

As a system operator, I want the system to stop calling failing services temporarily, so that the system can recover gracefully and not overwhelm struggling services.

**Why this priority**: Improves system resilience. Prevents cascade failures when a service is experiencing issues.

**Independent Test**: Can be fully tested by simulating repeated service failures and verifying the circuit breaker opens, then verifying it tests and closes after recovery.

**Acceptance Scenarios**:

1. **Given** a service has failed multiple times exceeding threshold, **When** circuit breaker opens, **Then** subsequent calls fail immediately without calling the service
2. **Given** circuit breaker is open, **When** wait period elapses, **Then** circuit breaker enters half-open state allowing test requests
3. **Given** circuit breaker is half-open, **When** test request succeeds, **Then** circuit breaker closes and normal operation resumes
4. **Given** circuit breaker is half-open, **When** test request fails, **Then** circuit breaker reopens

---

### User Story 7 - Service Recovery After Restart (Priority: P3)

As a system operator, I want incomplete transactions to be automatically recovered when the order service restarts, so that no transactions are lost due to system maintenance or crashes.

**Why this priority**: Ensures data durability. Customers should not lose orders due to system restarts.

**Independent Test**: Can be fully tested by stopping the service mid-transaction, restarting, and verifying the transaction continues or rolls back appropriately.

**Acceptance Scenarios**:

1. **Given** there are incomplete transactions in the database, **When** order service starts, **Then** system resumes monitoring all incomplete transactions
2. **Given** an incomplete transaction needs rollback, **When** service recovers it, **Then** rollback proceeds as normal
3. **Given** an incomplete transaction can continue, **When** service recovers it, **Then** remaining steps are processed

---

### User Story 8 - Rollback Failure Handling (Priority: P3)

As a system operator, I want to be notified when a rollback fails after multiple retries, so that I can manually intervene and resolve the issue.

**Why this priority**: Safety net for edge cases. While rare, failed rollbacks require human attention to prevent data inconsistencies.

**Independent Test**: Can be fully tested by simulating a rollback that fails repeatedly and verifying notification is sent after retry limit is reached.

**Acceptance Scenarios**:

1. **Given** rollback attempt fails, **When** retry limit is reached, **Then** transaction is marked as RollbackFail
2. **Given** rollback has failed, **When** failure is recorded, **Then** administrator is notified via email
3. **Given** a RollbackFail state, **When** administrator reviews, **Then** error details are available for troubleshooting

---

### Edge Cases

- What happens when the same order is submitted multiple times rapidly? System generates unique TxIDs and processes each independently.
- How does the system handle partial network failures during rollback? Rollback retries with exponential backoff up to configured limit.
- What happens when WebSocket connection is lost during processing? Order continues processing; client can reconnect and query status.
- What happens when database write fails during Outbox pattern? Transaction is rolled back atomically, ensuring consistency.
- What happens when a service returns success but the acknowledgment is lost? Service supports idempotent operations; retry is safe.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST accept order confirmation requests and respond with 202 Accepted status and a unique Transaction ID (TxID)
- **FR-002**: System MUST write transaction events to Outbox table atomically with business data
- **FR-003**: System MUST process Outbox events sequentially through a single Poller
- **FR-004**: System MUST call participating services in configurable order (default: Credit Card -> Inventory -> Logistics)
- **FR-005**: System MUST wait for each service response before calling the next service
- **FR-006**: System MUST trigger rollback when any service returns failure
- **FR-007**: System MUST monitor service failure rates and activate circuit breaker when threshold exceeded
- **FR-008**: System MUST return immediate failure when circuit breaker is open
- **FR-009**: System MUST probe service health when circuit breaker enters half-open state
- **FR-010**: System MUST close circuit breaker when probe succeeds
- **FR-011**: System MUST start a dedicated monitoring thread for each transaction
- **FR-012**: System MUST stop monitoring thread when all services succeed
- **FR-013**: System MUST stop monitoring thread when all rollbacks complete
- **FR-014**: System MUST stop monitoring thread when any rollback fails
- **FR-015**: System MUST detect and handle service timeout for Pending states
- **FR-016**: System MUST continue monitoring until transaction reaches terminal state
- **FR-017**: System MUST record Pending status when service is called
- **FR-018**: System MUST record Success status when service responds successfully
- **FR-019**: System MUST record Fail status when service responds with failure
- **FR-020**: System MUST record Rollback status when initiating rollback
- **FR-021**: System MUST record RollbackDone status when rollback succeeds
- **FR-022**: System MUST record RollbackFail status when rollback retries exhausted
- **FR-023**: System MUST record Skipped status for services not called
- **FR-024**: System MUST rollback successful services immediately on failure
- **FR-025**: System MUST rollback on timeout detection
- **FR-026**: System MUST rollback in reverse order of success
- **FR-027**: System MUST retry failed rollbacks up to configured limit
- **FR-028**: System MUST notify administrators when rollback fails
- **FR-029**: System MUST support idempotent rollback operations
- **FR-030**: System MUST establish WebSocket connections for real-time updates
- **FR-031**: System MUST push status changes via WebSocket
- **FR-032**: System MUST include TxId, orderId, status, currentStep, message, and timestamp in notifications
- **FR-033**: System MUST scan for incomplete transactions on startup
- **FR-034**: System MUST resume monitoring for recovered transactions
- **FR-035**: System MUST support querying transactions by Order ID
- **FR-036**: System MUST support querying transactions by TxID
- **FR-037**: System MUST allow querying current service order
- **FR-038**: System MUST allow modifying service order (pending until applied)
- **FR-039**: System MUST allow applying pending service order changes
- **FR-040**: System MUST allow querying timeout settings
- **FR-041**: System MUST allow modifying timeout settings (pending until applied)
- **FR-042**: System MUST allow applying pending timeout changes
- **FR-043**: System MUST allow querying participating services
- **FR-044**: System MUST allow adding new services (pending until applied)
- **FR-045**: System MUST allow removing services (pending until applied)
- **FR-046**: System MUST allow applying pending service changes
- **FR-047**: System MUST ensure atomicity between business data and Outbox writes
- **FR-048**: System MUST record all state changes as immutable events (append-only)

### Key Entities

- **Transaction (TxID)**: Represents a single saga execution attempt. Contains unique identifier, associated order ID, creation timestamp, and overall status. One order can have multiple transaction attempts.
- **Order (Order ID)**: Business-level order identifier visible to customers. One-to-many relationship with transactions.
- **Service Status Record**: Captures the state of each participating service within a transaction. Includes service name, current status, error messages, retry counts, and timestamps.
- **Service Configuration**: Defines participating services, their order, URLs, and timeout settings. Supports active vs pending configurations.
- **Outbox Event**: Transactional message record ensuring reliable event delivery. Contains event payload and processing status.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Order confirmation response time is under 200 milliseconds (excluding downstream service processing)
- **SC-002**: Complete order processing time is under 3 seconds (excluding individual service response times)
- **SC-003**: System supports at least 100 concurrent order confirmations per second
- **SC-004**: System achieves 99.9% uptime for order processing capability
- **SC-005**: Transaction status is queryable in real-time throughout the processing lifecycle
- **SC-006**: System automatically recovers all incomplete transactions within 30 seconds of restart
- **SC-007**: Circuit breaker activates within 5 failed requests when failure rate exceeds 50%
- **SC-008**: All service state changes are visible via WebSocket within 1 second of occurrence
- **SC-009**: Rollback operations complete within 5 seconds per service (excluding service response time)
- **SC-010**: 100% of timeout conditions are detected and handled within configured timeout period plus 5 seconds
- **SC-011**: Configuration changes take effect for new orders within 1 second of application
- **SC-012**: Zero data inconsistencies between Outbox events and business data under any failure scenario

## Assumptions

The following assumptions were made based on the PRD and industry best practices:

1. **Email Notification**: Administrator notifications via email will be simulated during development phase with a configurable interface for production integration.
2. **Default Timeouts**: Credit Card (30s), Inventory (60s), Logistics (120s) as specified in PRD.
3. **Circuit Breaker Defaults**: 50% failure threshold, 10-call sliding window, 30-second open duration, 3 half-open probes.
4. **Rollback Retry**: Maximum 5 retry attempts with exponential backoff.
5. **Database**: Single database instance for order service with support for transactions.
6. **Service Communication**: Synchronous REST API calls between services.
7. **WebSocket**: Server-initiated push notifications; client reconnection is client's responsibility.
