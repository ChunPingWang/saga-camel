# Quickstart: E-Commerce Saga Orchestration System

**Date**: 2026-01-01
**Branch**: `001-saga-orchestration`

## Prerequisites

- Java 21+
- Gradle 8.x
- (Optional) WebSocket client for testing real-time notifications

## Project Setup

### 1. Clone and Build

```bash
# Navigate to project root
cd ecom-saga-poc

# Build all modules
./gradlew clean build

# Run tests
./gradlew test
```

### 2. Start Services

Start all four services in separate terminals:

```bash
# Terminal 1: Order Service (port 8080)
./gradlew :order-service:bootRun

# Terminal 2: Credit Card Service (port 8081)
./gradlew :credit-card-service:bootRun

# Terminal 3: Inventory Service (port 8082)
./gradlew :inventory-service:bootRun

# Terminal 4: Logistics Service (port 8083)
./gradlew :logistics-service:bootRun
```

### 3. Verify Services

```bash
# Check Order Service health
curl http://localhost:8080/actuator/health

# View Swagger UI
open http://localhost:8080/swagger-ui.html
```

## Quick Test: Happy Path

### 1. Confirm an Order

```bash
curl -X POST http://localhost:8080/api/v1/orders/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "550e8400-e29b-41d4-a716-446655440000",
    "customerId": "CUST-001",
    "items": [
      {
        "productId": "PROD-001",
        "productName": "Widget",
        "quantity": 2,
        "price": 29.99
      }
    ],
    "totalAmount": 59.98
  }'
```

**Expected Response (202 Accepted):**
```json
{
  "txId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACCEPTED",
  "message": "Order accepted for processing",
  "websocketUrl": "ws://localhost:8080/ws/orders/a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2026-01-01T10:00:00"
}
```

### 2. Query Transaction Status

```bash
curl http://localhost:8080/api/v1/transactions/{txId}
```

**Expected Response (all services succeeded):**
```json
{
  "txId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "currentStatus": "COMPLETED",
  "services": [
    {"serviceName": "CREDIT_CARD", "status": "S", "lastUpdated": "..."},
    {"serviceName": "INVENTORY", "status": "S", "lastUpdated": "..."},
    {"serviceName": "LOGISTICS", "status": "S", "lastUpdated": "..."}
  ]
}
```

### 3. Connect WebSocket for Real-time Updates

```javascript
// Browser console or Node.js
const ws = new WebSocket('ws://localhost:8080/ws/orders/{txId}');

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log('Status update:', message);
};
```

**Expected Messages:**
```json
{"txId":"...","status":"PROCESSING","currentStep":"CREDIT_CARD","message":"正在處理: CREDIT_CARD"}
{"txId":"...","status":"PROCESSING","currentStep":"CREDIT_CARD","message":"CREDIT_CARD 處理成功"}
{"txId":"...","status":"PROCESSING","currentStep":"INVENTORY","message":"正在處理: INVENTORY"}
{"txId":"...","status":"PROCESSING","currentStep":"INVENTORY","message":"INVENTORY 處理成功"}
{"txId":"...","status":"PROCESSING","currentStep":"LOGISTICS","message":"正在處理: LOGISTICS"}
{"txId":"...","status":"PROCESSING","currentStep":"LOGISTICS","message":"LOGISTICS 處理成功"}
{"txId":"...","status":"COMPLETED","currentStep":null,"message":"訂單交易完成"}
```

## Quick Test: Failure & Rollback

### Simulate Service Failure

The downstream services can be configured to fail for testing. Set environment variable:

```bash
# Make inventory service fail
SIMULATE_FAILURE=true ./gradlew :inventory-service:bootRun
```

### Observe Rollback

1. Submit an order
2. Watch WebSocket for rollback notifications
3. Query transaction status to see `R` (rolled back) statuses

**Expected WebSocket Messages:**
```json
{"txId":"...","status":"PROCESSING","currentStep":"CREDIT_CARD","message":"CREDIT_CARD 處理成功"}
{"txId":"...","status":"FAILED","currentStep":"INVENTORY","message":"服務呼叫失敗: Simulated failure"}
{"txId":"...","status":"ROLLING_BACK","currentStep":null,"message":"開始回滾"}
{"txId":"...","status":"ROLLING_BACK","currentStep":"CREDIT_CARD","message":"CREDIT_CARD 回滾成功"}
{"txId":"...","status":"ROLLED_BACK","currentStep":null,"message":"交易已回滾完成"}
```

## Admin API Quick Reference

### Query Service Order

```bash
curl http://localhost:8080/api/v1/admin/saga/service-order
```

### Update Service Order (Staged)

```bash
curl -X PUT http://localhost:8080/api/v1/admin/saga/service-order \
  -H "Content-Type: application/json" \
  -d '{
    "services": [
      {"order": 1, "name": "INVENTORY", "notifyUrl": "http://localhost:8082/api/v1/inventory/notify", "rollbackUrl": "http://localhost:8082/api/v1/inventory/rollback"},
      {"order": 2, "name": "CREDIT_CARD", "notifyUrl": "http://localhost:8081/api/v1/credit-card/notify", "rollbackUrl": "http://localhost:8081/api/v1/credit-card/rollback"},
      {"order": 3, "name": "LOGISTICS", "notifyUrl": "http://localhost:8083/api/v1/logistics/notify", "rollbackUrl": "http://localhost:8083/api/v1/logistics/rollback"}
    ]
  }'
```

### Apply Service Order

```bash
curl -X POST http://localhost:8080/api/v1/admin/saga/service-order/apply
```

### Update Timeout Configuration

```bash
curl -X PUT http://localhost:8080/api/v1/admin/saga/timeout \
  -H "Content-Type: application/json" \
  -d '{
    "timeouts": {
      "CREDIT_CARD": 15,
      "INVENTORY": 30,
      "LOGISTICS": 60
    }
  }'

curl -X POST http://localhost:8080/api/v1/admin/saga/timeout/apply
```

## Database Access (H2 Console)

Access the H2 console for each service:

| Service | URL | JDBC URL |
|---------|-----|----------|
| Order | http://localhost:8080/h2-console | `jdbc:h2:mem:orderdb` |
| Credit Card | http://localhost:8081/h2-console | `jdbc:h2:mem:creditcarddb` |
| Inventory | http://localhost:8082/h2-console | `jdbc:h2:mem:inventorydb` |
| Logistics | http://localhost:8083/h2-console | `jdbc:h2:mem:logisticsdb` |

**Credentials**: Username: `sa`, Password: (empty)

### Useful Queries

```sql
-- View all transaction logs
SELECT * FROM transaction_log ORDER BY created_at;

-- View latest status per service for a transaction
SELECT * FROM transaction_log tl
WHERE tx_id = 'your-tx-id'
AND created_at = (
  SELECT MAX(created_at) FROM transaction_log
  WHERE tx_id = tl.tx_id AND service_name = tl.service_name
);

-- View unprocessed outbox events
SELECT * FROM outbox_event WHERE processed = FALSE;
```

## Metrics & Monitoring

### Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus | grep saga
```

**Available Metrics:**
- `saga_started_total` - Total sagas initiated
- `saga_completed_total` - Successful completions
- `saga_failed_total` - Failed sagas
- `saga_rolledback_total` - Successful rollbacks
- `saga_rollback_failed_total` - Failed rollbacks
- `saga_duration_seconds` - Execution time histogram

## Troubleshooting

### Services Not Starting

1. Check if ports are in use: `lsof -i :8080`
2. Verify Java version: `java -version` (must be 21+)
3. Check Gradle version: `./gradlew --version` (must be 8.x)

### WebSocket Not Connecting

1. Ensure correct txId in URL path
2. Check browser console for CORS errors
3. Verify Order Service is running

### Rollback Not Triggering

1. Check checker thread logs: `grep "Checker thread" logs/order-service.log`
2. Verify timeout configuration: `curl http://localhost:8080/api/v1/admin/saga/timeout`
3. Check transaction status: `curl http://localhost:8080/api/v1/transactions/{txId}`

### Database Reset

```bash
# Restart the service to reset H2 in-memory database
./gradlew :order-service:bootRun --no-daemon
```
