-- V61: chỉ Admin + Bếp trưởng được xem công thức / danh mục BTP (17/BE-1, D1)
-- Admin cấp thêm RECIPES:VIEW cho vai trò khác ở màn Phân quyền nếu cần. SUPER_ADMIN bypass trong code nên không cần dòng.

-- Thu hồi mọi quyền RECIPES của vai trò khác (kể cả vai trò cũ như KHO nếu có)
DELETE FROM role_permission rp
 USING user_role r
 WHERE rp.role_id = r.id
   AND rp.screen_code = 'RECIPES'
   AND r.code NOT IN ('SUPER_ADMIN', 'KITCHEN_MANAGER');

-- Bếp trưởng có RECIPES:VIEW (seed lại nếu thiếu)
INSERT INTO role_permission (role_id, screen_code, action_code)
SELECT r.id, 'RECIPES', 'VIEW'
  FROM user_role r
 WHERE r.code = 'KITCHEN_MANAGER'
ON CONFLICT DO NOTHING;
