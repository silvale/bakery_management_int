-- Thêm field yield_quantity vào recipe để lưu tổng KL sản phẩm BTP/SP tạo ra từ 1 mẻ.
-- Dùng trong RecipeCostService để tính đúng giá/KG: batchCost / yieldQuantity.
-- null = tự tính từ tổng KG nguyên liệu trong công thức (backward compatible).
ALTER TABLE recipe ADD COLUMN IF NOT EXISTS yield_quantity NUMERIC(10, 4);
ALTER TABLE recipe_his ADD COLUMN IF NOT EXISTS yield_quantity NUMERIC(10, 4);