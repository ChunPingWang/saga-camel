-- PostgreSQL initialization script for Saga CDC
-- This script runs when the container is first created

-- Create extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Transaction log table (Event Sourcing - Append Only)
CREATE TABLE IF NOT EXISTS transaction_log (
    id              BIGSERIAL PRIMARY KEY,
    tx_id           VARCHAR(36) NOT NULL,
    order_id        VARCHAR(36) NOT NULL,
    service_name    VARCHAR(50) NOT NULL,
    status          VARCHAR(2) NOT NULL CHECK (status IN ('U', 'S', 'F', 'R', 'D', 'RF')),
    error_message   VARCHAR(500),
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notified_at     TIMESTAMPTZ
);

-- Indexes for transaction_log
CREATE INDEX IF NOT EXISTS idx_tx_service_status ON transaction_log (tx_id, service_name, status);
CREATE INDEX IF NOT EXISTS idx_status_created ON transaction_log (status, created_at);
CREATE INDEX IF NOT EXISTS idx_order_id ON transaction_log (order_id);

-- Outbox event table for CDC (Debezium Outbox Pattern)
CREATE TABLE IF NOT EXISTS outbox_event (
    id              BIGSERIAL PRIMARY KEY,
    aggregatetype   VARCHAR(100) NOT NULL,      -- For Debezium routing (e.g., 'Order')
    aggregateid     VARCHAR(36) NOT NULL,       -- For Debezium routing (orderId)
    type            VARCHAR(100) NOT NULL,      -- Event type (e.g., 'ORDER_CONFIRMED')
    payload         JSONB NOT NULL,             -- Event payload as JSONB
    tx_id           VARCHAR(36) NOT NULL,
    order_id        VARCHAR(36) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed       BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at    TIMESTAMPTZ
);

-- Index for outbox polling (fallback)
CREATE INDEX IF NOT EXISTS idx_outbox_processed ON outbox_event (processed, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_event (aggregatetype, aggregateid);

-- Saga state table (for async saga coordination)
CREATE TABLE IF NOT EXISTS saga_state (
    id              BIGSERIAL PRIMARY KEY,
    tx_id           VARCHAR(36) NOT NULL UNIQUE,
    order_id        VARCHAR(36) NOT NULL,
    current_step    VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    completed_steps JSONB DEFAULT '[]'::JSONB,
    service_refs    JSONB DEFAULT '{}'::JSONB,
    last_error      TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_saga_state_status ON saga_state (status, updated_at);
CREATE INDEX IF NOT EXISTS idx_saga_state_order ON saga_state (order_id);

-- Saga config table
CREATE TABLE IF NOT EXISTS saga_config (
    id              BIGSERIAL PRIMARY KEY,
    config_type     VARCHAR(50) NOT NULL,
    config_key      VARCHAR(100) NOT NULL,
    config_value    JSONB NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    is_pending      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_config_type_key_active ON saga_config (config_type, config_key, is_active);

-- Insert default saga configuration
INSERT INTO saga_config (config_type, config_key, config_value, is_active) VALUES
    ('SERVICE_CONFIG', 'CREDIT_CARD', '{"order": 1, "timeoutSeconds": 30}'::JSONB, true),
    ('SERVICE_CONFIG', 'INVENTORY', '{"order": 2, "timeoutSeconds": 60}'::JSONB, true),
    ('SERVICE_CONFIG', 'LOGISTICS', '{"order": 3, "timeoutSeconds": 120}'::JSONB, true)
ON CONFLICT DO NOTHING;

-- Create publication for Debezium CDC
-- This allows Debezium to capture changes from the outbox_event table
CREATE PUBLICATION dbz_publication FOR TABLE outbox_event;

-- Grant replication permissions
ALTER USER saga WITH REPLICATION;

-- Create replication slot for Debezium (will be created by Debezium connector)
-- SELECT pg_create_logical_replication_slot('saga_outbox_slot', 'pgoutput');

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger for saga_state updated_at
CREATE TRIGGER update_saga_state_updated_at
    BEFORE UPDATE ON saga_state
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger for saga_config updated_at
CREATE TRIGGER update_saga_config_updated_at
    BEFORE UPDATE ON saga_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Print completion message
DO $$
BEGIN
    RAISE NOTICE 'PostgreSQL initialization completed for Saga CDC';
END $$;
