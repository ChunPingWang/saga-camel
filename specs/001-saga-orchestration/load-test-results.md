# Load Test Results

**Test Date**: [To be filled after running tests]
**Environment**: Local Development
**Target**: 100 orders/sec sustained load

## Test Scenarios

### 1. OrderLoadTest (Main Load Test)
- **Target**: 100 orders/sec for 2 minutes
- **Ramp-up**: 30 seconds from 1 to 100 users/sec
- **Duration**: 2 minutes sustained load

### 2. OrderSmokeTest (Quick Validation)
- **Purpose**: Validate endpoints before full load test
- **Users**: 1 user, single request

### 3. OrderStressTest (Breaking Point)
- **Purpose**: Find system breaking point
- **Load Pattern**: Incremental increase from 10 to 200+ users/sec

## Running the Tests

### Prerequisites
1. Start all services:
   ```bash
   # Start downstream services (in separate terminals)
   ./gradlew :credit-card-service:bootRun
   ./gradlew :inventory-service:bootRun
   ./gradlew :logistics-service:bootRun

   # Start order service
   ./gradlew :order-service:bootRun
   ```

2. Verify services are healthy:
   ```bash
   curl http://localhost:8080/actuator/health
   curl http://localhost:8081/actuator/health
   curl http://localhost:8082/actuator/health
   curl http://localhost:8083/actuator/health
   ```

### Run Smoke Test
```bash
./gradlew :order-service:gatlingRun-com.ecommerce.order.loadtest.OrderSmokeTest
```

### Run Main Load Test
```bash
./gradlew :order-service:gatlingRun-com.ecommerce.order.loadtest.OrderLoadTest
```

### Run Stress Test
```bash
./gradlew :order-service:gatlingRun-com.ecommerce.order.loadtest.OrderStressTest
```

### Run All Tests
```bash
./gradlew :order-service:gatlingRun
```

## Performance Assertions

| Metric | Target | Status |
|--------|--------|--------|
| Max Response Time | < 5000ms | TBD |
| 95th Percentile | < 2000ms | TBD |
| 99th Percentile (Confirm) | < 3000ms | TBD |
| 99th Percentile (Query) | < 1000ms | TBD |
| Success Rate | > 95% | TBD |

## Results

### Test Run: [Date TBD]

#### Summary
| Metric | Value |
|--------|-------|
| Total Requests | TBD |
| Successful Requests | TBD |
| Failed Requests | TBD |
| Mean Response Time | TBD |
| 95th Percentile | TBD |
| 99th Percentile | TBD |
| Requests/sec | TBD |

#### Response Time Distribution
```
[To be filled with actual test results]
```

#### Observations
1. [Add observations after running tests]

#### Recommendations
1. [Add recommendations based on test results]

## Reports

Gatling generates detailed HTML reports at:
```
order-service/build/reports/gatling/
```

Each test run creates a timestamped folder containing:
- `index.html` - Main report
- Response time distribution charts
- Request/response details
- Error analysis

## Notes

- Tests require all 4 services running locally
- For accurate results, run on a dedicated machine with consistent resources
- Consider running multiple iterations and averaging results
- Monitor system resources (CPU, memory, network) during tests
