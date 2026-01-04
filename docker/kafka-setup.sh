#!/bin/bash
#
# Kafka CDC Infrastructure Setup Script
# Usage: ./kafka-setup.sh [start|stop|status|deploy-connector|logs]
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.kafka.yml"
CONNECTOR_CONFIG="$SCRIPT_DIR/debezium/outbox-connector.json"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

wait_for_service() {
    local url=$1
    local name=$2
    local max_attempts=${3:-30}
    local attempt=1

    log_info "等待 $name 就緒..."
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            log_success "$name 已就緒"
            return 0
        fi
        sleep 2
        attempt=$((attempt + 1))
    done
    log_error "$name 啟動超時"
    return 1
}

start_kafka() {
    log_info "啟動 Kafka CDC 基礎設施..."

    docker-compose -f "$COMPOSE_FILE" up -d

    log_info "等待服務啟動..."
    wait_for_service "http://localhost:9092" "Kafka" 60 || true
    wait_for_service "http://localhost:8081/subjects" "Schema Registry" 30
    wait_for_service "http://localhost:8083/connectors" "Kafka Connect" 60
    wait_for_service "http://localhost:5432" "PostgreSQL" 30 || true

    log_success "Kafka CDC 基礎設施已啟動"
    echo ""
    echo "服務端點:"
    echo "  - Kafka:           localhost:29092"
    echo "  - Schema Registry: http://localhost:8081"
    echo "  - Kafka Connect:   http://localhost:8083"
    echo "  - Kafka UI:        http://localhost:8090"
    echo "  - PostgreSQL:      localhost:5432"
}

stop_kafka() {
    log_info "停止 Kafka CDC 基礎設施..."
    docker-compose -f "$COMPOSE_FILE" down
    log_success "Kafka CDC 基礎設施已停止"
}

status_kafka() {
    log_info "Kafka CDC 服務狀態:"
    echo ""
    docker-compose -f "$COMPOSE_FILE" ps
    echo ""

    # Check Kafka Connect connectors
    if curl -s http://localhost:8083/connectors > /dev/null 2>&1; then
        log_info "已部署的 Connectors:"
        curl -s http://localhost:8083/connectors | jq -r '.[]' 2>/dev/null || echo "  (無)"
    fi

    # Check Kafka topics
    echo ""
    log_info "Kafka Topics:"
    docker exec saga-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null || echo "  (Kafka 未運行)"
}

deploy_connector() {
    log_info "部署 Debezium Outbox Connector..."

    if ! curl -s http://localhost:8083/connectors > /dev/null 2>&1; then
        log_error "Kafka Connect 未運行，請先執行 ./kafka-setup.sh start"
        exit 1
    fi

    # Check if connector exists
    if curl -s http://localhost:8083/connectors/saga-outbox-connector > /dev/null 2>&1; then
        log_warn "Connector 已存在，刪除舊的..."
        curl -s -X DELETE http://localhost:8083/connectors/saga-outbox-connector
        sleep 2
    fi

    # Deploy connector
    log_info "建立 Connector..."
    response=$(curl -s -X POST -H "Content-Type: application/json" \
        --data @"$CONNECTOR_CONFIG" \
        http://localhost:8083/connectors)

    echo "$response" | jq . 2>/dev/null || echo "$response"

    # Wait and check status
    sleep 5
    log_info "Connector 狀態:"
    curl -s http://localhost:8083/connectors/saga-outbox-connector/status | jq . 2>/dev/null || log_error "無法取得狀態"

    log_success "Debezium Connector 已部署"
}

show_logs() {
    local service=${1:-""}
    if [ -n "$service" ]; then
        docker-compose -f "$COMPOSE_FILE" logs -f "$service"
    else
        docker-compose -f "$COMPOSE_FILE" logs -f
    fi
}

show_help() {
    echo "Kafka CDC Infrastructure Setup Script"
    echo ""
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  start             啟動 Kafka CDC 基礎設施"
    echo "  stop              停止所有服務"
    echo "  status            顯示服務狀態"
    echo "  deploy-connector  部署 Debezium Outbox Connector"
    echo "  logs [service]    顯示日誌 (可選服務: kafka, postgres, kafka-connect)"
    echo "  help              顯示此說明"
}

# Main
case "${1:-help}" in
    start)
        start_kafka
        ;;
    stop)
        stop_kafka
        ;;
    status)
        status_kafka
        ;;
    deploy-connector)
        deploy_connector
        ;;
    logs)
        show_logs "$2"
        ;;
    help|*)
        show_help
        ;;
esac
