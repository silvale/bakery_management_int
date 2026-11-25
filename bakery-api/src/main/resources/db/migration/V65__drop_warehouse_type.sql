-- V65 (22/BE-2): mọi luồng đã đọc kho theo cấu hình (V64) nên bỏ loại kho cứng.
ALTER TABLE warehouse DROP COLUMN warehouse_type;
-- Danh mục loại kho (V50) chỉ phục vụ ô chọn loại kho cũ
DELETE FROM code_value WHERE group_key = 'WAREHOUSE_TYPE';
