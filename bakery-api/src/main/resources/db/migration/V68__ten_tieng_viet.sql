-- 26/BE-1: không còn tiếng Anh / "Bán thành phẩm" trên giao diện; "Hàng cần nhập" chỉ Admin (mặc định).

-- 1. Tên màn trong Phân quyền (chỉ đổi tên hiển thị, mã giữ nguyên)
UPDATE screen_registry SET name = 'Liên kết mã POS'                WHERE code = 'PRODUCT_MAPPING';
UPDATE screen_registry SET name = 'Nhóm hàng hóa'                  WHERE code = 'ITEM_GROUPS';
UPDATE screen_registry SET name = 'Nhóm sản xuất'                  WHERE code = 'PROD_GROUPS';
UPDATE screen_registry SET name = 'Quản lý sản xuất'               WHERE code = 'THRESHOLD_RULES';
UPDATE screen_registry SET name = 'Tải dữ liệu POS'                WHERE code = 'POS_SALES';
UPDATE screen_registry SET name = 'Sản phẩm – tab Hàng gia công'   WHERE code = 'ITEMS_SEMI';
-- Còn sót chữ "bán thành phẩm" ở màn khác (nếu có) → hàng gia công
UPDATE screen_registry SET name = regexp_replace(name, '[Bb]án [Tt]hành [Pp]hẩm', 'hàng gia công', 'g')
 WHERE name ~* 'bán thành phẩm';

-- 2. Danh mục mã: loại sản phẩm SEMI. Không đụng UNIT.* (admin tự đổi) và PRODUCT_CATEGORY.* (dữ liệu admin nhập).
UPDATE code_value SET name = 'Hàng gia công' WHERE group_key = 'PRODUCT_TYPE' AND code = 'SEMI';

-- 3. Hàng cần nhập chỉ Admin: bỏ quyền LOW_STOCK của vai trò hệ thống (Admin bypass nên không có dòng). Vai trò tự tạo giữ nguyên.
DELETE FROM role_permission rp
 USING user_role r
 WHERE r.id = rp.role_id AND rp.screen_code = 'LOW_STOCK' AND r.is_system AND r.code <> 'SUPER_ADMIN';
