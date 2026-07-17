-- V23: Thêm unit_cost vào item table
-- INGREDIENT: nhập tay (giá vốn thực tế)
-- SEMI_PRODUCT / PRODUCT: tính tự động từ công thức (trigger khi approve recipe)
ALTER TABLE item
    ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(15, 4);
