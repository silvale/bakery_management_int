-- V64 (22/BE-1): kho cấu hình theo việc, thay cho so mã / loại kho trong code.
-- Chưa bỏ warehouse_type (V65 bỏ khi mọi luồng đã đọc theo cấu hình).

ALTER TABLE warehouse
    ADD COLUMN kind                  VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN can_receive_purchase  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN is_production_source  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN can_store_semi        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN is_delivery_hold      BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN is_sales_point        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN can_export_external   BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN sort_order            INT         NOT NULL DEFAULT 0;

-- 4 kho hiện có (thiết kế 22 §4.2). Tìm theo mã chỉ ở migration này; code ứng dụng không so mã.
UPDATE warehouse SET can_receive_purchase = TRUE, is_production_source = TRUE, can_export_external = TRUE, sort_order = 20
 WHERE code = 'MAIN';
UPDATE warehouse SET name = 'Phòng Bánh Mì', is_production_source = TRUE, can_store_semi = TRUE, is_delivery_hold = TRUE, sort_order = 30
 WHERE code = 'KITCHEN';
UPDATE warehouse SET is_production_source = TRUE, can_store_semi = TRUE, sort_order = 40
 WHERE code = 'COLD';
UPDATE warehouse SET name = 'Bán Hàng', is_sales_point = TRUE, sort_order = 50
 WHERE code = 'SHOP';

-- Chỉ một kho bán hàng, một kho bánh chờ giao (DB tự chặn)
CREATE UNIQUE INDEX uq_warehouse_delivery_hold ON warehouse (is_delivery_hold) WHERE is_delivery_hold;
CREATE UNIQUE INDEX uq_warehouse_sales_point   ON warehouse (is_sales_point)   WHERE is_sales_point;

ALTER TABLE warehouse ADD CONSTRAINT ck_warehouse_kind CHECK (kind IN ('INTERNAL', 'EXTERNAL'));
-- Nơi nhận ngoài không bao giờ có tồn nên không làm việc gì
ALTER TABLE warehouse ADD CONSTRAINT ck_warehouse_external_no_roles CHECK (
    kind = 'INTERNAL' OR NOT (can_receive_purchase OR is_production_source OR can_store_semi
                              OR is_delivery_hold OR is_sales_point OR can_export_external));

-- Màn Quản lý kho đếm lô còn tồn / phiếu theo kho; stock_lot và inventory_request chưa có index mở đầu bằng kho
CREATE INDEX idx_stock_lot_warehouse_remaining ON stock_lot (warehouse_id) WHERE qty_remaining > 0;
CREATE INDEX idx_stock_lot_warehouse           ON stock_lot (warehouse_id);
CREATE INDEX idx_inv_req_source_warehouse      ON inventory_request (source_warehouse_id);
CREATE INDEX idx_inv_req_target_warehouse      ON inventory_request (target_warehouse_id);
