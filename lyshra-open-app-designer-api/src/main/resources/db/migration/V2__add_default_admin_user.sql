-- ============================================
-- Lyshra OpenApp Workflow Designer
-- Default Admin User
-- Version: 1.0.0
-- ============================================

-- Insert default admin user with password 'admin123'
-- Password hash generated using BCrypt
-- In production, change this password immediately!
INSERT INTO users (
    id,
    username,
    email,
    password_hash,
    first_name,
    last_name,
    roles,
    enabled,
    account_locked,
    failed_login_attempts,
    created_at,
    updated_at,
    version
) VALUES (
    'admin-user-0001-0001-000000000001',
    'admin',
    'admin@lyshra.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMye9ypL9hvWJuV.8nBtbGkwLBY8K1lERuO', -- admin123
    'System',
    'Administrator',
    'ADMIN,DEVELOPER,OPERATOR',
    TRUE,
    FALSE,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);

-- Insert a demo developer user with password 'demo123'
INSERT INTO users (
    id,
    username,
    email,
    password_hash,
    first_name,
    last_name,
    roles,
    enabled,
    account_locked,
    failed_login_attempts,
    created_at,
    updated_at,
    version
) VALUES (
    'demo-user-0001-0001-000000000001',
    'demo',
    'demo@lyshra.com',
    '$2a$10$EqKBt.SkJKLQYsBBJtCXce4e7UY0hT/rZKuNdmY5vEkU0.T.j5WLm', -- demo123
    'Demo',
    'User',
    'DEVELOPER,OPERATOR',
    TRUE,
    FALSE,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
