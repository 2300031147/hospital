-- V6__seed_users.sql
-- Seed 6 default users (matches Python seed_data)
-- Passwords are BCrypt-hashed (strength 12)

INSERT INTO users (username, password_hash, full_name, role, ambulance_id, hospital_id)
VALUES
    ('admin',       '$2b$12$oL2vlaLvJesIYtiX1v9cBeKHOY6HSv6RmZ.2s7bl/teMrrc6DoCea', 'System Administrator', 'command_center',  NULL, NULL),
    ('hosp1',       '$2b$12$q6mBEn2pdcc5KADLQZTHD.0XdJT4VTOnw1msjqV7CdZ7nxy4zkWkq', 'Apollo Admin',        'hospital_admin',  NULL, 1),
    ('hosp2',       '$2b$12$q6mBEn2pdcc5KADLQZTHD.0XdJT4VTOnw1msjqV7CdZ7nxy4zkWkq', 'KIMS Admin',          'hospital_admin',  NULL, 2),
    ('paramedic1',  '$2b$12$6PVtqHGlSNLD1qLUojBGzODKTy1PUpO5BOWIQSjAXeNsnsotiumsO', 'Ravi Kumar',          'paramedic',       1,   NULL),
    ('driver1',     '$2b$12$uHrDvbbcuWXmdfhLWEF5QegWSEcSmp5IMnTC2NVEiY921ejYBa3aa', 'Suresh Reddy',        'paramedic',       2,   NULL),
    ('medic01',     '$2b$12$5QQo.QGdyI.3DZBkUO0fgOnf4A9D2bXyXac8cJgi9o1BxI/AAdg7a', 'Priya Sharma',        'paramedic',       3,   NULL),
    ('dispatcher1', '$2b$12$uElQXp7ekCqt5Mp4SKfnUuc5LQ2JaMwfObeJmsKooEs7c/w.y6aXa', 'Dispatch Operator',   'dispatcher',      NULL, NULL);
