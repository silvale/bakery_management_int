-- V36: Bảng unit master — quản lý đơn vị tính độc lập với unit_conversion
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE unit (
    code  VARCHAR(20)  NOT NULL,
    name  VARCHAR(100) NOT NULL,
    note  VARCHAR(200),
    CONSTRAINT pk_unit PRIMARY KEY (code)
);

-- Seed từ code_value WHERE group_k = 'UNIT' (V3 + V34)
INSERT INTO unit (code, name) VALUES
    ('KG',      'Kilogram'),
    ('G',       'Gram'),
    ('L',       'Lít'),
    ('ML',      'Mililít'),
    ('CAI',     'Cái'),
    ('HOP',     'Hộp'),
    ('GOI',     'Gói'),
    ('THUNG',   'Thùng'),
    ('BO',      'Bộ'),
    ('BINH',    'Bình'),
    ('CAY',     'Cây'),
    ('ONG',     'Ống'),
    ('CHAI',    'Chai'),
    ('QUA',     'Quả'),
    ('HOP_NHO', 'Hộp Nhỏ')
ON CONFLICT (code) DO NOTHING;

-- Đăng ký màn hình UNITS vào screen_registry
INSERT INTO screen_registry (code, name, available_actions, sort_order)
VALUES ('UNITS', 'Đơn vị tính', 'VIEW,CREATE,UPDATE,DELETE', 45)
ON CONFLICT (code) DO NOTHING;

-- Seed thêm các unit có trong unit_conversion mà chưa có trong danh sách trên
INSERT INTO unit (code, name)
SELECT DISTINCT from_unit, from_unit
FROM unit_conversion
WHERE from_unit NOT IN (SELECT code FROM unit)
ON CONFLICT (code) DO NOTHING;

INSERT INTO unit (code, name)
SELECT DISTINCT to_unit, to_unit
FROM unit_conversion
WHERE to_unit NOT IN (SELECT code FROM unit)
ON CONFLICT (code) DO NOTHING;
