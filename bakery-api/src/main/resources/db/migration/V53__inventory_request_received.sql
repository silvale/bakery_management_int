-- V53 (06/BE-1): ngày thực nhận + người nhận của phiếu nhập hàng

ALTER TABLE inventory_request     ADD COLUMN IF NOT EXISTS received_date DATE;
ALTER TABLE inventory_request     ADD COLUMN IF NOT EXISTS received_by   VARCHAR(100);
ALTER TABLE inventory_request_his ADD COLUMN IF NOT EXISTS received_date DATE;
ALTER TABLE inventory_request_his ADD COLUMN IF NOT EXISTS received_by   VARCHAR(100);

-- Báo cáo nhập hàng lọc theo tháng nhận: PURCHASE + received_date
CREATE INDEX IF NOT EXISTS idx_inv_req_type_received ON inventory_request (request_type, received_date);

-- Backfill phiếu PURCHASE đã duyệt (trước đây không lưu ngày nhận). Thứ tự ưu tiên:
--   1. ngày (giờ Asia/Ho_Chi_Minh) của revision SỚM NHẤT trong inventory_request_his có approval_status = 'APPROVED'
--      (audit_revision_log.timestamp là epoch mili giây)
--   2. expected_delivery_date
--   3. request_date
-- received_by để NULL với dữ liệu cũ.
UPDATE inventory_request r
   SET received_date = COALESCE(
         (SELECT (to_timestamp(min(a.timestamp) / 1000.0) AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
            FROM inventory_request_his h
            JOIN audit_revision_log a ON a.id = h.rev
           WHERE h.id = r.id AND h.approval_status = 'APPROVED'),
         r.expected_delivery_date,
         r.request_date)
 WHERE r.request_type = 'PURCHASE'
   AND r.approval_status = 'APPROVED'
   AND r.received_date IS NULL;
