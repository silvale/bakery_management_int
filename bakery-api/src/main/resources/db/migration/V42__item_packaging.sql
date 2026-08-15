-- V42: ItemPackaging — hỗ trợ nhiều đóng gói per item
-- Drop base_unit + unit_size (không cần nữa, thay bằng item_packaging)

-- ── 1. item_packaging ──────────────────────────────────────────────────────
CREATE TABLE item_packaging (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id         UUID            NOT NULL REFERENCES item(id),
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    qty_per_pack    NUMERIC(15, 4)  NOT NULL,   -- số lượng tính theo item.unit
    is_default      BOOLEAN         NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (item_id, code)
);

CREATE INDEX idx_item_packaging_item_id ON item_packaging(item_id);

-- ── 2. inventory_request_line — thêm packaging ────────────────────────────
ALTER TABLE inventory_request_line
    ADD COLUMN IF NOT EXISTS packaging_id    UUID REFERENCES item_packaging(id),
    ADD COLUMN IF NOT EXISTS purchase_qty    NUMERIC(15, 4);

-- ── 3. stock_lot — thêm packaging ─────────────────────────────────────────
ALTER TABLE stock_lot
    ADD COLUMN IF NOT EXISTS packaging_id       UUID REFERENCES item_packaging(id),
    ADD COLUMN IF NOT EXISTS qty_received_pack  NUMERIC(15, 4);  -- số bao/thùng ban đầu

-- ── 4. item — drop base_unit + unit_size ──────────────────────────────────
ALTER TABLE item
    DROP COLUMN IF EXISTS base_unit,
    DROP COLUMN IF EXISTS unit_size;
