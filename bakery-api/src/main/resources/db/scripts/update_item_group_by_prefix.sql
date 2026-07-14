-- Update item_group_id dựa vào prefix của item.code
-- Prefix = phần trước dấu '-' đầu tiên (VD: 'PL-BANH-BIA' → 'PL')
-- Chỉ update những item chưa có item_group_id

UPDATE item i
SET item_group_id = g.id
FROM item_group g
WHERE i.item_group_id IS NULL
  AND split_part(i.code, '-', 1) = g.code;

-- Kiểm tra kết quả
SELECT
    split_part(i.code, '-', 1) AS prefix,
    g.code                     AS group_code,
    g.name                     AS group_name,
    COUNT(*)                   AS so_item
FROM item i
JOIN item_group g ON i.item_group_id = g.id
GROUP BY split_part(i.code, '-', 1), g.code, g.name
ORDER BY g.code;

-- Báo các item vẫn chưa có group (prefix không khớp)
SELECT code, name, item_type
FROM item
WHERE item_group_id IS NULL
ORDER BY item_type, code;
