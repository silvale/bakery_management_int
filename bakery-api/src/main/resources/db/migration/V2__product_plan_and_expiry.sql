-- =============================================================
-- V2__product_plan_and_expiry.sql
-- Thêm: product_plan_template, product_plan_template_line,
--        product_expiry_config
-- =============================================================

CREATE TABLE IF NOT EXISTS product_plan_template (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    day_type         VARCHAR(10)  NOT NULL, -- WEEKDAY | WEEKEND
    is_active        BOOLEAN      NOT NULL DEFAULT FALSE,
    description      VARCHAR(500),
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

CREATE INDEX IF NOT EXISTS idx_plan_template_day_type ON product_plan_template (day_type, is_active);

CREATE TABLE IF NOT EXISTS product_plan_template_line (
    id                          UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    template_id                 UUID    NOT NULL REFERENCES product_plan_template (id),
    item_id                     UUID    NOT NULL REFERENCES item (id),  -- item_type = PRODUCT
    qty_target                  INTEGER NOT NULL,
    trigger_threshold_percent   INTEGER NOT NULL,
    batch_size                  INTEGER NOT NULL,
    sort_order                  INTEGER,
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

CREATE INDEX IF NOT EXISTS idx_plan_template_line_template ON product_plan_template_line (template_id);

CREATE TABLE IF NOT EXISTS product_expiry_config (
    id               UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    item_id          UUID    NOT NULL UNIQUE REFERENCES item (id), -- 1 sản phẩm 1 config
    shelf_days       INTEGER NOT NULL,                             -- 0 = hủy trong ngày
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
