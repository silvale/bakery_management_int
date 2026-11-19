-- V49: nhận hàng theo số thực nhận (BE-2), thanh toán phiếu nhập (BE-3), chỉ mục lọc (BE-4)

-- ── BE-2: số thực nhận theo dòng ─────────────────────────────────────────────
ALTER TABLE inventory_request_line     ADD COLUMN IF NOT EXISTS received_qty NUMERIC(15, 4);
ALTER TABLE inventory_request_line_his ADD COLUMN IF NOT EXISTS received_qty NUMERIC(15, 4);

-- Phiếu nhập đã duyệt trước đây coi như nhận đủ
UPDATE inventory_request_line l
   SET received_qty = l.quantity
  FROM inventory_request r
 WHERE r.id = l.inventory_request_id
   AND r.request_type = 'PURCHASE'
   AND r.approval_status = 'APPROVED';

-- Màn Roles cần cấp được quyền UPDATE (PUT /inventory-requests/{id} đang kiểm quyền này)
UPDATE screen_registry
   SET available_actions = 'VIEW,CREATE,UPDATE,APPROVE,REJECT'
 WHERE code = 'INVENTORY_REQUESTS';

-- ── BE-3: thanh toán ─────────────────────────────────────────────────────────
ALTER TABLE inventory_request     ADD COLUMN IF NOT EXISTS payment_status VARCHAR(10);
ALTER TABLE inventory_request     ADD COLUMN IF NOT EXISTS paid_at        TIMESTAMPTZ;
ALTER TABLE inventory_request     ADD COLUMN IF NOT EXISTS paid_by        VARCHAR(100);
ALTER TABLE inventory_request_his ADD COLUMN IF NOT EXISTS payment_status VARCHAR(10);
ALTER TABLE inventory_request_his ADD COLUMN IF NOT EXISTS paid_at        TIMESTAMPTZ;
ALTER TABLE inventory_request_his ADD COLUMN IF NOT EXISTS paid_by        VARCHAR(100);

-- Phiếu nhập hiện có coi là chưa thanh toán; loại khác để NULL
UPDATE inventory_request SET payment_status = 'UNPAID' WHERE request_type = 'PURCHASE';

-- ── BE-4: chỉ mục cho danh sách phiếu nhập (tình trạng / hạn giao / thanh toán) ──
CREATE INDEX IF NOT EXISTS idx_inv_req_purchase_filter
    ON inventory_request (approval_status, expected_delivery_date, payment_status)
    WHERE request_type = 'PURCHASE';
