-- Account Service Database Init Script
-- This script runs when the PostgreSQL container starts for the first time

-- Ensure the schema exists
CREATE SCHEMA IF NOT EXISTS public;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA public TO minibank;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO minibank;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO minibank;

-- Accounts table (primary table for account service)
-- CRITICAL: Balance uses DECIMAL(19,4) for precision - no floating point errors
CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL,
    balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    available_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    name VARCHAR(100),
    description VARCHAR(255),
    version BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_available_balance_non_negative CHECK (available_balance >= 0),
    CONSTRAINT chk_available_balance_valid CHECK (available_balance <= balance)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_account_user_id ON accounts(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_account_number ON accounts(account_number);
CREATE INDEX IF NOT EXISTS idx_account_status ON accounts(status);
CREATE INDEX IF NOT EXISTS idx_account_created_at ON accounts(created_at);

-- Create update trigger for updated_at
CREATE OR REPLACE FUNCTION update_account_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_accounts_updated_at 
    BEFORE UPDATE ON accounts 
    FOR EACH ROW 
    EXECUTE FUNCTION update_account_updated_at();

-- Insert test account for test user
-- INSERT INTO accounts (user_id, account_number, account_type, balance, available_balance, status, name)
-- VALUES ('<user-uuid-from-user-service>', 'MB1234567890', 'SAVINGS', 1000.0000, 1000.0000, 'ACTIVE', 'Main Savings');
