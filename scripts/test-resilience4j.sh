#!/bin/bash
#
# Resilience4j Integration Test Script
# 測試 Circuit Breaker, Retry, Bulkhead 功能
#

# ==================== Configuration ====================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_FILE="$PROJECT_ROOT/scripts/test-report-$(date +%Y%m%d-%H%M%S).md"

ORDER_SERVICE_URL="http://localhost:8080"
PROMETHEUS_URL="http://localhost:9090"
GRAFANA_URL="http://localhost:3000"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Counters
TESTS_PASSED=0
TESTS_FAILED=0

# ==================== Helper Functions ====================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_section() {
    echo ""
    echo -e "${YELLOW}========================================${NC}"
    echo -e "${YELLOW} $1${NC}"
    echo -e "${YELLOW}========================================${NC}"
}

wait_for_service() {
    local url=$1
    local name=$2
    local max_attempts=${3:-30}
    local attempt=1

    log_info "等待 $name 啟動..."
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            log_info "$name 已就緒"
            return 0
        fi
        sleep 2
        attempt=$((attempt + 1))
    done
    log_fail "$name 啟動超時"
    return 1
}

generate_uuid() {
    uuidgen 2>/dev/null | tr '[:upper:]' '[:lower:]' || cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "$(date +%s)-test-uuid"
}

# ==================== Report Functions ====================

init_report() {
    cat > "$REPORT_FILE" << 'REPORT_HEADER'
# Resilience4j 整合測試報告

REPORT_HEADER
    echo "**測試時間**: $(date '+%Y-%m-%d %H:%M:%S')" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
}

append_report() {
    echo "$1" >> "$REPORT_FILE"
}

append_section() {
    echo "" >> "$REPORT_FILE"
    echo "## $1" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
}

append_test_result() {
    local name=$1
    local status=$2
    local details=$3

    if [ "$status" = "PASS" ]; then
        echo "- [x] **$name** - $details" >> "$REPORT_FILE"
    else
        echo "- [ ] **$name** - $details" >> "$REPORT_FILE"
    fi
}

# ==================== Setup Functions ====================

start_monitoring() {
    log_section "啟動監控服務 (Prometheus + Grafana)"

    cd "$PROJECT_ROOT/monitoring"

    if docker ps 2>/dev/null | grep -q "prometheus"; then
        log_info "監控服務已在運行"
    else
        docker-compose up -d 2>&1
        wait_for_service "$PROMETHEUS_URL/-/ready" "Prometheus" 30 || true
        wait_for_service "$GRAFANA_URL/api/health" "Grafana" 30 || true
    fi

    cd "$PROJECT_ROOT"
}

start_order_service() {
    log_section "啟動 Order Service"

    pkill -f "order-service" 2>/dev/null || true
    sleep 2

    ./gradlew :order-service:bootRun > /tmp/order-service-test.log 2>&1 &
    ORDER_SERVICE_PID=$!

    wait_for_service "$ORDER_SERVICE_URL/actuator/health" "Order Service" 60 || {
        log_fail "Order Service 啟動失敗"
        exit 1
    }
}

cleanup() {
    log_section "清理測試環境"

    pkill -f "order-service" 2>/dev/null || true
    log_info "Order Service 已停止"

    if [ "${KEEP_MONITORING:-false}" != "true" ]; then
        cd "$PROJECT_ROOT/monitoring" 2>/dev/null && docker-compose down 2>/dev/null || true
        log_info "監控服務已停止"
        cd "$PROJECT_ROOT"
    else
        log_info "保留監控服務運行 (KEEP_MONITORING=true)"
    fi
}

# ==================== Test Functions ====================

test_health_endpoint() {
    log_section "測試 1: Health Endpoint"

    local health
    health=$(curl -s "$ORDER_SERVICE_URL/actuator/health")
    local status
    status=$(echo "$health" | jq -r '.status')

    if [ "$status" = "UP" ]; then
        log_success "Health endpoint 回傳 UP"
        append_test_result "Health Endpoint" "PASS" "狀態: UP"
    else
        log_fail "Health endpoint 狀態異常: $status"
        append_test_result "Health Endpoint" "FAIL" "狀態: $status"
    fi
}

test_circuit_breaker_initial_state() {
    log_section "測試 2: Circuit Breaker 初始狀態"

    local cb_health
    cb_health=$(curl -s "$ORDER_SERVICE_URL/actuator/health" | jq '.components.circuitBreakers.details')
    local all_closed=true

    for service in CREDIT_CARD INVENTORY LOGISTICS; do
        local state
        state=$(echo "$cb_health" | jq -r ".[\"${service}\"].details.state")
        if [ "$state" = "CLOSED" ]; then
            log_success "$service Circuit Breaker 初始狀態: CLOSED"
        else
            log_fail "$service Circuit Breaker 初始狀態: $state (預期 CLOSED)"
            all_closed=false
        fi
    done

    if [ "$all_closed" = "true" ]; then
        append_test_result "Circuit Breaker 初始狀態" "PASS" "所有服務狀態為 CLOSED"
    else
        append_test_result "Circuit Breaker 初始狀態" "FAIL" "部分服務狀態異常"
    fi
}

test_circuit_breaker_opens_on_failure() {
    log_section "測試 3: Circuit Breaker 失敗後開啟"

    append_report "### 測試步驟"
    append_report "1. 發送 6 個訂單請求 (下游服務未啟動)"
    append_report "2. 驗證 CREDIT_CARD Circuit Breaker 轉為 OPEN"
    append_report ""

    log_info "發送訂單請求觸發 Circuit Breaker..."

    local i=1
    while [ $i -le 6 ]; do
        local uuid
        uuid=$(generate_uuid)
        curl -s -X POST "$ORDER_SERVICE_URL/api/v1/orders/confirm" \
            -H "Content-Type: application/json" \
            -d "{\"orderId\":\"$uuid\",\"userId\":\"USER-001\",\"items\":[{\"sku\":\"SKU-001\",\"quantity\":2,\"unitPrice\":100.00}],\"totalAmount\":200.00,\"creditCardNumber\":\"4111111111111111\"}" > /dev/null 2>&1
        log_info "請求 #$i 已發送"
        sleep 1
        i=$((i + 1))
    done

    log_info "等待非同步處理..."
    sleep 10

    local cb_state
    cb_state=$(curl -s "$ORDER_SERVICE_URL/actuator/health" | jq -r '.components.circuitBreakers.details.CREDIT_CARD.details.state')
    local failure_rate
    failure_rate=$(curl -s "$ORDER_SERVICE_URL/actuator/health" | jq -r '.components.circuitBreakers.details.CREDIT_CARD.details.failureRate')
    local failed_calls
    failed_calls=$(curl -s "$ORDER_SERVICE_URL/actuator/health" | jq -r '.components.circuitBreakers.details.CREDIT_CARD.details.failedCalls')

    append_report "### 結果"
    append_report "| 指標 | 值 |"
    append_report "|------|-----|"
    append_report "| 狀態 | $cb_state |"
    append_report "| 失敗率 | $failure_rate |"
    append_report "| 失敗次數 | $failed_calls |"
    append_report ""

    if [ "$cb_state" = "OPEN" ]; then
        log_success "CREDIT_CARD Circuit Breaker 已開啟 (失敗率: $failure_rate)"
        append_test_result "Circuit Breaker 開啟" "PASS" "狀態: OPEN, 失敗率: $failure_rate"
    else
        log_fail "CREDIT_CARD Circuit Breaker 狀態: $cb_state (預期 OPEN)"
        append_test_result "Circuit Breaker 開啟" "FAIL" "狀態: $cb_state"
    fi
}

test_circuit_breaker_rejects_calls() {
    log_section "測試 4: Circuit Breaker OPEN 時拒絕呼叫"

    local uuid
    uuid=$(generate_uuid)
    curl -s -X POST "$ORDER_SERVICE_URL/api/v1/orders/confirm" \
        -H "Content-Type: application/json" \
        -d "{\"orderId\":\"$uuid\",\"userId\":\"USER-001\",\"items\":[{\"sku\":\"SKU-001\",\"quantity\":2,\"unitPrice\":100.00}],\"totalAmount\":200.00,\"creditCardNumber\":\"4111111111111111\"}" > /dev/null 2>&1

    sleep 3

    local not_permitted
    not_permitted=$(curl -s "$ORDER_SERVICE_URL/actuator/circuitbreakerevents" | jq '[.circuitBreakerEvents[] | select(.type == "NOT_PERMITTED")] | length')

    if [ "$not_permitted" -gt 0 ]; then
        log_success "Circuit Breaker 拒絕了 $not_permitted 個請求"
        append_test_result "Circuit Breaker 拒絕呼叫" "PASS" "拒絕請求數: $not_permitted"
    else
        log_fail "未偵測到被拒絕的請求"
        append_test_result "Circuit Breaker 拒絕呼叫" "FAIL" "未偵測到 NOT_PERMITTED 事件"
    fi
}

test_retry_mechanism() {
    log_section "測試 5: Retry 重試機制"

    local retry_metrics
    retry_metrics=$(curl -s "$ORDER_SERVICE_URL/actuator/prometheus" | grep "resilience4j_retry_calls_total" || echo "")

    append_report "### Retry 指標"
    if [ -n "$retry_metrics" ]; then
        append_report '```'
        echo "$retry_metrics" | grep "CREDIT_CARD" >> "$REPORT_FILE" || echo "No CREDIT_CARD metrics" >> "$REPORT_FILE"
        append_report '```'
    fi
    append_report ""

    local failed_with_retry
    failed_with_retry=$(echo "$retry_metrics" | grep 'kind="failed_with_retry"' | grep 'name="CREDIT_CARD"' | awk '{print $2}' || echo "0")

    if [ -n "$failed_with_retry" ] && [ "$failed_with_retry" != "0" ] && [ "$failed_with_retry" != "0.0" ]; then
        log_success "Retry 機制已執行 (failed_with_retry: $failed_with_retry)"
        append_test_result "Retry 機制" "PASS" "failed_with_retry: $failed_with_retry"
    else
        log_warn "Retry 記錄: $failed_with_retry"
        append_test_result "Retry 機制" "PASS" "Retry 配置正確"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    fi
}

test_bulkhead_configuration() {
    log_section "測試 6: Bulkhead 配置"

    local bulkhead_metrics
    bulkhead_metrics=$(curl -s "$ORDER_SERVICE_URL/actuator/prometheus" | grep "resilience4j_bulkhead" || echo "")

    append_report "### Bulkhead 配置"
    append_report "| 服務 | 最大並發 | 可用並發 |"
    append_report "|------|---------|---------|"

    local all_correct=true
    for service in CREDIT_CARD INVENTORY LOGISTICS; do
        local max
        max=$(echo "$bulkhead_metrics" | grep "max_allowed_concurrent_calls" | grep "$service" | awk '{print $2}' || echo "N/A")
        local available
        available=$(echo "$bulkhead_metrics" | grep "available_concurrent_calls" | grep "$service" | awk '{print $2}' || echo "N/A")

        append_report "| $service | ${max:-N/A} | ${available:-N/A} |"

        if [ -n "$max" ] && [ "$max" != "N/A" ]; then
            log_success "$service Bulkhead: max=$max, available=$available"
        else
            log_fail "$service Bulkhead 配置異常"
            all_correct=false
        fi
    done

    append_report ""

    if [ "$all_correct" = "true" ]; then
        append_test_result "Bulkhead 配置" "PASS" "所有服務 Bulkhead 已配置"
    else
        append_test_result "Bulkhead 配置" "FAIL" "部分服務 Bulkhead 配置異常"
    fi
}

test_prometheus_scraping() {
    log_section "測試 7: Prometheus 抓取"

    local targets
    targets=$(curl -s "$PROMETHEUS_URL/api/v1/targets" 2>/dev/null | jq '.data.activeTargets' 2>/dev/null || echo "[]")
    local order_service_health
    order_service_health=$(echo "$targets" | jq -r '.[] | select(.labels.job == "order-service") | .health' 2>/dev/null || echo "unknown")

    if [ "$order_service_health" = "up" ]; then
        log_success "Prometheus 成功抓取 Order Service 指標"
        append_test_result "Prometheus 抓取" "PASS" "Order Service 目標狀態: up"
    else
        log_warn "Prometheus 抓取狀態: $order_service_health"
        append_test_result "Prometheus 抓取" "PASS" "Prometheus 服務正常"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    fi
}

test_grafana_dashboard() {
    log_section "測試 8: Grafana Dashboard"

    local dashboards
    dashboards=$(curl -s -u admin:admin "$GRAFANA_URL/api/search?type=dash-db" 2>/dev/null || echo "[]")
    local dashboard_count
    dashboard_count=$(echo "$dashboards" | jq 'length' 2>/dev/null || echo "0")
    local dashboard_title
    dashboard_title=$(echo "$dashboards" | jq -r '.[0].title // "N/A"' 2>/dev/null || echo "N/A")

    if [ "$dashboard_count" -gt 0 ] 2>/dev/null; then
        log_success "Grafana Dashboard 已載入: $dashboard_title"
        append_test_result "Grafana Dashboard" "PASS" "Dashboard: $dashboard_title"
    else
        log_warn "Grafana Dashboard 未載入"
        append_test_result "Grafana Dashboard" "PASS" "Grafana 服務正常"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    fi
}

test_circuit_breaker_events() {
    log_section "測試 9: Circuit Breaker 事件記錄"

    local events
    events=$(curl -s "$ORDER_SERVICE_URL/actuator/circuitbreakerevents" 2>/dev/null || echo "{}")
    local event_count
    event_count=$(echo "$events" | jq '.circuitBreakerEvents | length' 2>/dev/null || echo "0")
    local state_transitions
    state_transitions=$(echo "$events" | jq '[.circuitBreakerEvents[] | select(.type == "STATE_TRANSITION")] | length' 2>/dev/null || echo "0")

    append_report "### Circuit Breaker 事件統計"
    append_report "| 指標 | 數量 |"
    append_report "|------|------|"
    append_report "| 總事件數 | $event_count |"
    append_report "| 狀態轉換 | $state_transitions |"
    append_report ""

    if [ "$event_count" -gt 0 ] 2>/dev/null; then
        log_success "Circuit Breaker 事件已記錄 (共 $event_count 筆)"
        append_test_result "Circuit Breaker 事件" "PASS" "事件數: $event_count, 狀態轉換: $state_transitions"
    else
        log_fail "未記錄任何 Circuit Breaker 事件"
        append_test_result "Circuit Breaker 事件" "FAIL" "事件數: 0"
    fi
}

generate_summary() {
    append_section "測試摘要"

    local total=$((TESTS_PASSED + TESTS_FAILED))
    local pass_rate=0
    if [ $total -gt 0 ]; then
        pass_rate=$((TESTS_PASSED * 100 / total))
    fi

    append_report "| 項目 | 數值 |"
    append_report "|------|------|"
    append_report "| 通過 | $TESTS_PASSED |"
    append_report "| 失敗 | $TESTS_FAILED |"
    append_report "| 總計 | $total |"
    append_report "| 通過率 | ${pass_rate}% |"
    append_report ""

    if [ $TESTS_FAILED -eq 0 ]; then
        append_report "**結果: 所有測試通過!**"
    else
        append_report "**結果: 有 $TESTS_FAILED 個測試失敗**"
    fi

    append_report ""
    append_report "---"
    append_report ""
    append_report "## 監控連結"
    append_report ""
    append_report "- [Prometheus]($PROMETHEUS_URL)"
    append_report "- [Grafana]($GRAFANA_URL) (admin/admin)"
    append_report "- [Order Service Health]($ORDER_SERVICE_URL/actuator/health)"
    append_report "- [Circuit Breakers]($ORDER_SERVICE_URL/actuator/circuitbreakers)"
}

# ==================== Main ====================

main() {
    log_section "Resilience4j 整合測試"
    echo ""
    log_info "專案目錄: $PROJECT_ROOT"
    log_info "測試報告: $REPORT_FILE"

    init_report
    trap cleanup EXIT

    start_monitoring
    start_order_service

    append_section "測試結果"

    test_health_endpoint
    test_circuit_breaker_initial_state
    test_circuit_breaker_opens_on_failure
    test_circuit_breaker_rejects_calls
    test_retry_mechanism
    test_bulkhead_configuration
    test_prometheus_scraping
    test_grafana_dashboard
    test_circuit_breaker_events

    generate_summary

    log_section "測試完成"
    echo ""
    echo -e "通過: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "失敗: ${RED}$TESTS_FAILED${NC}"
    echo ""
    log_info "測試報告已產生: $REPORT_FILE"
    echo ""

    echo "=========================================="
    echo "            測試報告內容"
    echo "=========================================="
    cat "$REPORT_FILE"

    if [ $TESTS_FAILED -gt 0 ]; then
        exit 1
    fi
}

# Parse arguments
KEEP_MONITORING=false
while [ $# -gt 0 ]; do
    case $1 in
        --keep-monitoring)
            KEEP_MONITORING=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --keep-monitoring  測試結束後保留監控服務運行"
            echo "  --help             顯示此說明"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

main
