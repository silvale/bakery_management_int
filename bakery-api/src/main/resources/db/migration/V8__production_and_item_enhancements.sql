-- =============================================================
-- V8: Item enhancements + Production request refactor
-- =============================================================

-- ── Item: fixed-unit support ──────────────────────────────────
ALTER TABLE item
    ADD COLUMN IF NOT EXISTS unit_size      DECIMAL(15,4),   -- kích thước đơn vị tối thiểu (e.g. 5.0 cho bơ 5kg/cục)
    ADD COLUMN IF NOT EXISTS is_splittable  BOOLEAN NOT NULL DEFAULT TRUE; -- false = không xuất lẻ từ kho tổng

-- ── Recipe: versioning lineage ────────────────────────────────
ALTER TABLE recipe
    ADD COLUMN IF NOT EXISTS parent_recipe_id UUID REFERENCES recipe (id);

COMMENT ON COLUMN recipe.parent_recipe_id IS 'NULL = công thức gốc (base). Non-null = bản sao custom từ recipe gốc.';

-- ── ProductionRequest: refactor ───────────────────────────────
ALTER TABLE production_request
    ADD COLUMN IF NOT EXISTS code             VARCHAR(50) UNIQUE,
    ADD COLUMN IF NOT EXISTS production_type  VARCHAR(20) NOT NULL DEFAULT 'DAILY', -- DAILY | ORDER
    ADD COLUMN IF NOT EXISTS production_date  DATE;

-- Migrate request_date → production_date nếu chưa có data
UPDATE production_request SET production_date = request_date WHERE production_date IS NULL;

-- ── ProductionRequestLine ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS production_request_line (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    production_request_id  UUID          NOT NULL REFERENCES production_request (id),
    product_id             UUID          NOT NULL REFERENCES item (id),
    recipe_id              UUID          REFERENCES recipe (id),   -- recipe version dùng để tính NL
    planned_qty            DECIMAL(15,4) NOT NULL,
    line_status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING | COMPLETED
    sort_order             INTEGER       NOT NULL DEFAULT 0,
    note                   VARCHAR(500),
    -- audit
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    approval_status  VARCHAR(30)  NOT NULL DEFAULT 'APPROVED'
);

CREATE INDEX IF NOT EXISTS idx_prl_request_id ON production_request_line (production_request_id);
CREATE INDEX IF NOT EXISTS idx_prl_product_id ON production_request_line (product_id);

-- ── DeliveryRecord ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS delivery_record (
    id                          UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    production_request_line_id  UUID          NOT NULL UNIQUE REFERENCES production_request_line (id),
    qty_produced                DECIMAL(15,4) NOT NULL,   -- bếp nhập lúc bấm Completed
    qty_received                DECIMAL(15,4),            -- shop confirm
    discrepancy                 DECIMAL(15,4),            -- qty_produced - qty_received
    delivery_status             VARCHAR(20)   NOT NULL DEFAULT 'READY', -- READY | CONFIRMED
    confirmed_at                TIMESTAMPTZ,
    confirmed_by                VARCHAR(100),
    note                        VARCHAR(500),
    -- audit
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    approval_status  VARCHAR(30)  NOT NULL DEFAULT 'APPROVED'
);

CREATE INDEX IF NOT EXISTS idx_dr_line_id ON delivery_record (production_request_line_id);
