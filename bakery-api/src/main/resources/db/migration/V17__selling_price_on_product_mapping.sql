-- V17: Chuyển selling_price từ item sang product_mapping
-- Lý do: cùng 1 sản phẩm (item) có thể có nhiều mức giá khác nhau tùy EX_CODE
-- (ví dụ: trang trí thứ 2-6 / thứ 7 / CN → giá khác nhau)
-- → Giá bán phải gắn với EX_CODE (product_mapping), không phải item

ALTER TABLE product_mapping
    ADD COLUMN IF NOT EXISTS selling_price NUMERIC(15, 2);

-- Xóa selling_price khỏi item nếu chưa có data (chưa dùng)
-- Giữ lại column item.selling_price để backward compat, set null
UPDATE item SET selling_price = NULL WHERE item_type = 'PRODUCT';
