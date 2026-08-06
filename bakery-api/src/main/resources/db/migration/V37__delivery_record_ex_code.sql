-- V37: Gắn EX_CODE vào delivery_record và stock_lot
-- Khi NV xác nhận giao nhận, hệ thống tra product_mapping để tìm EX_CODE
-- khớp với (item_id + ngày sản xuất) và lưu lại.

ALTER TABLE delivery_record
    ADD COLUMN IF NOT EXISTS ex_code VARCHAR(50);

ALTER TABLE stock_lot
    ADD COLUMN IF NOT EXISTS ex_code VARCHAR(50);

COMMENT ON COLUMN delivery_record.ex_code IS 'EX_CODE từ product_mapping khớp với item + ngày SX, gán khi shop confirm giao nhận';
COMMENT ON COLUMN stock_lot.ex_code IS 'EX_CODE của lô bánh (chỉ có đối với lô SHOP, lấy từ product_mapping)';
