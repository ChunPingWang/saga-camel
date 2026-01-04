# Monitoring 配置

本目錄包含監控相關的配置檔案。

## Grafana Dashboard

### 匯入 Dashboard

1. 開啟 Grafana (預設: http://localhost:3000)
2. 左側選單 → Dashboards → Import
3. 上傳 `grafana/dashboards/resilience4j-dashboard.json`
4. 選擇 Prometheus 資料來源
5. 點擊 Import

### Dashboard 內容

| Panel | 說明 |
|-------|------|
| **Circuit Breaker State** | 顯示各服務熔斷器狀態 (CLOSED/HALF_OPEN/OPEN) |
| **Circuit Breaker Failure Rate** | 失敗率趨勢圖 |
| **Circuit Breaker Calls Rate** | 呼叫速率 (成功/失敗/不允許) |
| **Retry Calls Rate** | 重試呼叫速率 |
| **Retry Events** | 重試事件統計 |
| **Bulkhead Gauges** | 各服務可用並發數 |
| **Saga Transaction Duration** | Saga 交易延遲 (p95/p99) |
| **Service Call Duration** | 各服務呼叫延遲 |

## Prometheus 指標

Order Service 透過 `/actuator/prometheus` 端點暴露指標。

### Resilience4j 指標

```promql
# Circuit Breaker 狀態 (0=CLOSED, 1=HALF_OPEN, 2=OPEN)
resilience4j_circuitbreaker_state{application="order-service", name="CREDIT_CARD"}

# Circuit Breaker 失敗率
resilience4j_circuitbreaker_failure_rate{application="order-service"}

# Circuit Breaker 呼叫統計
resilience4j_circuitbreaker_calls_total{application="order-service", kind="successful"}
resilience4j_circuitbreaker_calls_total{application="order-service", kind="failed"}
resilience4j_circuitbreaker_calls_total{application="order-service", kind="not_permitted"}

# Retry 呼叫統計
resilience4j_retry_calls_total{application="order-service", kind="successful_without_retry"}
resilience4j_retry_calls_total{application="order-service", kind="successful_with_retry"}
resilience4j_retry_calls_total{application="order-service", kind="failed_with_retry"}

# Bulkhead 可用並發數
resilience4j_bulkhead_available_concurrent_calls{application="order-service"}
resilience4j_bulkhead_max_allowed_concurrent_calls{application="order-service"}
```

### Saga 指標

```promql
# Saga 交易延遲
saga_transactions_duration_seconds_bucket{application="order-service"}
saga_transactions_duration_seconds_count{application="order-service"}

# 服務呼叫延遲
saga_service_duration_seconds_bucket{application="order-service", service="CREDIT_CARD"}
```

## 快速啟動 (Docker Compose)

```yaml
# docker-compose.monitoring.yml
version: '3.8'
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    volumes:
      - ./grafana/dashboards:/var/lib/grafana/dashboards
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
```

```yaml
# prometheus/prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'order-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
```
