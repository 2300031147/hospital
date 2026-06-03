-- V5__seed_ambulances.sql
-- Seed 3 ambulances (matches Python seed_data)

INSERT INTO ambulances (name, lat, lon, patient_severity, emergency_type, status, patient_vitals, eta_minutes)
VALUES
    ('AMB-001', 17.4239, 78.4483, 'unknown', NULL, 'idle', '{}', 0.0),
    ('AMB-002', 17.4156, 78.4347, 'unknown', NULL, 'idle', '{}', 0.0),
    ('AMB-003', 17.4401, 78.4983, 'unknown', NULL, 'idle', '{}', 0.0);
