-- =============================================================
-- insert_warehouse.sql
-- 3 kho cơ bản
-- =============================================================

INSERT INTO warehouse (code, name, warehouse_type, status, approval_status, approved_at)
VALUES
    ('MAIN',    'Kho Chính',  'MAIN',    'ACTIVE', 'APPROVED', NOW()),
    ('KITCHEN', 'Kho Bếp',   'KITCHEN', 'ACTIVE', 'APPROVED', NOW()),
    ('SHOP',    'Cửa Hàng',  'BRANCH',  'ACTIVE', 'APPROVED', NOW())
ON CONFLICT (code) DO NOTHING;
