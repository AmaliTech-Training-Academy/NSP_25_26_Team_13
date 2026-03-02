-- Seed admin user (password: password123)
INSERT INTO users (id, email, name, password, role, created_at) VALUES
  (1, 'admin@amalitech.com', 'Admin User', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN', NOW()),
  (2, 'user@amalitech.com', 'Test User', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER', NOW())
ON CONFLICT (id) DO NOTHING;

-- Seed retention policies
INSERT INTO retention_policies (id, name, retention_days, log_level, active) VALUES
  (1, 'Error Retention', 90, 'ERROR', true),
  (2, 'Warning Retention', 30, 'WARN', true),
  (3, 'Info Retention', 14, 'INFO', true),
  (4, 'Debug Retention', 7, 'DEBUG', true)
ON CONFLICT (id) DO NOTHING;

-- Seed sample log entries
INSERT INTO log_entries (id, timestamp, level, source, message, service_name, created_at) VALUES
  (gen_random_uuid(), NOW() - INTERVAL '1 hour', 'ERROR', 'auth-service', 'Authentication failed for user admin', 'auth-service', NOW()),
  (gen_random_uuid(), NOW() - INTERVAL '30 minutes', 'INFO', 'api-gateway', 'Request processed in 245ms', 'api-gateway', NOW()),
  (gen_random_uuid(), NOW() - INTERVAL '15 minutes', 'WARN', 'payment-service', 'Payment retry attempt 2 of 3', 'payment-service', NOW()),
  (gen_random_uuid(), NOW(), 'INFO', 'user-service', 'User registration completed successfully', 'user-service', NOW())
ON CONFLICT DO NOTHING;
