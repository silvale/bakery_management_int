-- V50: 4 kho mặc định + loại kho Phòng lạnh (COLD_ROOM)
-- Trước đây kho chỉ được thêm bằng script chạy tay nên DB mới không có kho nào.

INSERT INTO warehouse (code, name, warehouse_type, status, approval_status, approved_at)
VALUES
    ('MAIN',    'Kho Tổng',   'MAIN',      'ACTIVE', 'APPROVED', NOW()),
    ('KITCHEN', 'Kho Bếp',    'KITCHEN',   'ACTIVE', 'APPROVED', NOW()),
    ('SHOP',    'Cửa Hàng',   'SHOP',      'ACTIVE', 'APPROVED', NOW()),
    ('COLD',    'Phòng Lạnh', 'COLD_ROOM', 'ACTIVE', 'APPROVED', NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES ('WAREHOUSE_TYPE', 'COLD_ROOM', 'Phòng Lạnh', 4, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- Môi trường đã chạy insert_warehouse.sql cũ có SHOP loại BRANCH, không nằm trong enum WarehouseType
UPDATE warehouse SET warehouse_type = 'SHOP' WHERE code = 'SHOP' AND warehouse_type = 'BRANCH';
