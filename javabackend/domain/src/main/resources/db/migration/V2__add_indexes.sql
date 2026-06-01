-- V2__add_indexes.sql
-- Performance indexes for AEROVHYN

CREATE INDEX idx_hospitals_status ON hospitals(status);
CREATE INDEX idx_ambulances_status ON ambulances(status);
CREATE INDEX idx_ambulances_destination ON ambulances(destination_hospital_id);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_logs_event_type ON logs(event_type);
CREATE INDEX idx_logs_timestamp ON logs(timestamp);
CREATE INDEX idx_logs_ambulance ON logs(ambulance_id);
CREATE INDEX idx_logs_hospital ON logs(hospital_selected_id);
CREATE INDEX idx_blockchain_idx ON blockchain(idx);
CREATE INDEX idx_historical_patterns_hospital ON historical_patterns(hospital_id);
CREATE INDEX idx_historical_patterns_lookup ON historical_patterns(hospital_id, day_of_week, hour_of_day);
