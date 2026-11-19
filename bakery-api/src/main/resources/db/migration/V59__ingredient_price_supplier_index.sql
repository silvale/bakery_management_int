-- V59 (12/BE-1): lọc mặt hàng theo NCC — tra ingredient_price theo (supplier_id, item_id); trước đây chỉ có (item_id, effective_date)
CREATE INDEX IF NOT EXISTS idx_ingredient_price_supplier_item ON ingredient_price (supplier_id, item_id);
