-- =============================================================
-- V12__drop_product_category.sql
-- Bỏ cột product_category khỏi item — chức năng phân loại được
-- gộp vào item_group_id (FK tới item_group).
-- =============================================================

ALTER TABLE item
    DROP COLUMN IF EXISTS product_category;
