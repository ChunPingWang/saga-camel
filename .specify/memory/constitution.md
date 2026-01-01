<!--
================================================================================
SYNC IMPACT REPORT
================================================================================
Version Change: N/A → 1.0.0 (Initial ratification)

Added Principles:
- I. Hexagonal Architecture (六角形架構)
- II. Domain-Driven Design (領域驅動設計)
- III. SOLID Principles (SOLID 原則)
- IV. Test-Driven Development (測試驅動開發)
- V. Behavior-Driven Development (行為驅動開發)
- VI. Code Quality Standards (程式碼品質標準)
- VII. Dependency Inversion (依賴反轉)
- VIII. Testing Standards (測試標準)

Added Sections:
- Architecture Constraints (架構約束)
- Development Workflow (開發流程)
- Governance (治理)

Removed Sections:
- None (initial constitution)

Templates Requiring Updates:
- .specify/templates/plan-template.md: ✅ Compatible (Constitution Check section exists)
- .specify/templates/spec-template.md: ✅ Compatible (BDD scenarios supported)
- .specify/templates/tasks-template.md: ✅ Compatible (TDD workflow supported)

Follow-up TODOs:
- None
================================================================================
-->

# E-Commerce Saga POC Constitution

## Core Principles

### I. Hexagonal Architecture (六角形架構)

All code MUST be organized following Hexagonal Architecture (Ports & Adapters):

- **Domain Layer (核心)**: Pure business logic with no external dependencies. Contains entities, value objects, domain services, and domain events. This layer MUST NOT import any framework code.
- **Application Layer (應用層)**: Use cases and application services. Defines ports (interfaces) for external dependencies. Orchestrates domain logic without containing business rules.
- **Adapter Layer (轉接層)**: Implements ports for external systems. Divided into:
  - **Inbound Adapters (驅動端)**: REST controllers, WebSocket handlers, CLI, message consumers
  - **Outbound Adapters (被驅動端)**: Database repositories, external service clients, message producers
- **Infrastructure Layer (基礎設施層)**: Framework configurations, cross-cutting concerns (logging, metrics, tracing)

**Dependency Rule**: Dependencies MUST only point inward. Domain layer has zero dependencies. Infrastructure MUST NOT be imported by inner layers.

### II. Domain-Driven Design (領域驅動設計)

All business logic MUST follow Domain-Driven Design principles:

- **Ubiquitous Language**: All code, documentation, and team communication MUST use consistent domain terminology as defined in the PRD glossary
- **Bounded Contexts**: Each microservice represents a distinct bounded context with clear boundaries
- **Aggregates**: Domain objects MUST be organized into aggregates with a single aggregate root controlling all access
- **Entities vs Value Objects**: Use entities for objects with identity; use value objects for descriptive objects without identity
- **Domain Events**: State changes MUST be captured as domain events (Event Sourcing pattern for transaction logs)
- **Repository Pattern**: Data access MUST be abstracted through repository interfaces defined in the application layer

### III. SOLID Principles (SOLID 原則)

All code MUST adhere to SOLID principles:

- **Single Responsibility (單一職責)**: Each class MUST have exactly one reason to change. Maximum 200 lines per class as a guideline.
- **Open/Closed (開放封閉)**: Classes MUST be open for extension but closed for modification. Use interfaces and abstract classes for extension points.
- **Liskov Substitution (里氏替換)**: Derived classes MUST be substitutable for their base types without altering program correctness.
- **Interface Segregation (介面隔離)**: Clients MUST NOT depend on interfaces they do not use. Prefer small, focused interfaces over large ones.
- **Dependency Inversion (依賴反轉)**: High-level modules MUST NOT depend on low-level modules. Both MUST depend on abstractions (ports/interfaces).

### IV. Test-Driven Development (測試驅動開發)

**NON-NEGOTIABLE**: All production code MUST be developed using TDD:

1. **Red**: Write a failing test FIRST that defines the desired behavior
2. **Green**: Write the MINIMUM code necessary to make the test pass
3. **Refactor**: Improve code quality while keeping tests green

**Enforcement**:
- No production code MUST be written without a corresponding failing test
- Code reviews MUST verify TDD compliance by checking test commit timestamps precede implementation commits
- Test coverage MUST be ≥80% for domain and application layers

### V. Behavior-Driven Development (行為驅動開發)

All features MUST be specified using BDD format:

- **User Stories**: Use "As a [role], When [action], I expect [outcome]" format
- **Acceptance Criteria**: All scenarios MUST use Gherkin syntax (Given-When-Then)
- **Living Documentation**: Acceptance tests serve as executable specifications
- **Scenario Coverage**: Each acceptance criterion in the PRD MUST have corresponding BDD tests

**BDD Test Structure**:
```
Scenario: [Descriptive scenario name]
  Given [initial context]
  And [additional context if needed]
  When [action is performed]
  Then [expected outcome]
  And [additional assertions if needed]
```

### VI. Code Quality Standards (程式碼品質標準)

All code MUST meet the following quality standards:

- **Naming Conventions**: Use clear, descriptive names. Class names are nouns; method names are verbs. No abbreviations except widely accepted ones (ID, URL, API).
- **Documentation**: Public APIs MUST have Javadoc/documentation. Complex algorithms MUST have explanatory comments.
- **Error Handling**: All exceptions MUST be handled appropriately. Use domain-specific exceptions for business rule violations.
- **Logging**: Use structured logging (JSON format) with appropriate log levels. Include correlation IDs (txId) for distributed tracing.
- **No Magic Values**: All constants MUST be named and documented. No hardcoded values in business logic.
- **Immutability**: Prefer immutable objects. Use final/val where possible. Collections should be unmodifiable when exposed.

### VII. Dependency Inversion & Port/Adapter Pattern (依賴反轉與端口適配器)

All external dependencies MUST be accessed through ports (interfaces):

**Ports (Interfaces)** - Defined in Application Layer:
- `TransactionLogPort`: Persistence of transaction state
- `OutboxPort`: Outbox pattern operations
- `ServiceClientPort`: External service communication
- `WebSocketPort`: Real-time client notifications
- `NotificationPort`: Email/alerting services

**Adapters (Implementations)** - Defined in Infrastructure Layer:
- Framework-specific implementations (Spring Data, RestTemplate, etc.)
- MUST be swappable without modifying domain/application code
- MUST support mock implementations for testing

**Inversion Rule**: Inner layers define WHAT is needed (ports); outer layers decide HOW to provide it (adapters).

### VIII. Testing Standards (測試標準)

All code MUST be tested at multiple levels:

| Test Type | Purpose | Location | Coverage Target |
|-----------|---------|----------|-----------------|
| Unit Tests | Test individual classes in isolation | `tests/unit/` | ≥80% domain/application |
| Integration Tests | Test adapter implementations | `tests/integration/` | All adapters |
| Contract Tests | Verify API contracts | `tests/contract/` | All public APIs |
| BDD/Acceptance Tests | Verify user scenarios | `tests/acceptance/` | All PRD scenarios |

**Test Isolation**:
- Unit tests MUST mock all dependencies
- Integration tests MUST use embedded databases (H2) or test containers
- Tests MUST be independent and idempotent
- Tests MUST NOT depend on execution order

## Architecture Constraints

### Layer Dependencies

```
┌─────────────────────────────────────────────────────────┐
│                    Infrastructure                        │
│   (Spring Boot, Camel, H2, WebSocket Config, Metrics)   │
└─────────────────────────────────────────────────────────┘
                           │ implements
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   Adapter (Outer Ring)                   │
│   ┌─────────────────┐         ┌─────────────────┐       │
│   │ Inbound Adapters│         │Outbound Adapters│       │
│   │ (Controllers,   │         │(Repositories,   │       │
│   │  WebSocket,     │         │ ServiceClients, │       │
│   │  Consumers)     │         │ Notifications)  │       │
│   └────────┬────────┘         └────────┬────────┘       │
└────────────┼────────────────────────────┼───────────────┘
             │ calls                      │ implements
             ▼                            │
┌─────────────────────────────────────────┼───────────────┐
│                 Application (Use Cases)  │               │
│   ┌─────────────────┐         ┌─────────┴─────────┐     │
│   │    Use Cases    │         │   Ports (out)     │     │
│   │ (OrderSagaService│        │ (TransactionLogPort│    │
│   │  RollbackService)│        │  OutboxPort, etc.)│     │
│   └────────┬────────┘         └───────────────────┘     │
└────────────┼────────────────────────────────────────────┘
             │ orchestrates
             ▼
┌─────────────────────────────────────────────────────────┐
│                      Domain (Core)                       │
│   ┌─────────────┐  ┌──────────────┐  ┌──────────────┐   │
│   │  Entities   │  │Value Objects │  │Domain Events │   │
│   │(Order,      │  │(TxId, Status)│  │(Transaction  │   │
│   │ Transaction)│  │              │  │ Event)       │   │
│   └─────────────┘  └──────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### Technology Constraints

| Constraint | Requirement | Rationale |
|------------|-------------|-----------|
| Frameworks in Infrastructure | Spring Boot, Camel, WebSocket configs MUST only appear in adapter/infrastructure layers | Keeps domain pure and testable |
| No Framework Annotations in Domain | Domain classes MUST NOT have Spring/JPA/Camel annotations | Domain should be framework-agnostic |
| Repository Interface Location | Repository interfaces MUST be in application layer, implementations in adapter layer | Dependency inversion |
| Event Sourcing for State | Transaction state changes MUST use INSERT (append-only), never UPDATE | Audit trail and recoverability |

### Package Structure Enforcement

```
com.ecommerce.[service]/
├── domain/           # MUST NOT import from adapter/infrastructure
│   ├── model/        # Entities, Value Objects
│   └── event/        # Domain Events
├── application/      # MUST NOT import from adapter/infrastructure
│   ├── port/
│   │   ├── in/       # Use case interfaces (driven by adapters)
│   │   └── out/      # Port interfaces (driving adapters)
│   └── service/      # Use case implementations
├── adapter/          # MAY import from application, MUST NOT import domain directly
│   ├── in/           # Inbound adapters (web, websocket, messaging)
│   └── out/          # Outbound adapters (persistence, clients)
└── infrastructure/   # Configuration, cross-cutting concerns only
```

## Development Workflow

### Pre-Commit Checklist

Before committing any code, developers MUST verify:

1. [ ] All new code has corresponding tests written FIRST (TDD)
2. [ ] Test coverage meets minimum thresholds (≥80% domain/application)
3. [ ] No framework imports in domain layer
4. [ ] All ports have corresponding adapter implementations
5. [ ] BDD scenarios cover PRD acceptance criteria
6. [ ] Code follows naming conventions and quality standards
7. [ ] Build passes: `./gradlew clean build`
8. [ ] No TODO/FIXME without associated issue tracking

### Code Review Criteria

Pull requests MUST be verified against:

1. **Architecture Compliance**: Hexagonal architecture layer rules followed
2. **TDD Evidence**: Test commits precede or accompany implementation commits
3. **SOLID Adherence**: No SOLID principle violations
4. **DDD Alignment**: Domain terminology matches ubiquitous language
5. **Test Coverage**: Coverage thresholds met for changed files
6. **BDD Completeness**: Acceptance criteria have executable tests

## Governance

### Constitutional Authority

This constitution supersedes all other development practices, coding standards, and technical decisions. Any conflict between this constitution and other guidelines MUST be resolved in favor of this constitution.

### Amendment Process

1. **Proposal**: Submit proposed changes with rationale and impact assessment
2. **Review**: Technical lead review with 48-hour comment period
3. **Approval**: Requires consensus from core team members
4. **Documentation**: Update constitution with version increment
5. **Migration**: If breaking, provide migration plan for existing code

### Version Policy

- **MAJOR**: Incompatible changes to principles (removal, redefinition)
- **MINOR**: New principles or substantial guidance additions
- **PATCH**: Clarifications, examples, typo fixes

### Compliance Review

- Weekly: Spot-check PRs for constitution compliance
- Sprint: Architecture review of new components
- Monthly: Full codebase audit against principles

**Version**: 1.0.0 | **Ratified**: 2026-01-01 | **Last Amended**: 2026-01-01
