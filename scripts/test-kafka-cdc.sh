#!/bin/bash
#
# Kafka CDC Integration Test Script
# Tests the full Saga flow using Kafka messaging
#
# Prerequisites:
# - Docker and Docker Compose
# - Kafka infrastructure running (docker/kafka-setup.sh start)
# - All services running with --spring.profiles.active=kafka
#
# Usage: ./test-kafka-cdc.sh [scenario]
#   scenarios: happy-path, failure, rollback, all

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
ORDER_SERVICE_URL="${ORDER_SERVICE_URL:-http://localhost:8080}"
KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:29092}"

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "檢查前置條件..."

    # Check if Order Service is running
    if ! curl -s "${ORDER_SERVICE_URL}/actuator/health" > /dev/null 2>&1; then
        log_error "Order Service 未運行於 ${ORDER_SERVICE_URL}"
        exit 1
    fi
    log_success "Order Service 運行中"

    # Check if Kafka is reachable
    if ! docker exec saga-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
        log_warn "無法連接 Kafka (容器可能不存在)"
    else
        log_success "Kafka 運行中"
    fi
}

# Generate order payload
generate_order_payload() {
    local order_id="${1:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
    cat <<EOF
{
    "orderId": "${order_id}",
    "userId": "user-$(date +%s)",
    "items": [
        {
            "sku": "PROD-001",
            "quantity": 2,
            "unitPrice": 100.00
        },
        {
            "sku": "PROD-002",
            "quantity": 1,
            "unitPrice": 50.00
        }
    ],
    "totalAmount": 250.00,
    "creditCardNumber": "4111111111111111"
}
EOF
}

# Test: Happy Path - All services succeed
test_happy_path() {
    log_info "=== 測試場景: Happy Path (全部成功) ==="

    local order_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
    local payload=$(generate_order_payload "$order_id")

    log_info "發送訂單確認請求: orderId=${order_id}"

    response=$(curl -s -X POST "${ORDER_SERVICE_URL}/api/v1/orders/confirm" \
        -H "Content-Type: application/json" \
        -d "$payload")

    tx_id=$(echo "$response" | grep -o '"txId":"[^"]*"' | cut -d'"' -f4)

    if [ -z "$tx_id" ]; then
        log_error "無法取得 txId"
        echo "Response: $response"
        return 1
    fi

    log_info "取得 txId: ${tx_id}"

    # Wait and check status
    log_info "等待 Saga 處理完成..."
    sleep 5

    status_response=$(curl -s "${ORDER_SERVICE_URL}/api/v1/orders/transaction/${tx_id}")
    overall_status=$(echo "$status_response" | grep -o '"overallStatus":"[^"]*"' | cut -d'"' -f4)

    log_info "交易狀態: ${overall_status}"

    if [ "$overall_status" = "COMPLETED" ] || [ "$overall_status" = "PROCESSING" ]; then
        log_success "Happy Path 測試通過"
        return 0
    else
        log_warn "交易狀態非預期: ${overall_status}"
        echo "Status Response: $status_response"
        return 1
    fi
}

# Test: Service Failure - Trigger rollback
test_failure_rollback() {
    log_info "=== 測試場景: Service Failure (觸發回滾) ==="

    # Enable failure simulation on Inventory service
    log_info "啟用 Inventory 服務失敗模擬..."
    curl -s -X POST "http://localhost:8082/api/v1/inventory/simulate/failure?enabled=true&rate=1.0" > /dev/null 2>&1 || true

    local order_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
    local payload=$(generate_order_payload "$order_id")

    log_info "發送訂單確認請求: orderId=${order_id}"

    response=$(curl -s -X POST "${ORDER_SERVICE_URL}/api/v1/orders/confirm" \
        -H "Content-Type: application/json" \
        -d "$payload")

    tx_id=$(echo "$response" | grep -o '"txId":"[^"]*"' | cut -d'"' -f4)

    if [ -z "$tx_id" ]; then
        log_error "無法取得 txId"
        return 1
    fi

    log_info "取得 txId: ${tx_id}"

    # Wait for rollback
    log_info "等待回滾處理..."
    sleep 8

    status_response=$(curl -s "${ORDER_SERVICE_URL}/api/v1/orders/transaction/${tx_id}")
    overall_status=$(echo "$status_response" | grep -o '"overallStatus":"[^"]*"' | cut -d'"' -f4)

    log_info "交易狀態: ${overall_status}"

    # Disable failure simulation
    curl -s -X POST "http://localhost:8082/api/v1/inventory/simulate/failure?enabled=false" > /dev/null 2>&1 || true

    if [ "$overall_status" = "ROLLED_BACK" ] || [ "$overall_status" = "FAILED" ] || [ "$overall_status" = "ROLLING_BACK" ]; then
        log_success "Failure Rollback 測試通過"
        return 0
    else
        log_warn "回滾狀態非預期: ${overall_status}"
        return 1
    fi
}

# Test: Check Kafka Topics
test_kafka_topics() {
    log_info "=== 測試場景: Kafka Topics 檢查 ==="

    expected_topics=(
        "saga.credit-card.commands"
        "saga.credit-card.responses"
        "saga.inventory.commands"
        "saga.inventory.responses"
        "saga.logistics.commands"
        "saga.logistics.responses"
    )

    existing_topics=$(docker exec saga-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")

    all_found=true
    for topic in "${expected_topics[@]}"; do
        if echo "$existing_topics" | grep -q "$topic"; then
            log_success "Topic 存在: $topic"
        else
            log_warn "Topic 不存在: $topic"
            all_found=false
        fi
    done

    if $all_found; then
        log_success "Kafka Topics 檢查通過"
        return 0
    else
        log_warn "部分 Topic 不存在"
        return 1
    fi
}

# Test: Consumer Groups
test_consumer_groups() {
    log_info "=== 測試場景: Consumer Groups 檢查 ==="

    groups=$(docker exec saga-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")

    log_info "現有 Consumer Groups:"
    echo "$groups" | while read -r group; do
        if [ -n "$group" ]; then
            echo "  - $group"
        fi
    done

    log_success "Consumer Groups 檢查完成"
}

# Test: Message Flow
test_message_flow() {
    log_info "=== 測試場景: Message Flow 檢查 ==="

    # Check if there are messages in response topics
    for service in "credit-card" "inventory" "logistics"; do
        topic="saga.${service}.responses"
        count=$(docker exec saga-kafka kafka-run-class kafka.tools.GetOffsetShell \
            --broker-list localhost:9092 \
            --topic "$topic" 2>/dev/null | awk -F: '{sum += $3} END {print sum}' || echo "0")

        log_info "Topic ${topic}: ${count:-0} messages"
    done

    log_success "Message Flow 檢查完成"
}

# Run all tests
run_all_tests() {
    local passed=0
    local failed=0

    check_prerequisites || exit 1

    echo ""
    log_info "開始執行 Kafka CDC 整合測試..."
    echo ""

    # Run tests
    test_kafka_topics && ((passed++)) || ((failed++))
    echo ""

    test_consumer_groups && ((passed++)) || ((failed++))
    echo ""

    test_happy_path && ((passed++)) || ((failed++))
    echo ""

    test_message_flow && ((passed++)) || ((failed++))
    echo ""

    # Summary
    echo ""
    log_info "========== 測試結果 =========="
    log_success "通過: ${passed}"
    if [ $failed -gt 0 ]; then
        log_error "失敗: ${failed}"
    fi
    echo ""

    return $failed
}

# Main
case "${1:-all}" in
    happy-path)
        check_prerequisites
        test_happy_path
        ;;
    failure)
        check_prerequisites
        test_failure_rollback
        ;;
    rollback)
        check_prerequisites
        test_failure_rollback
        ;;
    topics)
        test_kafka_topics
        ;;
    groups)
        test_consumer_groups
        ;;
    flow)
        test_message_flow
        ;;
    all)
        run_all_tests
        ;;
    help|*)
        echo "Kafka CDC Integration Test Script"
        echo ""
        echo "Usage: $0 [scenario]"
        echo ""
        echo "Scenarios:"
        echo "  happy-path  - Test successful order flow"
        echo "  failure     - Test service failure and rollback"
        echo "  rollback    - Same as failure"
        echo "  topics      - Check Kafka topics"
        echo "  groups      - Check consumer groups"
        echo "  flow        - Check message flow"
        echo "  all         - Run all tests (default)"
        echo "  help        - Show this help"
        ;;
esac
