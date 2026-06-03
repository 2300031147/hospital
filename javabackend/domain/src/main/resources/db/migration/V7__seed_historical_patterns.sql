-- V7__seed_historical_patterns.sql
-- Seed 168 historical pattern slots per hospital (7 days x 24 hours)
-- Matches Python seed_data logic: base_load=0.6, +0.2 for hours 18-23, +0.1 for weekends

INSERT INTO historical_patterns (hospital_id, day_of_week, hour_of_day, avg_load, avg_turnover_rate)
SELECT h.id, d.day_of_week, h2.hour_of_day,
       LEAST(
           0.6
           + CASE WHEN h2.hour_of_day BETWEEN 18 AND 23 THEN 0.2 ELSE 0.0 END
           + CASE WHEN d.day_of_week >= 5 THEN 0.1 ELSE 0.0 END,
           1.0
       ) AS avg_load,
       0.05 AS avg_turnover_rate
FROM hospitals h
CROSS JOIN (SELECT generate_series(0, 6) AS day_of_week) d
CROSS JOIN (SELECT generate_series(0, 23) AS hour_of_day) h2
ON CONFLICT (hospital_id, day_of_week, hour_of_day) DO NOTHING;
