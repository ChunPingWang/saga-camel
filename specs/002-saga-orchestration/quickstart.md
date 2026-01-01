# Quickstart: E-Commerce Saga Orchestration System

**Feature**: 002-saga-orchestration
**Date**: 2026-01-01

## Prerequisites

- Java 21 (JDK)
- Gradle 8.x (or use included wrapper)
- IDE with Lombok support (IntelliJ IDEA recommended)

## Project Setup

### 1. Clone and Build

```bash
# Clone repository
git clone <repository-url>
cd ecommerce-saga

# Build all modules
./gradlew clean build

# Run tests
./gradlew test
```

### 2. Start Services

Start all services in separate terminals:

```bash
# Terminal 1: Order Service (Orchestrator) - Port 8080
./gradlew :order-service:bootRun

# Terminal 2: Credit Card Service - Port 8081
./gradlew :credit-card-service:bootRun

# Terminal 3: Inventory Service - Port 8082
./gradlew :inventory-service:bootRun

# Terminal 4: Logistics Service - Port 8083
./gradlew :logistics-service:bootRun
```

### 3. Verify Services

```bash
# Check health endpoints
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## Quick API Test

### Confirm an Order

```bash
curl -X POST http://localhost:8080/api/v1/orders/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-20260101-001",
    "customerId": "CUST-001",
    "items": [
      {
        "productId": "IPHONE-15-PRO",
        "quantity": 1,
        "unitPrice": 36900
      }
    ],
    "totalAmount": 36900
  }'
```

**Expected Response** (202 Accepted):
```json
{
  "txId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": "ORD-20260101-001",
  "status": "PROCESSING",
  "message": "訂單已受理，處理中",
  "websocketUrl": "/ws/orders/550e8400-e29b-41d4-a716-446655440000"
}
```

### Query Transaction Status

```bash
# By TxID
curl "http://localhost:8080/api/v1/transactions?txId=550e8400-e29b-41d4-a716-446655440000"

# By Order ID
curl "http://localhost:8080/api/v1/transactions?orderId=ORD-20260101-001"
```

### WebSocket Connection (using websocat or browser)

```bash
# Using websocat
websocat ws://localhost:8080/ws/orders/550e8400-e29b-41d4-a716-446655440000
```

## Development Access

| Service | URL | Description |
|---------|-----|-------------|
| Order API | http://localhost:8080/swagger-ui.html | Swagger UI |
| H2 Console | http://localhost:8080/h2-console | Database UI |
| Metrics | http://localhost:8080/actuator/metrics | Prometheus metrics |
| Health | http://localhost:8080/actuator/health | Health check |

### H2 Console Access

- URL: `jdbc:h2:mem:orderdb`
- Username: `sa`
- Password: (empty)

## Admin API Examples

### View Service Configuration

```bash
# Get current service order
curl http://localhost:8080/api/v1/admin/saga/service-order

# Get timeout settings
curl http://localhost:8080/api/v1/admin/saga/timeout

# Get participating services
curl http://localhost:8080/api/v1/admin/saga/services
```

### Modify Service Order

```bash
# Update order (pending)
curl -X PUT http://localhost:8080/api/v1/admin/saga/service-order \
  -H "Content-Type: application/json" \
  -d '{
    "services": [
      {"name": "INVENTORY", "order": 1},
      {"name": "CREDIT_CARD", "order": 2},
      {"name": "LOGISTICS", "order": 3}
    ]
  }'

# Apply changes
curl -X POST http://localhost:8080/api/v1/admin/saga/service-order/apply
```

### Add New Service

```bash
# Add bonus point service (pending)
curl -X POST http://localhost:8080/api/v1/admin/saga/services \
  -H "Content-Type: application/json" \
  -d '{
    "name": "BONUS_POINT",
    "notifyUrl": "http://localhost:8084/api/v1/bonus-point/notify",
    "rollbackUrl": "http://localhost:8084/api/v1/bonus-point/rollback",
    "timeout": 30,
    "order": 2
  }'

# Apply changes
curl -X POST http://localhost:8080/api/v1/admin/saga/services/apply
```

## Testing Scenarios

### Happy Path (All Success)
All services configured to succeed → Transaction completes with all Success statuses.

### Rollback Scenario
Configure inventory service to fail:
```bash
# Set inventory to fail mode (service-specific test endpoint)
curl -X POST http://localhost:8082/test/fail-next
```
Submit order → Credit card succeeds, inventory fails, credit card rolls back.

### Timeout Scenario
Configure logistics to delay beyond timeout:
```bash
# Set logistics delay (service-specific test endpoint)
curl -X POST http://localhost:8083/test/delay?seconds=150
```
Submit order → Credit card and inventory succeed, logistics times out, both roll back.

### Circuit Breaker Test
Fail credit card service repeatedly:
```bash
# Fail credit card 10 times to open circuit breaker
for i in {1..10}; do
  curl -X POST http://localhost:8081/test/fail-next
  curl -X POST http://localhost:8080/api/v1/orders/confirm \
    -H "Content-Type: application/json" \
    -d '{"orderId": "ORD-TEST-'$i'", "customerId": "C1", "items": [], "totalAmount": 100}'
done

# Next order should fail immediately (circuit open)
curl -X POST http://localhost:8080/api/v1/orders/confirm \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-CB-TEST", "customerId": "C1", "items": [], "totalAmount": 100}'
```

## Project Structure

```
ecommerce-saga/
├── common/                 # Shared DTOs, enums
├── order-service/          # Saga Orchestrator
│   ├── domain/             # Business logic (pure)
│   ├── application/        # Use cases, ports
│   ├── adapter/            # Controllers, repositories
│   └── infrastructure/     # Camel routes, config
├── credit-card-service/    # Payment processing
├── inventory-service/      # Stock management
└── logistics-service/      # Shipping coordination
```

## Troubleshooting

### Service Won't Start
- Check port conflicts (8080-8083)
- Verify Java 21 is installed: `java -version`
- Check Gradle build succeeded: `./gradlew build`

### Transaction Stuck
- Check Checker Thread is running in logs
- Query transaction status via API
- Check H2 console for transaction_log entries

### Circuit Breaker Issues
- View circuit breaker state: `curl http://localhost:8080/actuator/metrics/circuitbreaker.state`
- Wait 30 seconds for half-open state
- Send successful requests to close breaker

## Next Steps

1. Read the [spec.md](./spec.md) for detailed requirements
2. Review [data-model.md](./data-model.md) for entity details
3. Check [contracts/](./contracts/) for API specifications
4. Run acceptance tests: `./gradlew :order-service:test --tests '*AcceptanceTest*'`
