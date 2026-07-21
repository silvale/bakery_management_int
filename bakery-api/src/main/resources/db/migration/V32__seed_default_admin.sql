-- V32: Seed default SUPER_ADMIN role + admin user
-- password: admin123  (BCrypt $2a$10)
-- Idempotent: ON CONFLICT DO NOTHING

INSERT INTO user_role (id, code, name, description, status, approval_status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'SUPER_ADMIN',
    'Super Admin',
    'Toàn quyền — bypass mọi kiểm tra phân quyền',
    'ACTIVE',
    'APPROVED',
    NOW(),
    NOW()
) ON CONFLICT (code) DO NOTHING;

INSERT INTO user_account (id, username, password_hash, full_name, role_id, status, approval_status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'admin',
    '$2b$10$7tb2NsVDSXh041ySUiB9Ne5Y8mgb1HzEUuIaWiXre0RKcpSzcMwWa',
    'Administrator',
    '00000000-0000-0000-0000-000000000001',
    'ACTIVE',
    'APPROVED',
    NOW(),
    NOW()
) ON CONFLICT (username) DO NOTHING;
