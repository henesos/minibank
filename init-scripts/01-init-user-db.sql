-- User Service Database Init Script
-- This script runs when the PostgreSQL container starts for the first time

-- Ensure the schema exists
CREATE SCHEMA IF NOT EXISTS public;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA public TO minibank;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO minibank;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO minibank;

-- Users table (primary table for user service)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20) UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    national_id VARCHAR(20) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    last_login_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_user_phone ON users(phone);
CREATE INDEX IF NOT EXISTS idx_user_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_user_created_at ON users(created_at);

-- Create update trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Insert test user (password: Test123!)
-- INSERT INTO users (email, password_hash, first_name, last_name, status, email_verified)
-- VALUES ('test@minibank.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4wqO.6mYTrXr6Y.S', 'Test', 'User', 'ACTIVE', true);
