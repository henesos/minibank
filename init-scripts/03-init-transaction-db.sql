-- Transaction Service Database Init Script
-- This script runs when the PostgreSQL container starts for the first time

-- Ensure the schema exists
CREATE SCHEMA IF NOT EXISTS public;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA public TO minibank;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO minibank;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO minibank;

-- Transactions table
-- Stores money transfer transactions with Saga state
CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id UUID NOT NULL UNIQUE,
    idempotency_key VARCHAR(100) UNIQUE,
    from_account_id UUID NOT NULL,
    to_account_id UUID NOT NULL,
    from_user_id UUID,
    to_user_id UUID,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    saga_step VARCHAR(30),
    description VARCHAR(255),
    failure_reason VARCHAR(500),
    retry_count INTEGER DEFAULT 0,
    processed_at TIMESTAMP,
    completed_at TIMESTAMP,
    version BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

-- Outbox table for reliable event publishing
CREATE TABLE IF NOT EXISTS outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id UUID NOT NULL,
    transaction_id UUID,
    event_type VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    error_message VARCHAR(500),
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for transactions
CREATE INDEX IF NOT EXISTS idx_transaction_from_account ON transactions(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transaction_to_account ON transactions(to_account_id);
CREATE INDEX IF NOT EXISTS idx_transaction_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transaction_saga_id ON transactions(saga_id);
CREATE INDEX IF NOT EXISTS idx_transaction_idempotency ON transactions(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_transaction_created_at ON transactions(created_at);

-- Create indexes for outbox
CREATE INDEX IF NOT EXISTS idx_outbox_saga_id ON outbox(saga_id);
CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox(status);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox(created_at);

-- Create update trigger for updated_at
CREATE OR REPLACE FUNCTION update_transaction_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_transactions_updated_at 
    BEFORE UPDATE ON transactions 
    FOR EACH ROW 
    EXECUTE FUNCTION update_transaction_updated_at();
