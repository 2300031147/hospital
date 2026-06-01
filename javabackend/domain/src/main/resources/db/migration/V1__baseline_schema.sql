-- V1__baseline_schema.sql
-- AEROVHYN Database Baseline Schema (PostgreSQL)
-- Translated from Alembic migration fad811a338cf

CREATE TABLE hospitals (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,
    icu_beds INTEGER DEFAULT 0,
    total_icu_beds INTEGER DEFAULT 10,
    soft_reserve INTEGER DEFAULT 0,
    ventilators INTEGER DEFAULT 0,
    total_ventilators INTEGER DEFAULT 5,
    specialists TEXT DEFAULT '[]',
    current_load INTEGER DEFAULT 0,
    max_capacity INTEGER DEFAULT 100,
    equipment_score DOUBLE PRECISION DEFAULT 0.8,
    status VARCHAR(255) DEFAULT 'active',
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ambulances (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) DEFAULT 'AMB-001',
    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,
    patient_severity VARCHAR(255) DEFAULT 'unknown',
    destination_hospital_id BIGINT REFERENCES hospitals(id) ON DELETE SET NULL,
    emergency_type VARCHAR(255),
    status VARCHAR(255) DEFAULT 'idle',
    patient_vitals TEXT DEFAULT '{}',
    eta_minutes DOUBLE PRECISION DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL DEFAULT 'paramedic',
    ambulance_id BIGINT REFERENCES ambulances(id) ON DELETE SET NULL,
    hospital_id BIGINT REFERENCES hospitals(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(255) NOT NULL,
    ambulance_id BIGINT REFERENCES ambulances(id) ON DELETE SET NULL,
    hospital_selected_id BIGINT REFERENCES hospitals(id) ON DELETE SET NULL,
    score DOUBLE PRECISION,
    details TEXT DEFAULT ''
);

CREATE TABLE blockchain (
    idx BIGINT PRIMARY KEY,
    timestamp VARCHAR(255) NOT NULL,
    data TEXT NOT NULL,
    prev_hash VARCHAR(255) NOT NULL,
    hash VARCHAR(255) NOT NULL,
    nonce INTEGER DEFAULT 0
);

CREATE TABLE historical_patterns (
    id BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    day_of_week INTEGER NOT NULL,
    hour_of_day INTEGER NOT NULL,
    avg_load DOUBLE PRECISION NOT NULL,
    avg_turnover_rate DOUBLE PRECISION DEFAULT 0.05,
    CONSTRAINT uq_pattern UNIQUE (hospital_id, day_of_week, hour_of_day)
);

CREATE TABLE settings (
    id BIGINT PRIMARY KEY,
    distance_weight DOUBLE PRECISION DEFAULT 0.2,
    readiness_weight DOUBLE PRECISION DEFAULT 0.5,
    severity_match_weight DOUBLE PRECISION DEFAULT 0.3,
    max_routing_distance_km DOUBLE PRECISION DEFAULT 30.0
);
