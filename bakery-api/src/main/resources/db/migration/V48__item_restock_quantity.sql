-- Mức nhập hàng mục tiêu — khi tồn < min_stock_quantity thì nhập đủ lên restock_quantity.
-- Chỉ dùng cho INGREDIENT và SEMI_PRODUCT.
ALTER TABLE item
    ADD COLUMN IF NOT EXISTS restock_quantity DECIMAL(15, 4);

-- Đồng bộ bảng audit (Hibernate Envers)
ALTER TABLE item_his
    ADD COLUMN IF NOT EXISTS restock_quantity DECIMAL(15, 4);
