-- =============================================================
-- V11__add_missing_product_mapping_columns.sql
-- Thêm cột production_day vào product_mapping nếu chưa có
-- (cột này có trong định nghĩa V6 nhưng DB được tạo trước khi V6 được cập nhật)
-- =============================================================

ALTER TABLE product_mapping
    ADD COLUMN IF NOT EXISTS production_day SMALLINT;
