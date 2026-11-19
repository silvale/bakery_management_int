-- V56 (10/BE-3): item_his thiếu cột nên khi bật audit cho các lớp con của Item (Ingredient, SemiProduct, Product)
-- Envers sẽ lỗi khi ghi. Thêm các cột của item chưa có ở item_his. Không backfill lịch sử cũ.
ALTER TABLE item_his ADD COLUMN IF NOT EXISTS default_supplier_id UUID;
ALTER TABLE item_his ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
ALTER TABLE item_his ADD COLUMN IF NOT EXISTS min_stock_quantity NUMERIC(15, 4);
