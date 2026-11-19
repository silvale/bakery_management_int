-- V58 (11/BE-1): phân quyền giai đoạn 1 — 7 vai trò hệ thống, kho theo user, NCC xem giá, màn hình/action mới

-- ── Cột/bảng mới ───────────────────────────────────────────────────────────
ALTER TABLE user_role ADD COLUMN IF NOT EXISTS is_system boolean NOT NULL DEFAULT false;
ALTER TABLE user_role ADD COLUMN IF NOT EXISTS default_warehouse_codes varchar(200);   -- vd 'KITCHEN,COLD'
ALTER TABLE user_role ADD COLUMN IF NOT EXISTS price_by_supplier boolean NOT NULL DEFAULT false;   -- vai trò có quyền giá theo NCC (D3)

CREATE TABLE IF NOT EXISTS user_warehouse (
    user_id      uuid NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    warehouse_id uuid NOT NULL REFERENCES warehouse (id),
    PRIMARY KEY (user_id, warehouse_id)
);
CREATE TABLE IF NOT EXISTS user_price_supplier (
    user_id     uuid NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    supplier_id uuid NOT NULL REFERENCES supplier (id),
    PRIMARY KEY (user_id, supplier_id)
);
CREATE INDEX IF NOT EXISTS idx_user_warehouse_wh ON user_warehouse (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_user_price_supplier_sup ON user_price_supplier (supplier_id);

-- NCC "Mua lẻ / trả ngay" (chợ). supplier là @Audited nên thêm cả vào supplier_his.
ALTER TABLE supplier     ADD COLUMN IF NOT EXISTS is_cash_purchase boolean NOT NULL DEFAULT false;
ALTER TABLE supplier_his ADD COLUMN IF NOT EXISTS is_cash_purchase boolean;

-- ── Màn hình / action mới ──────────────────────────────────────────────────
INSERT INTO screen_registry (code, name, available_actions, sort_order) VALUES
  ('DASHBOARD',          'Bảng điều khiển',         'VIEW',                                              5),
  ('STOCK_ADJUSTMENT',   'Điều chỉnh kiểm kê',      'VIEW,CREATE,UPDATE,UPDATE_ANY,APPROVE,REJECT',      125),
  ('LOW_STOCK',          'Hàng cần nhập',           'VIEW,CREATE',                                       135),
  ('RECIPES',            'Công thức',               'VIEW,CREATE,UPDATE,DELETE,APPROVE,REJECT,HISTORY',  85),
  ('COST_PRICE',         'Giá vốn',                 'VIEW,UPDATE',                                       190),
  ('PURCHASE_REPORT',    'BC Nhập hàng',            'VIEW',                                              200),
  ('ADJUSTMENT_REPORT',  'BC Điều chỉnh kho',       'VIEW',                                              210),
  ('CAKE_DESIGN_REPORT', 'BC Mẫu bánh',             'VIEW',                                              220),
  ('ADMIN_REPORTS',      'BC Admin / Tổng hợp',     'VIEW',                                              230),
  ('PAYMENTS',           'Thanh toán phiếu nhập',   'VIEW,UPDATE',                                       240),
  ('ACTIVITY_LOG',       'Nhật ký hoạt động',       'VIEW',                                              250),
  ('WAREHOUSES',         'Kho',                     'VIEW,CREATE,UPDATE,DELETE',                         260),
  ('CODE_VALUES',        'Danh mục mã',             'VIEW,CREATE,UPDATE,DELETE',                         270)
ON CONFLICT (code) DO NOTHING;

UPDATE screen_registry SET available_actions = 'VIEW,CREATE,UPDATE,UPDATE_ANY,RECEIVE,APPROVE,REJECT' WHERE code = 'INVENTORY_REQUESTS';
UPDATE screen_registry SET available_actions = 'VIEW,CREATE,APPROVE,REJECT,COMPLETE'                     WHERE code = 'PROD_REQUESTS';

-- ── 7 vai trò hệ thống ─────────────────────────────────────────────────────
UPDATE user_role SET name = 'Admin', is_system = true WHERE code = 'SUPER_ADMIN';

INSERT INTO user_role (code, name, description, status, approval_status, approved_at, is_system, default_warehouse_codes, price_by_supplier) VALUES
  ('MAIN_MANAGER',    'Quản lý kho tổng',   'Quản lý Kho Tổng',         'ACTIVE', 'APPROVED', now(), true, 'MAIN',         true),
  ('MAIN_STAFF',      'Nhân viên kho',      'Nhân viên Kho Tổng',       'ACTIVE', 'APPROVED', now(), true, 'MAIN',         true),
  ('KITCHEN_MANAGER', 'Quản lý bếp',        'Quản lý bếp',              'ACTIVE', 'APPROVED', now(), true, 'KITCHEN,COLD', true),
  ('KITCHEN_STAFF',   'Nhân viên bếp',      'Nhân viên bếp',            'ACTIVE', 'APPROVED', now(), true, 'KITCHEN,COLD', true),
  ('SHOP_MANAGER',    'Quản lý cửa hàng',   'Quản lý cửa hàng',         'ACTIVE', 'APPROVED', now(), true, 'SHOP',         true),
  ('SHOP_STAFF',      'Nhân viên cửa hàng', 'Nhân viên cửa hàng',       'ACTIVE', 'APPROVED', now(), true, 'SHOP',         false)
ON CONFLICT (code) DO UPDATE SET is_system = true, default_warehouse_codes = EXCLUDED.default_warehouse_codes,
                                 price_by_supplier = EXCLUDED.price_by_supplier;

-- ── Bộ quyền mặc định (11-phan-quyen-be.md §3). Mọi quyền chỉ trong kho được gán. ──
WITH seed(role_code, screen_code, actions) AS (VALUES
  -- Quản lý kho tổng
  ('MAIN_MANAGER','DASHBOARD','VIEW'), ('MAIN_MANAGER','STOCK_SUMMARY','VIEW'),
  ('MAIN_MANAGER','INVENTORY_REQUESTS','VIEW,CREATE,UPDATE,UPDATE_ANY,RECEIVE,APPROVE,REJECT'),
  ('MAIN_MANAGER','STOCK_ADJUSTMENT','VIEW,CREATE,UPDATE,UPDATE_ANY'), ('MAIN_MANAGER','LOW_STOCK','VIEW,CREATE'),
  -- Nhân viên kho
  ('MAIN_STAFF','DASHBOARD','VIEW'), ('MAIN_STAFF','STOCK_SUMMARY','VIEW'),
  ('MAIN_STAFF','INVENTORY_REQUESTS','VIEW,CREATE,UPDATE'), ('MAIN_STAFF','STOCK_ADJUSTMENT','VIEW,CREATE,UPDATE'),
  ('MAIN_STAFF','LOW_STOCK','VIEW'),
  -- Quản lý bếp
  ('KITCHEN_MANAGER','DASHBOARD','VIEW'), ('KITCHEN_MANAGER','STOCK_SUMMARY','VIEW'),
  ('KITCHEN_MANAGER','INVENTORY_REQUESTS','VIEW,CREATE,UPDATE,UPDATE_ANY,RECEIVE,APPROVE,REJECT'),
  ('KITCHEN_MANAGER','STOCK_ADJUSTMENT','VIEW,CREATE,UPDATE,UPDATE_ANY'), ('KITCHEN_MANAGER','LOW_STOCK','VIEW'),
  ('KITCHEN_MANAGER','PROD_PLANS','VIEW,CREATE,APPROVE,REJECT'),
  ('KITCHEN_MANAGER','PROD_REQUESTS','VIEW,CREATE,APPROVE,REJECT,COMPLETE'),
  ('KITCHEN_MANAGER','PROD_ADJUSTMENTS','VIEW,APPROVE,REJECT'), ('KITCHEN_MANAGER','DELIVERY_RECORDS','VIEW'),
  ('KITCHEN_MANAGER','SX_CONFIG','VIEW'), ('KITCHEN_MANAGER','PROD_GROUPS','VIEW'), ('KITCHEN_MANAGER','THRESHOLD_RULES','VIEW'),
  -- Nhân viên bếp
  ('KITCHEN_STAFF','DASHBOARD','VIEW'), ('KITCHEN_STAFF','STOCK_SUMMARY','VIEW'),
  ('KITCHEN_STAFF','INVENTORY_REQUESTS','VIEW,CREATE,UPDATE'), ('KITCHEN_STAFF','STOCK_ADJUSTMENT','VIEW,CREATE,UPDATE'),
  ('KITCHEN_STAFF','PROD_PLANS','VIEW'), ('KITCHEN_STAFF','PROD_REQUESTS','VIEW,COMPLETE'),
  ('KITCHEN_STAFF','PROD_ADJUSTMENTS','VIEW'), ('KITCHEN_STAFF','DELIVERY_RECORDS','VIEW'),
  -- Quản lý cửa hàng
  ('SHOP_MANAGER','DASHBOARD','VIEW'), ('SHOP_MANAGER','STOCK_SUMMARY','VIEW'),
  ('SHOP_MANAGER','INVENTORY_REQUESTS','VIEW,CREATE,UPDATE,UPDATE_ANY,RECEIVE,APPROVE,REJECT'),
  ('SHOP_MANAGER','STOCK_ADJUSTMENT','VIEW,CREATE,UPDATE,UPDATE_ANY'), ('SHOP_MANAGER','LOW_STOCK','VIEW'),
  ('SHOP_MANAGER','DELIVERY_RECORDS','VIEW,APPROVE'), ('SHOP_MANAGER','DAILY_REPORT','VIEW,CREATE,FINALIZE'),
  ('SHOP_MANAGER','HUY_BANH','VIEW,CREATE'), ('SHOP_MANAGER','POS_SALES','VIEW,CREATE'),
  -- Nhân viên cửa hàng
  ('SHOP_STAFF','DASHBOARD','VIEW'), ('SHOP_STAFF','STOCK_SUMMARY','VIEW'),
  ('SHOP_STAFF','INVENTORY_REQUESTS','VIEW'), ('SHOP_STAFF','STOCK_ADJUSTMENT','VIEW,CREATE,UPDATE'),
  ('SHOP_STAFF','DELIVERY_RECORDS','VIEW,APPROVE'), ('SHOP_STAFF','DAILY_REPORT','VIEW,CREATE'),
  ('SHOP_STAFF','HUY_BANH','VIEW,CREATE')
)
INSERT INTO role_permission (role_id, screen_code, action_code)
SELECT r.id, s.screen_code, a.action
  FROM seed s
  JOIN user_role r ON r.code = s.role_code
  JOIN screen_registry sr ON sr.code = s.screen_code
  CROSS JOIN LATERAL regexp_split_to_table(s.actions, ',') AS a(action)
ON CONFLICT DO NOTHING;

-- Danh mục dùng chung (xem được với mọi vai trò): sản phẩm, NCC, mẫu bánh, đơn vị, nhóm, mapping, công thức, mã, kho
WITH roles(code) AS (VALUES ('MAIN_MANAGER'),('MAIN_STAFF'),('KITCHEN_MANAGER'),('KITCHEN_STAFF'),('SHOP_MANAGER'),('SHOP_STAFF')),
     screens(code) AS (VALUES ('ITEMS'),('SUPPLIERS'),('CAKE_DESIGN'),('UNITS'),('ITEM_GROUPS'),('PRODUCT_MAPPING'),('RECIPES'),
                              ('WAREHOUSES'),('CODE_VALUES'))
INSERT INTO role_permission (role_id, screen_code, action_code)
SELECT r.id, s.code, 'VIEW' FROM roles x JOIN user_role r ON r.code = x.code CROSS JOIN screens s
ON CONFLICT DO NOTHING;
