-- V63: bảng tạm "Đang sản xuất" cho phiếu SX bán thành phẩm (20/BE-1)
-- NL rời kho là trừ tồn ngay (stock_movement OUT), chuyển vào production_material gắn với phiếu SX.
-- Mỗi dòng = một lô nguồn đã lấy (giữ giá vốn lô, trả lại đúng lô).
-- Còn trong bảng tạm = qty_picked - qty_consumed - qty_returned - qty_written_off.

CREATE TABLE production_material (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    production_request_id uuid NOT NULL REFERENCES production_request(id),
    item_id               uuid NOT NULL REFERENCES item(id),
    source_lot_id         uuid NOT NULL REFERENCES stock_lot(id),
    source_warehouse_id   uuid NOT NULL REFERENCES warehouse(id),
    unit_cost             numeric(15,4),
    qty_picked            numeric(15,4) NOT NULL CHECK (qty_picked > 0),
    qty_consumed          numeric(15,4) NOT NULL DEFAULT 0,
    qty_returned          numeric(15,4) NOT NULL DEFAULT 0,
    qty_written_off       numeric(15,4) NOT NULL DEFAULT 0,
    picked_by             varchar(100) NOT NULL,
    picked_at             timestamptz  NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100), updated_by varchar(100),
    CHECK (qty_consumed + qty_returned + qty_written_off <= qty_picked)
);
CREATE INDEX idx_prod_material_request ON production_material(production_request_id);
CREATE INDEX idx_prod_material_item    ON production_material(item_id);

-- Ghi hao hụt: bảng riêng để có lý do và người ghi
CREATE TABLE production_material_writeoff (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    production_material_id uuid NOT NULL REFERENCES production_material(id),
    qty numeric(15,4) NOT NULL CHECK (qty > 0),
    reason_code varchar(30) NOT NULL,
    note varchar(500),
    amount numeric(15,2),
    created_at timestamptz NOT NULL DEFAULT now(), created_by varchar(100) NOT NULL
);
CREATE INDEX idx_prod_material_writeoff_material ON production_material_writeoff(production_material_id);

-- Bếp trưởng xác nhận đóng phiếu (20/D7)
ALTER TABLE production_request ADD COLUMN closed_at timestamptz;
ALTER TABLE production_request ADD COLUMN closed_by varchar(100);
