-- V3__seed_settings.sql
-- Default settings row

INSERT INTO settings (id, distance_weight, readiness_weight, severity_match_weight, max_routing_distance_km)
VALUES (1, 0.2, 0.5, 0.3, 30.0)
ON CONFLICT (id) DO NOTHING;
