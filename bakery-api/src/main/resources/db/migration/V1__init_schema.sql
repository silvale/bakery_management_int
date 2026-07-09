-- =============================================================
-- V1__init_schema.sql
-- Bakery Management System — Initial Schema
-- 17 DB tables | Single Table Inheritance for item
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================
-- AUTH (3 tables)
-- =============================================================

CREATE TABLE IF NOT EXISTS user_role (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
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

CREATE TABLE IF NOT EXISTS user_account (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    username         VARCHAR(100) NOT NULL UNIQUE,
    password_hash    TEXT         NOT NULL,
    full_name        VARCHAR(200),
    role_id          UUID REFERENCES user_role (id),
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

CREATE TABLE IF NOT EXISTS refresh_token (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID         NOT NULL,
    token       VARCHAR(500) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id ON refresh_token (user_id);

-- =============================================================
-- MASTER DATA (4 tables)
-- =============================================================

CREATE TABLE IF NOT EXISTS code_value (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    group_key        VARCHAR(50)  NOT NULL,
    code             VARCHAR(50)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    sort_order       INTEGER,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    approval_status  VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    approved_at      TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    rejected_reason  VARCHAR(500),
    UNIQUE (group_key, code)
);

CREATE INDEX IF NOT EXISTS idx_code_value_group_key ON code_value (group_key);

CREATE TABLE IF NOT EXISTS warehouse (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    name             VARCHAR(200) NOT NULL,
    warehouse_type   VARCHAR(20)  NOT NULL, -- MAIN | BRANCH | KITCHEN
    address          VARCHAR(500),
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

CREATE TABLE IF NOT EXISTS supplier (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    name             VARCHAR(200) NOT NULL,
    contact_name     VARCHAR(200),
    phone            VARCHAR(20),
    email            VARCHAR(100),
    address          VARCHAR(500),
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

-- =============================================================
-- ITEM — Single Table Inheritance (1 table, 3 types)
-- item_type: INGREDIENT | SEMI_PRODUCT | PRODUCT
-- =============================================================

CREATE TABLE IF NOT EXISTS item (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    item_type            VARCHAR(20)    NOT NULL, -- discriminator
    code                 VARCHAR(50)    NOT NULL UNIQUE,
    name                 VARCHAR(200)   NOT NULL,
    unit                 VARCHAR(30)    NOT NULL,
    -- INGREDIENT fields
    ingredient_type      VARCHAR(50),             -- code_value: INGREDIENT_TYPE
    default_supplier_id  UUID REFERENCES supplier (id),
    -- PRODUCT fields
    product_type         VARCHAR(50),             -- code_value: PRODUCT_TYPE
    selling_price        NUMERIC(15, 2),
    -- base
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

CREATE INDEX IF NOT EXISTS idx_item_type ON item (item_type);
CREATE INDEX IF NOT EXISTS idx_item_code ON item (code);

-- =============================================================
-- RECIPE (2 tables)
-- =============================================================

CREATE TABLE IF NOT EXISTS recipe (
    id               UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id       UUID REFERENCES item (id),      -- item_type = PRODUCT
    semi_product_id  UUID REFERENCES item (id),      -- item_type = SEMI_PRODUCT
    version          INTEGER NOT NULL DEFAULT 1,
    is_active        BOOLEAN NOT NULL DEFAULT FALSE,
    note             VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    approval_status  VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    approved_at      TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    rejected_reason  VARCHAR(500),
    CONSTRAINT chk_recipe_target CHECK (
        (product_id IS NOT NULL AND semi_product_id IS NULL) OR
        (product_id IS NULL AND semi_product_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_recipe_product_id      ON recipe (product_id);
CREATE INDEX IF NOT EXISTS idx_recipe_semi_product_id ON recipe (semi_product_id);

CREATE TABLE IF NOT EXISTS recipe_line (
    id          UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    recipe_id   UUID           NOT NULL REFERENCES recipe (id),
    item_id     UUID           NOT NULL REFERENCES item (id),   -- INGREDIENT or SEMI_PRODUCT
    quantity    NUMERIC(15, 4) NOT NULL,
    unit        VARCHAR(30)    NOT NULL,
    sort_order  INTEGER,
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

CREATE INDEX IF NOT EXISTS idx_recipe_line_recipe_id ON recipe_line (recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_line_item_id   ON recipe_line (item_id);

-- =============================================================
-- PRICING (2 tables)
-- =============================================================

CREATE TABLE IF NOT EXISTS ingredient_price (
    id              UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    item_id         UUID           NOT NULL REFERENCES item (id), -- item_type = INGREDIENT
    supplier_id     UUID REFERENCES supplier (id),
    price           NUMERIC(15, 2) NOT NULL,
    effective_date  DATE           NOT NULL,
    lot_ref         VARCHAR(100),
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

CREATE INDEX IF NOT EXISTS idx_ingredient_price_item ON ingredient_price (item_id, effective_date DESC);

CREATE TABLE IF NOT EXISTS product_price (
    id              UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    item_id         UUID           NOT NULL REFERENCES item (id), -- item_type = PRODUCT
    price           NUMERIC(15, 2) NOT NULL,
    effective_date  DATE           NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_product_price_item ON product_price (item_id, effective_date DESC);

-- =============================================================
-- INVENTORY (4 tables)
-- =============================================================

CREATE TABLE IF NOT EXISTS inventory_request (
    id                     UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code                   VARCHAR(50) NOT NULL UNIQUE,         -- tự sinh: PO-YYYYMMDD-NNN
    request_type           VARCHAR(20) NOT NULL,                -- PURCHASE | TRANSFER
    request_date           DATE        NOT NULL,
    expected_delivery_date DATE,
    source_warehouse_id    UUID REFERENCES warehouse (id),      -- null khi PURCHASE
    target_warehouse_id    UUID REFERENCES warehouse (id),
    supplier_id            UUID REFERENCES supplier (id),       -- chỉ khi PURCHASE
    note                   VARCHAR(500),
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

CREATE INDEX IF NOT EXISTS idx_inventory_request_type_date ON inventory_request (request_type, request_date);

CREATE TABLE IF NOT EXISTS inventory_request_line (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    inventory_request_id UUID           NOT NULL REFERENCES inventory_request (id),
    item_id              UUID           NOT NULL REFERENCES item (id),
    quantity             NUMERIC(15, 4) NOT NULL,
    unit                 VARCHAR(30)    NOT NULL,
    unit_cost            NUMERIC(15, 4),                        -- chỉ khi PURCHASE
    sort_order           INTEGER,
    note                 VARCHAR(500),
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

CREATE INDEX IF NOT EXISTS idx_inv_req_line_request_id ON inventory_request_line (inventory_request_id);

CREATE TABLE IF NOT EXISTS stock_lot (
    id             UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    item_id        UUID           NOT NULL REFERENCES item (id),
    supplier_id    UUID REFERENCES supplier (id),
    warehouse_id   UUID REFERENCES warehouse (id),
    qty_initial    NUMERIC(15, 4) NOT NULL,
    qty_remaining  NUMERIC(15, 4) NOT NULL,
    unit_cost      NUMERIC(15, 4) NOT NULL,
    received_date  DATE           NOT NULL,
    expiry_date    DATE,
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

CREATE INDEX IF NOT EXISTS idx_stock_lot_fifo ON stock_lot (item_id, warehouse_id, qty_remaining, received_date);

CREATE TABLE IF NOT EXISTS stock_movement (
    id             UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    lot_id         UUID           NOT NULL REFERENCES stock_lot (id),
    movement_type  VARCHAR(20)    NOT NULL, -- IN | OUT | ADJUST
    qty            NUMERIC(15, 4) NOT NULL,
    ref_id         UUID,                   -- ID phiếu phát sinh
    ref_type       VARCHAR(50),            -- INVENTORY_REQUEST | PRODUCTION_REQUEST
    note           VARCHAR(500),
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

CREATE INDEX IF NOT EXISTS idx_stock_movement_lot ON stock_movement (lot_id);
CREATE INDEX IF NOT EXISTS idx_stock_movement_ref ON stock_movement (ref_id, ref_type);

-- =============================================================
-- PRODUCTION (1 table)
-- =============================================================

CREATE TABLE IF NOT EXISTS production_request (
    id            UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    request_date  DATE    NOT NULL,
    warehouse_id  UUID REFERENCES warehouse (id),
    note          VARCHAR(500),
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

CREATE INDEX IF NOT EXISTS idx_production_request_date ON production_request (request_date, warehouse_id);
