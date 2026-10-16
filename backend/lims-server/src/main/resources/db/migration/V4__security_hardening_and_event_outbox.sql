-- ====================================================================
-- V4 Flyway Migration: auth hardening, signer identity, transactional
-- outbox for domain events, and indexes for paged worklists.
-- (V3 is left untouched so existing databases keep a valid checksum.)
-- ====================================================================

-- 1. Repair the seeded admin credential. The hash shipped in V3 does not match the documented
--    password 'admin123', so nobody could sign in. Only that exact broken hash is replaced;
--    a password that was already changed is left alone.
UPDATE app_users
SET password_hash = '$2a$10$4he.zgyyTO/jdGJSt5ouAOU1GcfieRpdJ7CAuCngSpm73M.YBb7d.'
WHERE username = 'admin'
  AND password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';

-- 2. Time-boxed brute-force lockout (is_account_locked + locked_until)
ALTER TABLE app_users ADD COLUMN locked_until DATETIME(6) NULL;

-- 3. Bind each report sign-off to the authenticated account that performed it
ALTER TABLE order_authorizations ADD COLUMN authorized_by VARCHAR(100) NULL;

-- 4. Spring Modulith event publication registry (transactional outbox).
--    Rows are written in the publishing transaction and completed when the listener succeeds.
CREATE TABLE IF NOT EXISTS event_publication (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event VARCHAR(12000) NOT NULL,
    publication_date DATETIME(6) NOT NULL,
    completion_date DATETIME(6) NULL,
    INDEX idx_event_publication_completion (completion_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Indexes backing the paged / sorted list endpoints and audit review
CREATE INDEX idx_samples_status ON specimen_samples (status);
CREATE INDEX idx_samples_created_at ON specimen_samples (created_at);
CREATE INDEX idx_orders_created_at ON diagnostic_orders (created_at);
CREATE INDEX idx_orders_patient_created ON diagnostic_orders (patient_id, created_at);
CREATE INDEX idx_audit_changed_by ON audit_logs (changed_by);
