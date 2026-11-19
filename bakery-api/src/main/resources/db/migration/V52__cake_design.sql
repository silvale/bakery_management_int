-- V52: Mẫu bánh kem (cake design) — danh mục mẫu, phụ kiện theo mẫu, mẫu theo giao nhận và báo cáo ngày.
-- Tồn kho (stock_lot) không tách theo mẫu (D10).

CREATE TABLE IF NOT EXISTS cake_design (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code             VARCHAR(30)  NOT NULL UNIQUE,
    name             VARCHAR(150) NOT NULL,
    product_id       UUID         NOT NULL REFERENCES item (id),
    group_code       VARCHAR(30),
    image_url        VARCHAR(500),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0,
    -- audit (BaseEntity)
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    approval_status  VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    approved_at      TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    rejected_reason  VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_cake_design_product ON cake_design (product_id);

CREATE TABLE IF NOT EXISTS cake_design_accessory (
    id             UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    cake_design_id UUID          NOT NULL REFERENCES cake_design (id) ON DELETE CASCADE,
    item_id        UUID          NOT NULL REFERENCES item (id),
    qty            NUMERIC(15,4) NOT NULL CHECK (qty > 0),
    UNIQUE (cake_design_id, item_id)
);
CREATE INDEX IF NOT EXISTS idx_cake_design_accessory_item ON cake_design_accessory (item_id);

CREATE TABLE IF NOT EXISTS delivery_record_design (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    delivery_record_id UUID          NOT NULL REFERENCES delivery_record (id),
    cake_design_id     UUID          NOT NULL REFERENCES cake_design (id),
    qty_produced       NUMERIC(15,4) NOT NULL DEFAULT 0,
    qty_received       NUMERIC(15,4),
    UNIQUE (delivery_record_id, cake_design_id)
);
CREATE INDEX IF NOT EXISTS idx_dr_design_design ON delivery_record_design (cake_design_id);

ALTER TABLE delivery_record ADD COLUMN IF NOT EXISTS design_mismatch BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS daily_report_line_design (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    line_id              UUID          NOT NULL REFERENCES daily_report_line (id) ON DELETE CASCADE,
    cake_design_id       UUID          NOT NULL REFERENCES cake_design (id),
    qty_opening          NUMERIC(10,3),
    qty_received         NUMERIC(10,3),
    qty_remaining_actual NUMERIC(10,3),
    qty_cancelled        NUMERIC(10,3),
    qty_sold_implied     NUMERIC(10,3),
    UNIQUE (line_id, cake_design_id)
);
CREATE INDEX IF NOT EXISTS idx_drl_design_design ON daily_report_line_design (cake_design_id);

-- Nến tặng kèm bánh kem (D8). item là @Audited nên thêm cả vào item_his.
ALTER TABLE item     ADD COLUMN IF NOT EXISTS gift_with_cake BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE item_his ADD COLUMN IF NOT EXISTS gift_with_cake BOOLEAN;

-- Nhóm mẫu
INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES ('CAKE_DESIGN_GROUP', 'BOY',  'Bé trai', 1, 'ACTIVE', 'APPROVED'),
       ('CAKE_DESIGN_GROUP', 'GIRL', 'Bé gái',  2, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;

-- Quyền màn hình
INSERT INTO screen_registry (code, name, available_actions, sort_order)
VALUES ('CAKE_DESIGN', 'Mẫu bánh kem', 'VIEW,CREATE,UPDATE,DELETE',
        (SELECT COALESCE(MAX(sort_order), 0) + 10 FROM screen_registry))
ON CONFLICT (code) DO NOTHING;
