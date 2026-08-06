-- V39: Backfill item.unit_cost cho INGREDIENT từ ingredient_price (giá mới nhất)
-- Chạy 1 lần để seed giá mặc định cho những NL đã có trong ingredient_price nhưng
-- item.unit_cost còn NULL. Sau đó mỗi lần duyệt phiếu nhập, giá sẽ tự cập nhật.

UPDATE item i
SET unit_cost = sub.latest_price
FROM (
    SELECT DISTINCT ON (ip.item_id)
        ip.item_id,
        ip.price AS latest_price
    FROM ingredient_price ip
    ORDER BY ip.item_id, ip.effective_date DESC
) sub
WHERE i.id = sub.item_id
  AND i.item_type = 'INGREDIENT'
  AND i.unit_cost IS NULL;
