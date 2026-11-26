-- V67 (24/BE-1): bảng tạm = kho "Đang sản xuất" của cửa hàng; phiếu Lấy / Trả / Hao hụt; phiên ngày; lô giữ cho phiếu SX.
-- Bảng production_material của 20 giữ nguyên để tra lịch sử, không ghi nữa.

-- 1. Cờ kho bảng tạm
ALTER TABLE warehouse ADD COLUMN is_work_in_progress BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uq_warehouse_wip_per_store ON warehouse (store_id) WHERE is_work_in_progress;
-- Kho bảng tạm phải thuộc cửa hàng, nội bộ và không làm việc nào khác
ALTER TABLE warehouse ADD CONSTRAINT ck_warehouse_wip_exclusive CHECK (
    NOT is_work_in_progress OR (store_id IS NOT NULL AND kind = 'INTERNAL'
        AND NOT (can_receive_purchase OR is_production_source OR can_store_semi
                 OR is_delivery_hold OR is_sales_point OR can_export_external)));

-- 2. Kho WIP_CH1 ngay sau Phòng Lạnh (COLD) của CH1; viết theo mã để chạy được trên production
INSERT INTO warehouse (code, name, kind, is_work_in_progress, store_id, sort_order, status, approval_status)
SELECT 'WIP_CH1', 'Đang sản xuất', 'INTERNAL', TRUE,
       (SELECT id FROM store WHERE code = 'CH1'),
       COALESCE((SELECT sort_order FROM warehouse WHERE code = 'COLD'), 40) + 5, 'ACTIVE', 'APPROVED'
 WHERE EXISTS (SELECT 1 FROM store WHERE code = 'CH1')
   AND NOT EXISTS (SELECT 1 FROM warehouse WHERE code = 'WIP_CH1');

-- 3. Phiên ngày (mỗi cửa hàng một phiên mỗi ngày)
CREATE TABLE production_session (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id         UUID        NOT NULL REFERENCES store(id),
    session_date     DATE        NOT NULL,
    session_status   VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (session_status IN ('OPEN', 'CLOSED')),
    opened_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    opened_by        VARCHAR(100),
    closed_at        TIMESTAMPTZ,
    closed_by        VARCHAR(100),
    -- BaseEntity audit
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    approval_status  VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    approved_at      TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    rejected_reason  VARCHAR(500),
    CONSTRAINT uq_production_session_store_date UNIQUE (store_id, session_date)
);

-- 4. Phiếu kho: phiếu SX liên quan, lý do lấy, phiên; dòng: kho nguồn thiếu lúc tạo
ALTER TABLE inventory_request
    ADD COLUMN production_request_id UUID REFERENCES production_request(id),
    ADD COLUMN pick_reason           VARCHAR(30),
    ADD COLUMN session_id            UUID REFERENCES production_session(id);
ALTER TABLE inventory_request_line ADD COLUMN stock_short BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_inv_req_production_request ON inventory_request (production_request_id) WHERE production_request_id IS NOT NULL;
CREATE INDEX idx_inv_req_session ON inventory_request (session_id) WHERE session_id IS NOT NULL;
CREATE INDEX idx_inv_req_type_date_pick ON inventory_request (request_type, request_date) WHERE request_type IN ('PICK', 'RETURN', 'WRITE_OFF');

-- 5. Lô giữ cho phiếu SX (chỉ lô ở kho bảng tạm có giá trị)
ALTER TABLE stock_lot ADD COLUMN reserved_for_request_id UUID REFERENCES production_request(id);
CREATE INDEX idx_stock_lot_wh_reserved ON stock_lot (warehouse_id, reserved_for_request_id);

-- 6. Lý do lấy
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES ('PICK_REASON', 'PLAN',           'Theo kế hoạch / phiếu SX',     1, 'ACTIVE', 'APPROVED'),
       ('PICK_REASON', 'MANUAL',         'Bếp tạo',                      2, 'ACTIVE', 'APPROVED'),
       ('PICK_REASON', 'EXTRA_DAMAGED',  'Lấy thêm: NL hư hỏng',         3, 'ACTIVE', 'APPROVED'),
       ('PICK_REASON', 'EXTRA_CUSTOMER', 'Lấy thêm: khách thêm yêu cầu', 4, 'ACTIVE', 'APPROVED'),
       ('PICK_REASON', 'EXTRA_OTHER',    'Lấy thêm: khác',               5, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- 7. Quyền "Lấy thêm NL" (mặc định Quản lý bếp; Admin tạo vai trò Tổ trưởng rồi cấp)
UPDATE screen_registry SET available_actions = available_actions || ',EXTRA_MATERIAL'
 WHERE code = 'PROD_REQUESTS' AND available_actions NOT LIKE '%EXTRA_MATERIAL%';
INSERT INTO role_permission (role_id, screen_code, action_code)
SELECT r.id, 'PROD_REQUESTS', 'EXTRA_MATERIAL' FROM user_role r WHERE r.code = 'KITCHEN_MANAGER'
ON CONFLICT DO NOTHING;

-- 8. Kho bảng tạm là kho của bếp, gán như mọi kho khác (không miễn quy tắc vào trang).
-- Mặc định vai trò bếp: thêm WIP_CH1 vào danh sách mã kho mặc định
UPDATE user_role
   SET default_warehouse_codes = CASE WHEN default_warehouse_codes IS NULL OR default_warehouse_codes = ''
                                      THEN 'WIP_CH1' ELSE default_warehouse_codes || ',WIP_CH1' END
 WHERE code IN ('KITCHEN_MANAGER', 'KITCHEN_STAFF')
   AND EXISTS (SELECT 1 FROM warehouse WHERE code = 'WIP_CH1')
   AND (',' || COALESCE(default_warehouse_codes, '') || ',') NOT LIKE '%,WIP_CH1,%';
-- Mọi user đang được gán kho Bánh chờ giao (theo cờ, không theo mã) được gán kho bảng tạm cùng cửa hàng
INSERT INTO user_warehouse (user_id, warehouse_id)
SELECT DISTINCT uw.user_id, wip.id
  FROM user_warehouse uw
  JOIN warehouse hold ON hold.id = uw.warehouse_id AND hold.is_delivery_hold
  JOIN warehouse wip  ON wip.is_work_in_progress AND wip.store_id = hold.store_id
ON CONFLICT DO NOTHING;
