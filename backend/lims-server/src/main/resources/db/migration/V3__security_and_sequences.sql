-- ====================================================================
-- V3 Flyway Migration: Security (users, roles) + Database Sequences
-- Industry-standard JWT RBAC authentication + persistent sequence counters
-- ====================================================================

-- 11. Application Users (Authentication)
CREATE TABLE IF NOT EXISTS app_users (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    last_login_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT DEFAULT 0,
    INDEX idx_users_username (username),
    INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. User Roles (Authorization)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. Persistent Database Sequences (replaces volatile AtomicLong)
CREATE TABLE IF NOT EXISTS db_sequences (
    sequence_name VARCHAR(100) NOT NULL PRIMARY KEY,
    current_value BIGINT NOT NULL DEFAULT 0,
    prefix VARCHAR(50) NOT NULL DEFAULT '',
    increment_by INT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed initial sequence values
INSERT INTO db_sequences (sequence_name, current_value, prefix, increment_by) VALUES
    ('UHID_SEQ', 1000, 'UHID', 1),
    ('ACCESSION_SEQ', 5000, 'ACC', 1)
ON DUPLICATE KEY UPDATE sequence_name = sequence_name;

-- 14. Seed default admin user (password: admin123 - BCrypt hash)
-- $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy = BCrypt("admin123")
INSERT INTO app_users (id, username, email, password_hash, full_name, is_enabled) VALUES
    (UUID(), 'admin', 'admin@apexpathlab.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'System Administrator', TRUE)
ON DUPLICATE KEY UPDATE username = username;

-- Assign ADMIN role to the default admin user
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ADMIN' FROM app_users WHERE username = 'admin'
ON DUPLICATE KEY UPDATE role = role;

-- Also give ADMIN all other roles for convenience
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_PATHOLOGIST' FROM app_users WHERE username = 'admin'
ON DUPLICATE KEY UPDATE role = role;

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_TECHNICIAN' FROM app_users WHERE username = 'admin'
ON DUPLICATE KEY UPDATE role = role;

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_RECEPTIONIST' FROM app_users WHERE username = 'admin'
ON DUPLICATE KEY UPDATE role = role;
