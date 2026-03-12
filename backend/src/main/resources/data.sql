-- Seed admin user (password: password123)
INSERT INTO users (id, email, name, password, role, active, created_at)
VALUES (1, 'admin@amalitech.com', 'Admin User', '$2a$12$Qu7RJC3wCVyqn8DkdItaqOJ2W4IBrsVa30gwfEYS116PG3FG2isSC', 'ADMIN',
        true, NOW()),
       (2, 'user@amalitech.com', 'Test User', '$2a$12$Qu7RJC3wCVyqn8DkdItaqOJ2W4IBrsVa30gwfEYS116PG3FG2isSC', 'USER',
        true, NOW())
ON CONFLICT (id) DO NOTHING;


-- Seed retention policies
INSERT INTO retention_policies (service_name, retention_days, archive_enabled)
VALUES ('auth-service', 90, false),
       ('api-gateway', 30, false),
       ('payment-service', 14, false),
       ('user-service', 7, false)
ON CONFLICT DO NOTHING;


-- Seed sample log entries
INSERT INTO log_entries (id, timestamp, level, source, message, service_name, created_at)
VALUES (gen_random_uuid(), NOW() - INTERVAL '1 hour', 'ERROR', 'auth-service', 'Authentication failed for user admin',
        'auth-service', NOW()),
       (gen_random_uuid(), NOW() - INTERVAL '30 minutes', 'INFO', 'api-gateway', 'Request processed in 245ms',
        'api-gateway', NOW()),
       (gen_random_uuid(), NOW() - INTERVAL '15 minutes', 'WARN', 'payment-service', 'Payment retry attempt 2 of 3',
        'payment-service', NOW()),
       (gen_random_uuid(), NOW(), 'INFO', 'user-service', 'User registration completed successfully', 'user-service',
        NOW())
ON CONFLICT DO NOTHING;
