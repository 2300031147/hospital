-- V4__seed_hospitals.sql
-- Seed 8 Hyderabad-inspired hospitals (matches Python seed_data)

INSERT INTO hospitals (name, lat, lon, icu_beds, total_icu_beds, soft_reserve, ventilators, total_ventilators, specialists, current_load, max_capacity, equipment_score, status)
VALUES
    ('Apollo Emergency Hospital',    17.4239, 78.4483, 8,  12, 0, 5,  8,  '["cardiology","neurology","trauma"]',                                     45, 120, 0.95, 'active'),
    ('KIMS Heart Center',            17.4156, 78.4347, 6,  10, 0, 4,  6,  '["cardiology","pulmonology"]',                                             62, 100, 0.90, 'active'),
    ('Yashoda Super Specialty',      17.4401, 78.4983, 10, 15, 0, 7,  10, '["cardiology","orthopedics","neurology","trauma"]',                        30, 150, 0.92, 'active'),
    ('Care Hospitals',               17.4485, 78.3908, 4,  8,  0, 3,  5,  '["trauma","orthopedics"]',                                                  78, 90,  0.85, 'active'),
    ('Continental General Hospital', 17.4350, 78.4600, 3,  6,  0, 2,  4,  '["general","pulmonology"]',                                                 55, 80,  0.78, 'active'),
    ('Sunshine Trauma Center',       17.4100, 78.4750, 7,  10, 0, 5,  7,  '["trauma","neurology","orthopedics"]',                                     40, 110, 0.88, 'active'),
    ('Medicover Emergency Wing',     17.4600, 78.4200, 5,  8,  0, 3,  5,  '["cardiology","general"]',                                                  70, 95,  0.82, 'active'),
    ('Global Hospitals',             17.4000, 78.4400, 12, 18, 0, 9,  12, '["cardiology","neurology","trauma","pulmonology","orthopedics"]',          25, 200, 0.97, 'active');
