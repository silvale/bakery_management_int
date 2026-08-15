-- V40: Xóa 2 field không sử dụng — ingredient_type và product_type
-- Phân loại đã được thay thế hoàn toàn bằng item_group (phòng SX).
ALTER TABLE item DROP COLUMN IF EXISTS ingredient_type;
ALTER TABLE item DROP COLUMN IF EXISTS product_type;
