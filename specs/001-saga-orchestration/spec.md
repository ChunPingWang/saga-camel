# Feature Specification: E-Commerce Saga Orchestration System

**Feature Branch**: `001-saga-orchestration`
**Created**: 2026-01-01
**Status**: Draft
**Input**: PRD.md - 電子商務微服務交易編排系統

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Customer Order Confirmation with Real-time Progress (Priority: P1)

As a customer purchasing products on the e-commerce platform, when I confirm my order, I want to receive immediate acknowledgment and real-time progress updates for each step (payment, inventory reservation, shipping schedule) so that I can track my order status without refreshing the page.

**Why this priority**: This is the core happy-path user experience. Without successful order processing with visibility, the system provides no value. Customers need confidence that their order is being processed correctly.

**Independent Test**: Can be fully tested by submitting an order and verifying that all three services (payment, inventory, logistics) complete successfully with real-time notifications delivered to the customer.

**Acceptance Scenarios**:

1. **Given** customer has items in cart and is logged in, **When** customer confirms the order, **Then** system immediately responds with acceptance and provides a unique transaction identifier

2. **Given** order is being processed, **When** each service step completes successfully, **Then** customer receives a real-time notification indicating which step completed (payment processed, inventory reserved, shipping scheduled)

3. **Given** all three services have completed successfully, **When** the final step finishes, **Then** customer receives a "Order Complete" notification and all transaction states show success

4. **Given** customer has established a real-time connection, **When** order processing begins, **Then** notifications are pushed without customer needing to poll or refresh

---

### User Story 2 - Automatic Rollback on Service Failure (Priority: P2)

As the system, when any downstream service fails during order processing, I need to automatically compensate (rollback) all previously successful services in reverse order, so that the system maintains data consistency and customers are not charged for failed orders.

**Why this priority**: This is critical for data integrity. Without proper compensation, customers could be charged without receiving products, or inventory could be locked incorrectly. This is the second-most important flow after happy path.

**Independent Test**: Can be tested by simulating a failure in one service (e.g., inventory) after another has succeeded (e.g., payment), then verifying that the successful service is rolled back and customer is notified of the failure.

**Acceptance Scenarios**:

1. **Given** payment has succeeded and inventory service fails, **When** failure is detected, **Then** system triggers rollback of payment service and records failure status

2. **Given** rollback is triggered, **When** compensation APIs are called, **Then** services are rolled back in reverse order of their original execution

3. **Given** rollback is in progress, **When** each rollback completes, **Then** customer receives real-time notification of rollback progress

4. **Given** all rollbacks complete, **When** compensation flow finishes, **Then** system records completion status and customer receives "Order Failed - Refunded" notification

---

### User Story 3 - Timeout Detection and Automatic Compensation (Priority: P3)

As the system, when any service remains in "processing" state longer than its configured timeout threshold, I need to detect this and automatically trigger the compensation flow, so that orders do not remain stuck indefinitely.

**Why this priority**: Timeout handling prevents orders from being stuck in limbo. While less common than explicit failures, stuck transactions damage customer trust and create support burden.

**Independent Test**: Can be tested by configuring a short timeout for a service, simulating a slow response that exceeds the timeout, and verifying that rollback is triggered automatically.

**Acceptance Scenarios**:

1. **Given** a service has been in "processing" state for configured timeout duration, **When** monitoring detects the timeout, **Then** system triggers compensation flow

2. **Given** timeout is detected for inventory service, **When** rollback begins, **Then** both the timed-out service and any previously successful services are rolled back

3. **Given** different services have different timeout thresholds, **When** monitoring checks status, **Then** each service is evaluated against its own configured timeout

---

### User Story 4 - Rollback Failure Escalation (Priority: P4)

As an administrator, when a rollback operation fails after maximum retry attempts, I need to be notified immediately, so that I can manually intervene and resolve the inconsistency.

**Why this priority**: This is a safety net for edge cases. While rare, failed rollbacks require human intervention to prevent data inconsistency and customer dissatisfaction.

**Independent Test**: Can be tested by simulating repeated rollback failures (exceeding retry limit) and verifying that administrator notification is sent with all relevant transaction details.

**Acceptance Scenarios**:

1. **Given** rollback has failed and maximum retries (5) are exhausted, **When** final retry fails, **Then** system records "rollback failed" status with error details

2. **Given** rollback failed status is recorded, **When** notification is triggered, **Then** administrator receives alert with transaction ID, failed service, and error message

3. **Given** notification is sent, **When** recording the notification, **Then** system logs the notification timestamp for audit purposes

---

### User Story 5 - Service Recovery After Restart (Priority: P5)

As the system, when the order service restarts, I need to automatically scan for and resume processing of incomplete transactions, so that no orders are lost or left in inconsistent states due to service interruptions.

**Why this priority**: This ensures system resilience. Service restarts are inevitable, and the system must recover gracefully without manual intervention or data loss.

**Independent Test**: Can be tested by creating incomplete transactions, restarting the order service, and verifying that all incomplete transactions are automatically resumed for either completion or rollback.

**Acceptance Scenarios**:

1. **Given** service has restarted and incomplete transactions exist, **When** service initialization completes, **Then** system scans and identifies all incomplete transactions

2. **Given** incomplete transactions are identified, **When** recovery process starts, **Then** each transaction gets its own monitoring thread for continued processing

3. **Given** transaction was mid-processing before restart, **When** recovery resumes, **Then** system either continues forward (if possible) or triggers rollback (if failure detected)

---

### User Story 6 - Dynamic Service Configuration (Priority: P6)

As an administrator, I want to configure the service execution order and timeout thresholds through management APIs, so that I can adjust system behavior without redeploying the application.

**Why this priority**: This enables operational flexibility. While not required for core functionality, it allows fine-tuning of the system based on real-world performance and business needs.

**Independent Test**: Can be tested by changing service order configuration via API, applying the change, and verifying that new orders use the updated configuration while existing orders continue with their original configuration.

**Acceptance Scenarios**:

1. **Given** administrator wants to query current configuration, **When** query API is called, **Then** system returns both active and pending configurations

2. **Given** administrator submits new configuration, **When** configuration is saved, **Then** it is stored as pending until explicitly applied

3. **Given** pending configuration exists, **When** apply API is called, **Then** new orders use the updated configuration while in-progress orders continue with original

4. **Given** timeout thresholds need adjustment, **When** new values are applied, **Then** monitoring uses updated thresholds for new transactions

---

### Edge Cases

- What happens when customer loses real-time connection mid-transaction?
  - Transaction continues processing; customer can reconnect using transaction ID to receive remaining updates
- How does system handle duplicate order confirmation requests?
  - Each confirmation generates a unique transaction ID; duplicate submissions are treated as separate transactions (idempotency at order level, not confirmation level)
- What happens when rollback API is called for a non-existent transaction?
  - Rollback APIs are idempotent and return success even if the transaction doesn't exist
- How does system handle partial state (e.g., service responded but network failed before recording)?
  - Event sourcing with append-only logs ensures all state transitions are durable; recovery process reconstructs last known state
- What happens when all services fail simultaneously during rollback?
  - Each service is rolled back independently with retries; multiple RF (Rollback Failed) statuses may be recorded

## Requirements *(mandatory)*

### Functional Requirements

**Order Processing**
- **FR-001**: System MUST accept order confirmation requests and immediately respond with a unique transaction identifier
- **FR-002**: System MUST process order confirmations asynchronously after acknowledgment
- **FR-003**: System MUST call downstream services in configurable order (default: payment, inventory, logistics)
- **FR-004**: System MUST wait for each service to respond before calling the next service
- **FR-005**: System MUST push real-time status updates to customers for each processing step

**State Management**
- **FR-006**: System MUST record transaction state before calling each service (status: Uncommitted)
- **FR-007**: System MUST record success status when service responds successfully
- **FR-008**: System MUST record failure status when service responds with error
- **FR-009**: System MUST record rollback status after executing compensation
- **FR-010**: System MUST record completion status when rollback flow finishes
- **FR-011**: System MUST record rollback-failed status when compensation exhausts retries
- **FR-012**: System MUST use append-only state recording (no updates to existing records)

**Compensation (Rollback)**
- **FR-013**: System MUST trigger compensation when any service fails
- **FR-014**: System MUST trigger compensation when any service exceeds timeout threshold
- **FR-015**: System MUST rollback services in reverse order of successful execution
- **FR-016**: System MUST retry failed rollback operations up to configured maximum (default: 5)
- **FR-017**: System MUST support idempotent rollback APIs (success even for non-existent transactions)

**Monitoring and Notifications**
- **FR-018**: System MUST monitor each transaction for timeout conditions
- **FR-019**: System MUST stop monitoring when transaction reaches terminal state (all success, done, or rollback-failed)
- **FR-020**: System MUST notify administrators when rollback fails after maximum retries
- **FR-021**: System MUST record notification timestamp for audit purposes

**Recovery**
- **FR-022**: System MUST scan for incomplete transactions on startup
- **FR-023**: System MUST resume monitoring for all incomplete transactions after restart
- **FR-024**: System MUST continue processing or trigger rollback based on last known state

**Configuration Management**
- **FR-025**: System MUST provide API to query current service order configuration
- **FR-026**: System MUST provide API to update service order (staged, not immediately active)
- **FR-027**: System MUST provide API to apply staged configuration changes
- **FR-028**: System MUST provide API to query current timeout configuration
- **FR-029**: System MUST provide API to update timeout thresholds (staged, not immediately active)
- **FR-030**: System MUST provide API to apply staged timeout changes

**Data Consistency**
- **FR-031**: System MUST ensure business data and event data are written atomically
- **FR-032**: System MUST use single event processor to ensure ordered processing

### Key Entities

- **Transaction**: Represents a single order processing flow, identified by unique transaction ID (TxID), linked to order ID, tracks overall progress
- **Transaction Log Entry**: Immutable record of a state transition, contains service name, status code, timestamp, and optional error message
- **Service Configuration**: Defines execution order and endpoints for each downstream service (payment, inventory, logistics)
- **Timeout Configuration**: Defines maximum wait time for each service before triggering timeout
- **Outbox Event**: Pending event for asynchronous processing, ensures atomicity between business data and event publishing

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Customers receive order acknowledgment within 200 milliseconds of confirmation
- **SC-002**: System processes 100 concurrent orders per second without degradation
  - Measured using: Gatling load test with 100 virtual users, 60-second sustained load
  - Degradation threshold: p99 latency > 500ms OR error rate > 0.1%
  - Test environment: Single instance, H2 embedded database
- **SC-003**: Real-time notifications are delivered to customers within 1 second of each service completing
- **SC-004**: 100% of failed transactions are automatically compensated without manual intervention (excluding rollback failures)
- **SC-005**: System automatically recovers and resumes all incomplete transactions within 30 seconds of restart
- **SC-006**: Administrators are notified of rollback failures within 1 minute of occurrence
- **SC-007**: System maintains complete audit trail of all state transitions for 100% of transactions
- **SC-008**: Service order and timeout changes can be applied without system restart or downtime
- **SC-009**: System achieves 99.9% availability for order processing
  - Measurement window: Monthly (30 days)
  - Allowed downtime: ~43 minutes per month
  - Exclusions: Scheduled maintenance windows (must be announced 24h in advance)
- **SC-010**: Single order processing completes within 3 seconds (excluding downstream service response time)

## Assumptions

The following reasonable defaults have been applied based on the PRD and industry standards:

- **Service Timeout Defaults**: Payment 30 seconds, Inventory 60 seconds, Logistics 120 seconds (per PRD Section 8.4)
- **Retry Count**: Maximum 5 retries for rollback operations (per PRD Section 8.3)
- **Email Notification**: Will use simulated email during development phase with production-ready interface (per PRD Section 8.3)
- **Concurrent Processing**: Each order uses independent monitoring thread; no distributed locking required
- **Session Management**: Customer authentication is handled externally; this system receives already-authenticated requests
- **Order Validation**: Order content validation occurs before this system; this system receives valid order data

## Glossary

| Term | Definition |
|------|------------|
| TxID | Transaction Identifier - unique UUID for each order processing flow |
| Saga | Distributed transaction pattern using compensating actions instead of two-phase commit |
| Orchestration | Saga coordination style where central coordinator controls service execution order |
| Compensation | Rollback action to undo the effect of a previously successful service call |
| Outbox Pattern | Design pattern ensuring atomic write of business data and events |
| Event Sourcing | Pattern where state changes are stored as sequence of immutable events |
| Idempotent | Operation that produces same result regardless of how many times executed |
