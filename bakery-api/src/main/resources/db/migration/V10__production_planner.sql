-- V10: Production Planner — item_group, threshold rules, production groups, production plans
-- ──────────────────────────────────────────────────────────────────────────────────────────
-- BaseEntity columns (required cho mọi bảng extend BaseEntity):
--   id, created_at, updated_at, created_by, updated_by,
--   status, approval_status, approved_at, approved_by, rejected_reason

-- 1. Item Group (phòng/bộ phận sản xuất): PL=Phòng Lạnh, PK=Phòng Kem, BMN=Bánh Mì Ngọt...
CREATE TABLE IF NOT EXISTS item_group (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(20)  NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
    sort_order       INT NOT NULL DEFAULT 0,
    -- BaseEntity audit
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    approval_status  VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    approved_at      TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    rejected_reason  VARCHAR(500)
);

-- 2. Gán item vào item_group
ALTER TABLE item ADD COLUMN IF NOT EXISTS item_group_id UUID REFERENCES item_group(id);

-- 3. Threshold rules — Pattern 1: SIMPLE
CREATE TABLE IF NOT EXISTS production_threshold_rule (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id          UUID NOT NULL REFERENCES item(id) ON DELETE CASCADE,
    day_type         VARCHAR(10) NOT NULL,
    sort_order       INT NOT NULL DEFAULT 1,
    condition_type   VARCHAR(10) NOT NULL,
    condition_value  NUMERIC(10,2) NOT NULL,
    produce_qty      INT NOT NULL,
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
    UNIQUE (item_id, day_type, sort_order)
);

-- 4. Production Group — Pattern 2 (FREE_GROUP) và Pattern 3 (BATCH_FORMULA)
CREATE TABLE IF NOT EXISTS production_group (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    group_type          VARCHAR(20) NOT NULL,
    item_group_id       UUID REFERENCES item_group(id),
    target_weekday      INT,
    target_weekend      INT,
    batch_weight_grams  INT,
    note                TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    -- BaseEntity audit
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    approval_status     VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    approved_at         TIMESTAMPTZ,
    approved_by         VARCHAR(100),
    rejected_reason     VARCHAR(500)
);

-- 5. Các sản phẩm thuộc production_group
CREATE TABLE IF NOT EXISTS production_group_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL REFERENCES production_group(id) ON DELETE CASCADE,
    item_id         UUID NOT NULL REFERENCES item(id) ON DELETE CASCADE,
    grams_per_unit  NUMERIC(8,2),
    sort_order      INT NOT NULL DEFAULT 0,
    -- BaseEntity audit
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    approval_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    approved_at     TIMESTAMPTZ,
    approved_by     VARCHAR(100),
    rejected_reason VARCHAR(500),
    UNIQUE (group_id, item_id)
);

-- 6. Kế hoạch sản xuất ngày mai
CREATE TABLE IF NOT EXISTS production_plan (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_date       DATE NOT NULL UNIQUE,
    day_type        VARCHAR(10) NOT NULL,
    generated_from  UUID REFERENCES daily_report(id),
    -- BaseEntity audit (approval_status = DRAFT | APPROVED | REJECTED)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    approval_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    approved_at     TIMESTAMPTZ,
    approved_by     VARCHAR(100),
    rejected_reason VARCHAR(500)
);

-- 7. Dòng kế hoạch
CREATE TABLE IF NOT EXISTS production_plan_line (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id          UUID NOT NULL REFERENCES production_plan(id) ON DELETE CASCADE,
    item_id          UUID NOT NULL REFERENCES item(id),
    plan_type        VARCHAR(20) NOT NULL,
    group_id         UUID REFERENCES production_group(id),
    qty_remaining    NUMERIC(10,2),
    suggested_qty    INT,
    adjusted_qty     INT,
    grams_per_unit   NUMERIC(8,2),
    rule_note        TEXT,
    sort_order       INT NOT NULL DEFAULT 0,
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
    UNIQUE (plan_id, item_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_item_item_group ON item(item_group_id);
CREATE INDEX IF NOT EXISTS idx_threshold_rule_item ON production_threshold_rule(item_id, day_type);
CREATE INDEX IF NOT EXISTS idx_prod_group_item_group ON production_group(item_group_id);
CREATE INDEX IF NOT EXISTS idx_prod_group_item ON production_group_item(group_id);
CREATE INDEX IF NOT EXISTS idx_prod_group_item_item ON production_group_item(item_id);
CREATE INDEX IF NOT EXISTS idx_production_plan_date ON production_plan(plan_date);
CREATE INDEX IF NOT EXISTS idx_production_plan_line_plan ON production_plan_line(plan_id);
CREATE INDEX IF NOT EXISTS idx_production_plan_line_group ON production_plan_line(group_id);
