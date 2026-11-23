-- V62: quyền theo tab màn Sản phẩm (19/BE-1)
-- Danh mục BTP theo ITEMS_SEMI:VIEW, công thức vẫn theo RECIPES:VIEW (17). Mặc định giữ y như trước (D4).
-- SUPER_ADMIN bypass trong code nên không cần dòng.

INSERT INTO screen_registry (code, name, available_actions, sort_order) VALUES
  ('ITEMS_PRODUCT',    'Sản phẩm – tab Thành phẩm',     'VIEW', 11),
  ('ITEMS_SEMI',       'Sản phẩm – tab Bán thành phẩm', 'VIEW', 12),
  ('ITEMS_INGREDIENT', 'Sản phẩm – tab Nguyên liệu',    'VIEW', 13)
ON CONFLICT (code) DO NOTHING;

-- Thành phẩm + Nguyên liệu: mọi vai trò đang có ITEMS:VIEW
INSERT INTO role_permission (role_id, screen_code, action_code)
SELECT rp.role_id, t.code, 'VIEW'
  FROM role_permission rp
 CROSS JOIN (VALUES ('ITEMS_PRODUCT'), ('ITEMS_INGREDIENT')) AS t(code)
 WHERE rp.screen_code = 'ITEMS' AND rp.action_code = 'VIEW'
ON CONFLICT DO NOTHING;

-- Bán thành phẩm: mọi vai trò đang có RECIPES:VIEW (17/D1 buộc BTP với quyền này)
INSERT INTO role_permission (role_id, screen_code, action_code)
SELECT rp.role_id, 'ITEMS_SEMI', 'VIEW'
  FROM role_permission rp
 WHERE rp.screen_code = 'RECIPES' AND rp.action_code = 'VIEW'
ON CONFLICT DO NOTHING;
