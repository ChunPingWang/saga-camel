-- Transaction Log - Immutable event sourcing table (append-only, no UPDATE)
CREATE TABLE IF NOT EXISTS transaction_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_id           VARCHAR(36) NOT NULL,
    order_id        VARCHAR(36) NOT NULL,
    service_name    VARCHAR(50) NOT NULL,
    status          VARCHAR(2) NOT NULL,
    error_message   VARCHAR(500),
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notified_at     TIMESTAMP,

    CONSTRAINT chk_status CHECK (status IN ('U', 'S', 'F', 'R', 'D', 'RF'))
);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_tx_service_status ON transaction_log (tx_id, service_name, status);
CREATE INDEX IF NOT EXISTS idx_status_created ON transaction_log (status, created_at);
CREATE INDEX IF NOT EXISTS idx_order_id ON transaction_log (order_id);

-- Outbox Event - For transactional outbox pattern (CDC compatible)
CREATE TABLE IF NOT EXISTS outbox_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregatetype   VARCHAR(100) NOT NULL,
    aggregateid     VARCHAR(36) NOT NULL,
    type            VARCHAR(100) NOT NULL,
    payload         CLOB NOT NULL,
    tx_id           VARCHAR(36) NOT NULL,
    order_id        VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed       BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at    TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_outbox_processed ON outbox_event (processed, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_event (aggregatetype, aggregateid);

-- Saga Configuration - Runtime configuration for service order and timeouts
CREATE TABLE IF NOT EXISTS saga_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_type     VARCHAR(50) NOT NULL,
    config_key      VARCHAR(100) NOT NULL,
    config_value    CLOB NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    is_pending      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_config_type_key_active ON saga_config (config_type, config_key, is_active);

-- Insert default service configurations (SERVICE_CONFIG type with individual service rows)
INSERT INTO saga_config (config_type, config_key, config_value, is_active, is_pending)
SELECT 'SERVICE_CONFIG', 'CREDIT_CARD', '{"order":1,"timeoutSeconds":30}', TRUE, FALSE
WHERE NOT EXISTS (SELECT 1 FROM saga_config WHERE config_type = 'SERVICE_CONFIG' AND config_key = 'CREDIT_CARD' AND is_active = TRUE);

INSERT INTO saga_config (config_type, config_key, config_value, is_active, is_pending)
SELECT 'SERVICE_CONFIG', 'INVENTORY', '{"order":2,"timeoutSeconds":60}', TRUE, FALSE
WHERE NOT EXISTS (SELECT 1 FROM saga_config WHERE config_type = 'SERVICE_CONFIG' AND config_key = 'INVENTORY' AND is_active = TRUE);

INSERT INTO saga_config (config_type, config_key, config_value, is_active, is_pending)
SELECT 'SERVICE_CONFIG', 'LOGISTICS', '{"order":3,"timeoutSeconds":120}', TRUE, FALSE
WHERE NOT EXISTS (SELECT 1 FROM saga_config WHERE config_type = 'SERVICE_CONFIG' AND config_key = 'LOGISTICS' AND is_active = TRUE);
