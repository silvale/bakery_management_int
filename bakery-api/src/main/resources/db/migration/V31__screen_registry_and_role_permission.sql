-- ================================================================
-- V31: Screen Registry + Role Permission
-- ================================================================

CREATE TABLE IF NOT EXISTS screen_registry (
    code             VARCHAR(50)  NOT NULL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    available_actions TEXT         NOT NULL, -- comma-separated: VIEW,CREATE,UPDATE,...
    sort_order       INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id     UUID        NOT NULL REFERENCES user_role (id) ON DELETE CASCADE,
    screen_code VARCHAR(50) NOT NULL REFERENCES screen_registry (code) ON DELETE CASCADE,
    action_code VARCHAR(30) NOT NULL,
    PRIMARY KEY (role_id, screen_code, action_code)
);

CREATE INDEX IF NOT EXISTS idx_role_permission_role_id ON role_permission (role_id);

-- ================================================================
-- Seed: toàn bộ màn hình trong UI
-- ================================================================
INSERT INTO screen_registry (code, name, available_actions, sort_order) VALUES
  ('ITEMS',              'Sản phẩm',            'VIEW,CREATE,UPDATE,DELETE,APPROVE,REJECT,HISTORY', 10),
  ('SUPPLIERS',          'Nhà cung cấp',        'VIEW,CREATE,UPDATE,DELETE,APPROVE',                20),
  ('PRODUCT_MAPPING',    'Product Mapping',     'VIEW,CREATE,UPDATE,DELETE',                        30),
  ('ITEM_GROUPS',        'Item Groups',         'VIEW,CREATE,UPDATE,DELETE',                        40),
  ('SX_CONFIG',          'Cấu hình SX',         'VIEW,UPDATE',                                      50),
  ('PROD_GROUPS',        'Production Groups',   'VIEW,CREATE,UPDATE,DELETE',                        60),
  ('THRESHOLD_RULES',    'Threshold Rules',     'VIEW,CREATE,UPDATE,DELETE',                        70),
  ('PROD_PLANS',         'Kế hoạch SX',         'VIEW,CREATE,APPROVE,REJECT',                       80),
  ('PROD_REQUESTS',      'Phiếu SX',            'VIEW,CREATE,APPROVE,REJECT',                       90),
  ('DELIVERY_RECORDS',   'Giao nhận',           'VIEW,APPROVE',                                    100),
  ('PROD_ADJUSTMENTS',   'Điều chỉnh SX',       'VIEW,APPROVE,REJECT',                             110),
  ('INVENTORY_REQUESTS', 'Phiếu kho',           'VIEW,CREATE,APPROVE,REJECT',                      120),
  ('STOCK_SUMMARY',      'Tồn kho',             'VIEW',                                            130),
  ('DAILY_REPORT',       'Báo cáo ngày',        'VIEW,CREATE,FINALIZE',                            140),
  ('HUY_BANH',           'Hủy bánh',            'VIEW,CREATE',                                     150),
  ('POS_SALES',          'POS Sales',           'VIEW,CREATE',                                     160),
  ('USERS',              'Tài khoản',           'VIEW,CREATE,UPDATE,DELETE',                       170),
  ('ROLES',              'Phân quyền',          'VIEW,CREATE,UPDATE,DELETE',                       180)
ON CONFLICT (code) DO NOTHING;
