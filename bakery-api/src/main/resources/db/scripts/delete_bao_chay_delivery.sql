-- Xóa delivery_record CONFIRMED của Bánh Bao Chay hôm nay (record lỗi trước fix)
-- Chạy từng câu một trong DBeaver

-- 1. Xem trước để confirm đúng record
SELECT dr.id, dr.delivery_status, dr.qty_received, dr.confirmed_at,
       prl.product_id, i.name, pr.production_date
FROM delivery_record dr
JOIN production_request_line prl ON prl.id = dr.production_request_line_id
JOIN production_request pr       ON pr.id  = prl.production_request_id
JOIN item i                      ON i.id   = prl.product_id
WHERE i.name ILIKE '%Bánh Bao Chay%'
  AND pr.production_date = CURRENT_DATE;

-- 2. Xóa (chạy sau khi confirm câu 1 đúng)
DELETE FROM delivery_record dr
USING production_request_line prl,
      production_request pr,
      item i
WHERE dr.production_request_line_id = prl.id
  AND prl.production_request_id = pr.id
  AND prl.item_id = i.id
  AND i.name ILIKE '%Bánh Bao Chay%'
  AND pr.production_date = CURRENT_DATE;

-- 3. Reset production_request_line về PENDING (để kitchen complete lại)
UPDATE production_request_line prl
SET approval_status = 'PENDING',
    qty_produced    = 0,
    updated_at      = NOW()
FROM production_request pr, item i
WHERE prl.production_request_id = pr.id
  AND prl.product_id = i.id
  AND i.name ILIKE '%Bánh Bao Chay%'
  AND pr.production_date = CURRENT_DATE;



select * product_request 
