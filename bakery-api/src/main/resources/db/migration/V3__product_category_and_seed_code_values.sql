-- =============================================================
-- V3__product_category_and_seed_code_values.sql
-- 1. Thêm cột product_category vào bảng item
-- 2. Seed toàn bộ CodeValue cho hệ thống
-- =============================================================

-- ── 1. Schema change ─────────────────────────────────────────

ALTER TABLE item
    ADD COLUMN IF NOT EXISTS product_category VARCHAR(50);

COMMENT ON COLUMN item.product_category IS 'Code value: PRODUCT_CATEGORY. Chỉ dùng cho item_type = PRODUCT';
COMMENT ON COLUMN item.warehouse_type    IS 'WarehouseType enum: MAIN | KITCHEN | SHOP';

-- ── 2. Seed CodeValue ─────────────────────────────────────────
-- Dùng INSERT ... ON CONFLICT DO NOTHING để idempotent

-- UNIT — Đơn vị đo lường
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('UNIT', 'KG',    'Kilogram',   1, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'G',     'Gram',       2, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'L',     'Lít',        3, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'ML',    'Mililít',    4, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'CAI',   'Cái',        5, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'HOP',   'Hộp',        6, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'GOI',   'Gói',        7, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'THUNG', 'Thùng',      8, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- INGREDIENT_TYPE — Loại nguyên liệu
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('INGREDIENT_TYPE', 'DRY_GOODS', 'Nguyên Liệu Khô', 1, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- PRODUCT_TYPE — Loại vật tư
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('PRODUCT_TYPE', 'FINISHED', 'Thành Phẩm',      1, 'ACTIVE', 'APPROVED'),
    ('PRODUCT_TYPE', 'SEMI',     'Bán Thành Phẩm',  2, 'ACTIVE', 'APPROVED'),
    ('PRODUCT_TYPE', 'RAW',      'Nguyên Liệu',     3, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- WAREHOUSE_TYPE — Loại kho
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('WAREHOUSE_TYPE', 'MAIN',    'Kho Tổng',   1, 'ACTIVE', 'APPROVED'),
    ('WAREHOUSE_TYPE', 'KITCHEN', 'Kho Bếp',    2, 'ACTIVE', 'APPROVED'),
    ('WAREHOUSE_TYPE', 'SHOP',    'Cửa Hàng',   3, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- PRODUCT_CATEGORY — Nhóm sản phẩm
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('PRODUCT_CATEGORY', 'BMN', 'Bánh Mì Ngọt',  1, 'ACTIVE', 'APPROVED'),
    ('PRODUCT_CATEGORY', 'BMM', 'Bánh Mì Mặn',   2, 'ACTIVE', 'APPROVED'),
    ('PRODUCT_CATEGORY', 'COO', 'Cookie',         3, 'ACTIVE', 'APPROVED'),
    ('PRODUCT_CATEGORY', 'PK',  'Phòng Kem',      4, 'ACTIVE', 'APPROVED'),
    ('PRODUCT_CATEGORY', 'PL',  'Phòng Lạnh',     5, 'ACTIVE', 'APPROVED'),
    ('PRODUCT_CATEGORY', 'LAN', 'Lan',            6, 'ACTIVE', 'APPROVED'),
    ('PRODUCT_CATEGORY', 'ACC', 'Phụ Kiện',       7, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- TRANSACTION_TYPE — Loại giao dịch kho
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('TRANSACTION_TYPE', 'IMPORT', 'Nhập',         1, 'ACTIVE', 'APPROVED'),
    ('TRANSACTION_TYPE', 'EXPORT', 'Xuất',         2, 'ACTIVE', 'APPROVED'),
    ('TRANSACTION_TYPE', 'ADJ',    'Điều chỉnh',   3, 'ACTIVE', 'APPROVED'),
    ('TRANSACTION_TYPE', 'CANCEL', 'Huỷ',          4, 'ACTIVE', 'APPROVED'),
    ('TRANSACTION_TYPE', 'RETURN', 'Trả hàng',     5, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- REF_TYPE_IMPORT — Lý do nhập hàng
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('REF_TYPE_IMPORT', 'DAILY_IMPORT', 'Nhập hàng ngày', 1, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- REF_TYPE_EXPORT — Lý do xuất hàng
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('REF_TYPE_EXPORT', 'DAILY_EXPORT', 'Xuất hàng ngày', 1, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- REF_TYPE_ADJ — Lý do điều chỉnh
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('REF_TYPE_ADJ', 'EXPIRED',         'Hết hạn',         1, 'ACTIVE', 'APPROVED'),
    ('REF_TYPE_ADJ', 'DAMAGED',         'Hư Hỏng',         2, 'ACTIVE', 'APPROVED'),
    ('REF_TYPE_ADJ', 'LOSS',            'Thất Thoát',      3, 'ACTIVE', 'APPROVED'),
    ('REF_TYPE_ADJ', 'LOSS_COMPENSATE', 'Bù Thất Thoát',   4, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- REF_TYPE_CANCEL — Lý do huỷ
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('REF_TYPE_CANCEL', 'EXPIRED', 'Hết hạn',  1, 'ACTIVE', 'APPROVED'),
    ('REF_TYPE_CANCEL', 'DAMAGED', 'Hư Hỏng',  2, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- REF_TYPE_RETURN — Lý do trả hàng
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('REF_TYPE_RETURN', 'EXPIRED',      'Hết hạn',                1, 'ACTIVE', 'APPROVED'),
    ('REF_TYPE_RETURN', 'DAMAGED',      'Hư Hỏng',                2, 'ACTIVE', 'APPROVED'),
    ('REF_TYPE_RETURN', 'WRONG_QTY',    'Không đúng số lượng',    3, 'ACTIVE', 'APPROVED'),
    ('REF_TYPE_RETURN', 'WRONG_TYPE',   'Sai Loại',               4, 'ACTIVE', 'APPROVED'),
    ('REF_TYPE_RETURN', 'POOR_QUALITY', 'Không đúng chất lượng',  5, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;
