-- V54 (08/BE-1): phiếu điều chỉnh tồn theo số đếm thực tế

ALTER TABLE inventory_request_line     ADD COLUMN IF NOT EXISTS system_qty  NUMERIC(15, 4);   -- tồn hệ thống lúc lưu phiếu
ALTER TABLE inventory_request_line     ADD COLUMN IF NOT EXISTS counted_qty NUMERIC(15, 4);   -- số đếm thực tế
ALTER TABLE inventory_request_line     ADD COLUMN IF NOT EXISTS reason_code VARCHAR(30);      -- code_value ADJUSTMENT_REASON
ALTER TABLE inventory_request_line     ADD COLUMN IF NOT EXISTS amount      NUMERIC(15, 2);   -- giá trị điều chỉnh, lưu lúc duyệt (tăng +, giảm -)
ALTER TABLE inventory_request_line_his ADD COLUMN IF NOT EXISTS system_qty  NUMERIC(15, 4);
ALTER TABLE inventory_request_line_his ADD COLUMN IF NOT EXISTS counted_qty NUMERIC(15, 4);
ALTER TABLE inventory_request_line_his ADD COLUMN IF NOT EXISTS reason_code VARCHAR(30);
ALTER TABLE inventory_request_line_his ADD COLUMN IF NOT EXISTS amount      NUMERIC(15, 2);

-- Thống kê điều chỉnh theo tháng duyệt (approved_at)
CREATE INDEX IF NOT EXISTS idx_inv_req_type_approved ON inventory_request (request_type, approved_at);

INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES ('ADJUSTMENT_REASON', 'COUNT_ERROR',  'Kiểm kê lệch',      1, 'ACTIVE', 'APPROVED'),
       ('ADJUSTMENT_REASON', 'DAMAGED',      'Hỏng, vỡ',          2, 'ACTIVE', 'APPROVED'),
       ('ADJUSTMENT_REASON', 'EXPIRED',      'Hết hạn',           3, 'ACTIVE', 'APPROVED'),
       ('ADJUSTMENT_REASON', 'LOST',         'Mất',               4, 'ACTIVE', 'APPROVED'),
       ('ADJUSTMENT_REASON', 'INTERNAL_USE', 'Dùng nội bộ, tặng', 5, 'ACTIVE', 'APPROVED'),
       ('ADJUSTMENT_REASON', 'OTHER',        'Khác',              6, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;
