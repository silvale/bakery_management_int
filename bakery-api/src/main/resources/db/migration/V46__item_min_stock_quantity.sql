-- V46: Thêm ngưỡng cảnh báo tồn kho tối thiểu cho Ingredient và SemiProduct
ALTER TABLE item ADD COLUMN IF NOT EXISTS min_stock_quantity DECIMAL(15, 4);

COMMENT ON COLUMN item.min_stock_quantity IS
    'Ngưỡng tồn kho tối thiểu. Cảnh báo khi SUM(stock_lot.qty_remaining) < min_stock_quantity. Chỉ dùng cho INGREDIENT và SEMI_PRODUCT.';
