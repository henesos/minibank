-- ═══════════════════════════════════════════════════════════════════════════════
-- MiniBank Notification Service Database Initialization
-- ═══════════════════════════════════════════════════════════════════════════════

-- Create schema
CREATE SCHEMA IF NOT EXISTS notification;

-- Set search path
SET search_path TO notification;

-- ═══════════════════════════════════════════════════════════════════════════════
-- Notifications Table
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    subject VARCHAR(255),
    content TEXT NOT NULL,
    reference_id UUID,
    reference_type VARCHAR(50),
    recipient VARCHAR(255),
    idempotency_key VARCHAR(100) UNIQUE,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_message VARCHAR(1000),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    metadata TEXT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_notification_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_status ON notifications(status);
CREATE INDEX IF NOT EXISTS idx_notification_type ON notifications(type);
CREATE INDEX IF NOT EXISTS idx_notification_reference ON notifications(reference_id);
CREATE INDEX IF NOT EXISTS idx_notification_created_at ON notifications(created_at);
CREATE INDEX IF NOT EXISTS idx_notification_idempotency ON notifications(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_notification_user_read ON notifications(user_id, is_read);

-- ═══════════════════════════════════════════════════════════════════════════════
-- Notification Templates Table (for future use)
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    subject VARCHAR(255),
    content_template TEXT NOT NULL,
    variables JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- ═══════════════════════════════════════════════════════════════════════════════
-- Notification Preferences Table (for future use)
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    transaction_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    security_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    marketing_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- ═══════════════════════════════════════════════════════════════════════════════
-- Sample Templates
-- ═══════════════════════════════════════════════════════════════════════════════

INSERT INTO notification_templates (name, type, subject, content_template, variables) VALUES
('TRANSACTION_INITIATED', 'EMAIL', 'İşleminiz Başlatıldı - MiniBank', 
 'Sayın Müşterimiz,\n\n{{amount}} {{currency}} tutarındaki transfer işleminiz başlatılmıştır.\nİşlem tamamlandığında bilgilendirileceksiniz.\n\nSaygılarımızla,\nMiniBank',
 '["amount", "currency"]'),

('TRANSACTION_COMPLETED', 'EMAIL', 'İşleminiz Tamamlandı - MiniBank',
 'Sayın Müşterimiz,\n\n{{amount}} {{currency}} tutarındaki transfer işleminiz başarıyla tamamlanmıştır.\n\nSaygılarımızla,\nMiniBank',
 '["amount", "currency"]'),

('TRANSACTION_FAILED', 'EMAIL', 'İşlem Başarısız - MiniBank',
 'Sayın Müşterimiz,\n\n{{amount}} {{currency}} tutarındaki transfer işleminiz gerçekleştirilemedi.\nHata nedeni: {{failureReason}}\n\nSaygılarımızla,\nMiniBank',
 '["amount", "currency", "failureReason"]'),

('COMPENSATION_COMPLETED', 'EMAIL', 'İşlem İptal Edildi - MiniBank',
 'Sayın Müşterimiz,\n\n{{amount}} {{currency}} tutarındaki transfer işleminiz iptal edilmiş olup,\ntutar hesabınıza iade edilmiştir.\n\nSaygılarımızla,\nMiniBank',
 '["amount", "currency"]');

-- ═══════════════════════════════════════════════════════════════════════════════
-- Comments
-- ═══════════════════════════════════════════════════════════════════════════════

COMMENT ON TABLE notifications IS 'Stores all notifications sent to users';
COMMENT ON COLUMN notifications.type IS 'Notification channel: EMAIL, SMS, PUSH, IN_APP';
COMMENT ON COLUMN notifications.status IS 'Current status: PENDING, SENDING, SENT, DELIVERED, FAILED, CANCELLED';
COMMENT ON COLUMN notifications.reference_id IS 'Reference to related entity (transaction, account, etc.)';
COMMENT ON COLUMN notifications.idempotency_key IS 'Unique key for duplicate prevention';
