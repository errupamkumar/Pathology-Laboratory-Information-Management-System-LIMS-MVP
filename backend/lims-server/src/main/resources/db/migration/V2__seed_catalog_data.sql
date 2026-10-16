-- ====================================================================
-- Pathology Laboratory Information Management System (LIMS) MVP
-- V2 Flyway Migration Script: Medical Catalog Seed Data & Clinical Ranges
-- ====================================================================

-- 1. Complete Blood Count (CBC)
INSERT INTO test_catalogs (id, test_code, test_name, department, specimen_type, container_type, price, tat_minutes, is_active)
VALUES 
('a0000000-0000-0000-0000-000000000001', 'CBC', 'Complete Blood Count (5-Part Diff)', 'HEMATOLOGY', 'Whole Blood', 'EDTA (Lavender)', 450.00, 120, true),
('a0000000-0000-0000-0000-000000000002', 'LIPID', 'Lipid Profile Comprehensive', 'BIOCHEMISTRY', 'Serum', 'Plain (Red/Gold SST)', 850.00, 240, true),
('a0000000-0000-0000-0000-000000000003', 'LFT', 'Liver Function Test', 'BIOCHEMISTRY', 'Serum', 'Plain (Red/Gold SST)', 750.00, 180, true),
('a0000000-0000-0000-0000-000000000004', 'KFT', 'Kidney / Renal Function Test', 'BIOCHEMISTRY', 'Serum', 'Plain (Red/Gold SST)', 650.00, 180, true),
('a0000000-0000-0000-0000-000000000005', 'GLU_F', 'Glucose Fasting (Fluoride Plasma)', 'BIOCHEMISTRY', 'Plasma', 'Fluoride (Grey)', 120.00, 60, true);

-- Parameters for CBC
INSERT INTO test_parameters (id, test_catalog_id, parameter_name, unit, formula, display_order)
VALUES
('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 'Hemoglobin (Hb)', 'g/dL', NULL, 1),
('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001', 'Total Leucocyte Count (WBC)', '10^3/uL', NULL, 2),
('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001', 'Platelet Count', '10^3/uL', NULL, 3),
('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000001', 'RBC Count', '10^6/uL', NULL, 4),
('b0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000001', 'Hematocrit (PCV)', '%', NULL, 5),
('b0000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-000000000001', 'Neutrophils', '%', NULL, 6),
('b0000000-0000-0000-0000-000000000007', 'a0000000-0000-0000-0000-000000000001', 'Lymphocytes', '%', NULL, 7);

-- Parameters for Lipid Profile
INSERT INTO test_parameters (id, test_catalog_id, parameter_name, unit, formula, display_order)
VALUES
('b0000000-0000-0000-0000-000000000011', 'a0000000-0000-0000-0000-000000000002', 'Total Cholesterol', 'mg/dL', NULL, 1),
('b0000000-0000-0000-0000-000000000012', 'a0000000-0000-0000-0000-000000000002', 'HDL Cholesterol (Direct)', 'mg/dL', NULL, 2),
('b0000000-0000-0000-0000-000000000013', 'a0000000-0000-0000-0000-000000000002', 'LDL Cholesterol (Direct)', 'mg/dL', NULL, 3),
('b0000000-0000-0000-0000-000000000014', 'a0000000-0000-0000-0000-000000000002', 'Triglycerides', 'mg/dL', NULL, 4);

-- Parameters for KFT
INSERT INTO test_parameters (id, test_catalog_id, parameter_name, unit, formula, display_order)
VALUES
('b0000000-0000-0000-0000-000000000021', 'a0000000-0000-0000-0000-000000000004', 'Serum Creatinine', 'mg/dL', NULL, 1),
('b0000000-0000-0000-0000-000000000022', 'a0000000-0000-0000-0000-000000000004', 'Blood Urea Nitrogen', 'mg/dL', NULL, 2),
('b0000000-0000-0000-0000-000000000023', 'a0000000-0000-0000-0000-000000000004', 'Uric Acid', 'mg/dL', NULL, 3);

-- Parameters for Glucose Fasting
INSERT INTO test_parameters (id, test_catalog_id, parameter_name, unit, formula, display_order)
VALUES
('b0000000-0000-0000-0000-000000000031', 'a0000000-0000-0000-0000-000000000005', 'Fasting Blood Glucose', 'mg/dL', NULL, 1);

-- Reference Ranges (Adults & Gender-specific)
INSERT INTO parameter_reference_ranges (id, parameter_id, gender, min_age, max_age, normal_low, normal_high, critical_low, critical_high)
VALUES
('c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'MALE', 12, 120, 13.0, 17.5, 7.0, 20.0),
('c0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001', 'FEMALE', 12, 120, 12.0, 15.5, 7.0, 20.0),
('c0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000002', 'ALL', 0, 120, 4.0, 11.0, 2.0, 30.0),
('c0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000003', 'ALL', 0, 120, 150.0, 450.0, 40.0, 1000.0),
('c0000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000004', 'MALE', 12, 120, 4.5, 5.9, 2.5, 7.0),
('c0000000-0000-0000-0000-000000000006', 'b0000000-0000-0000-0000-000000000004', 'FEMALE', 12, 120, 4.0, 5.2, 2.5, 7.0),
('c0000000-0000-0000-0000-000000000007', 'b0000000-0000-0000-0000-000000000021', 'ALL', 0, 120, 0.7, 1.3, 0.3, 5.0),
('c0000000-0000-0000-0000-000000000008', 'b0000000-0000-0000-0000-000000000031', 'ALL', 0, 120, 70.0, 99.0, 50.0, 400.0),
('c0000000-0000-0000-0000-000000000009', 'b0000000-0000-0000-0000-000000000011', 'ALL', 0, 120, 120.0, 200.0, 50.0, 350.0);
