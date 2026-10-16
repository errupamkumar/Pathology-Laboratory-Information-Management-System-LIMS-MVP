-- ====================================================================
-- Pathology Laboratory Information Management System (LIMS) MVP
-- V1 Flyway Migration Script: Complete MySQL 8.0 Relational & JSON Schema
-- ====================================================================

-- 1. Patients Table
CREATE TABLE IF NOT EXISTS patients (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    uhid VARCHAR(64) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    dob DATE NOT NULL,
    gender VARCHAR(20) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(150),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT DEFAULT 0,
    INDEX idx_patients_phone (phone),
    INDEX idx_patients_uhid (uhid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Test Catalog Directory
CREATE TABLE IF NOT EXISTS test_catalogs (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    test_code VARCHAR(50) NOT NULL UNIQUE,
    test_name VARCHAR(200) NOT NULL,
    department VARCHAR(100) NOT NULL,
    specimen_type VARCHAR(100) NOT NULL,
    container_type VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    tat_minutes INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Test Parameters
CREATE TABLE IF NOT EXISTS test_parameters (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    test_catalog_id VARCHAR(36) NOT NULL,
    parameter_name VARCHAR(150) NOT NULL,
    unit VARCHAR(50),
    formula VARCHAR(255),
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT DEFAULT 0,
    INDEX idx_test_params_catalog (test_catalog_id),
    CONSTRAINT fk_test_parameters_catalog FOREIGN KEY (test_catalog_id) REFERENCES test_catalogs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Parameter Reference Ranges (Age & Gender Aware)
CREATE TABLE IF NOT EXISTS parameter_reference_ranges (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    parameter_id VARCHAR(36) NOT NULL,
    gender VARCHAR(20) NOT NULL,
    min_age INT NOT NULL DEFAULT 0,
    max_age INT NOT NULL DEFAULT 150,
    normal_low DECIMAL(10, 3),
    normal_high DECIMAL(10, 3),
    critical_low DECIMAL(10, 3),
    critical_high DECIMAL(10, 3),
    text_normal_val VARCHAR(255),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT DEFAULT 0,
    INDEX idx_ref_ranges_param (parameter_id),
    CONSTRAINT fk_param_ref_ranges_param FOREIGN KEY (parameter_id) REFERENCES test_parameters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Diagnostic Orders (Billing & Booking)
CREATE TABLE IF NOT EXISTS diagnostic_orders (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    accession_number VARCHAR(64) NOT NULL UNIQUE,
    patient_id VARCHAR(36) NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    paid_amount DECIMAL(10, 2) NOT NULL,
    referring_doctor VARCHAR(150),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT DEFAULT 0,
    INDEX idx_orders_patient (patient_id),
    INDEX idx_orders_accession (accession_number),
    INDEX idx_orders_status (status),
    CONSTRAINT fk_orders_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Order Items
CREATE TABLE IF NOT EXISTS order_items (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    test_catalog_id VARCHAR(36) NOT NULL,
    test_code VARCHAR(50) NOT NULL,
    test_name VARCHAR(200) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT DEFAULT 0,
    INDEX idx_order_items_order (order_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES diagnostic_orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Specimen Samples & Chain of Custody
CREATE TABLE IF NOT EXISTS specimen_samples (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    accession_number VARCHAR(64) NOT NULL,
    barcode VARCHAR(64) NOT NULL UNIQUE,
    container_type VARCHAR(100) NOT NULL,
    specimen_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    rejection_reason VARCHAR(100),
    rejection_notes TEXT,
    collected_at DATETIME(6),
    received_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT DEFAULT 0,
    INDEX idx_samples_barcode (barcode),
    INDEX idx_samples_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Test Results Grid & Panic Flagging
CREATE TABLE IF NOT EXISTS test_results (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    parameter_id VARCHAR(36) NOT NULL,
    parameter_name VARCHAR(150) NOT NULL,
    unit VARCHAR(50),
    observed_value VARCHAR(100) NOT NULL,
    is_critical BOOLEAN NOT NULL DEFAULT FALSE,
    is_out_of_range BOOLEAN NOT NULL DEFAULT FALSE,
    requires_review BOOLEAN NOT NULL DEFAULT FALSE,
    delta_change_percentage DECIMAL(8, 2),
    technician_notes TEXT,
    entered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    evaluated_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT DEFAULT 0,
    INDEX idx_test_results_order (order_id),
    INDEX idx_test_results_param (parameter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Clinical Doctor Authorization & Sign-Off
CREATE TABLE IF NOT EXISTS order_authorizations (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL UNIQUE,
    pathologist_name VARCHAR(150) NOT NULL,
    license_number VARCHAR(100) NOT NULL,
    digital_signature_hash VARCHAR(255) NOT NULL,
    verification_token VARCHAR(100) NOT NULL,
    authorized_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Immutable Audit Trail (JSON)
CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    entity_name VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    old_data JSON,
    new_data JSON,
    ip_address VARCHAR(50),
    timestamp DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_audit_entity (entity_name, entity_id),
    INDEX idx_audit_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
