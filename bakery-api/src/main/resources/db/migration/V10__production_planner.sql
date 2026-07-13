-- V10: Production Planner — item_group, threshold rules, production groups, production plans
-- ──────────────────────────────────────────────────────────────────────────────────────────

-- 1. Item Group (phòng/bộ phận sản xuất): PL=Phòng Lạnh, PK=Phòng Kem, BMN=Bánh Mì Ngọt...
CREATE TABLE item_group (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Gán item vào item_group
ALTER TABLE item ADD COLUMN item_group_id UUID REFERENCES item_group(id);

-- 3. Threshold rules — Pattern 1: SIMPLE
--    Mỗi sản phẩm có thể có nhiều rule theo dayType + sort_order
CREATE TABLE production_threshold_rule (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id          UUID NOT NULL REFERENCES item(id) ON DELETE CASCADE,
    day_type         VARCHAR(10) NOT NULL,       -- WEEKDAY | WEEKEND
    sort_order       INT NOT NULL DEFAULT 1,
    condition_type   VARCHAR(10) NOT NULL,       -- COUNT | PERCENT
    condition_value  NUMERIC(10,2) NOT NULL,     -- ngưỡng tồn kho (số lượng hoặc %)
    produce_qty      INT NOT NULL,               -- số lượng cần làm thêm khi khớp rule
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (item_id, day_type, sort_order)
);

-- 4. Production Group — Pattern 2 (FREE_GROUP) và Pattern 3 (BATCH_FORMULA)
CREATE TABLE production_group (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    group_type          VARCHAR(20) NOT NULL,    -- FREE_GROUP | BATCH_FORMULA
    item_group_id       UUID REFERENCES item_group(id),
    -- FREE_GROUP: tổng target theo dayType
    target_weekday      INT,
    target_weekend      INT,
    -- BATCH_FORMULA: trọng lượng 1 cối (gram)
    batch_weight_grams  INT,
    note                TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. Các sản phẩm thuộc production_group
CREATE TABLE production_group_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL REFERENCES production_group(id) ON DELETE CASCADE,
    item_id         UUID NOT NULL REFERENCES item(id) ON DELETE CASCADE,
    grams_per_unit  NUMERIC(8,2),               -- gram/cái — chỉ dùng cho BATCH_FORMULA
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (group_id, item_id)
);

-- 6. Kế hoạch sản xuất ngày mai (được tự động tạo khi DailyReport finalize)
CREATE TABLE production_plan (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_date       DATE NOT NULL UNIQUE,        -- ngày sản xuất (ngày mai)
    day_type        VARCHAR(10) NOT NULL,        -- WEEKDAY | WEEKEND
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT | APPROVED | REJECTED
    generated_from  UUID REFERENCES daily_report(id),     -- DailyReport trigger tạo plan này
    approved_by     UUID,
    approved_at     TIMESTAMPTZ,
    reject_reason   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 7. Dòng kế hoạch — mỗi dòng là 1 item với số lượng gợi ý và số lượng đã điều chỉnh
CREATE TABLE production_plan_line (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id          UUID NOT NULL REFERENCES production_plan(id) ON DELETE CASCADE,
    item_id          UUID NOT NULL REFERENCES item(id),
    -- Pattern
    plan_type        VARCHAR(20) NOT NULL,       -- SIMPLE | FREE_GROUP | BATCH_FORMULA
    group_id         UUID REFERENCES production_group(id),
    -- Tồn kho lúc tạo plan
    qty_remaining    NUMERIC(10,2),
    -- Số lượng gợi ý (do hệ thống tính)
    suggested_qty    INT,
    -- Số lượng đã điều chỉnh (manager sửa)
    adjusted_qty     INT,
    -- Thông tin batch (BATCH_FORMULA)
    grams_per_unit   NUMERIC(8,2),
    -- Ghi chú rule đã match (debug/audit)
    rule_note        TEXT,
    sort_order       INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (plan_id, item_id)
);

-- Indexes
CREATE INDEX idx_item_item_group ON item(item_group_id);
CREATE INDEX idx_threshold_rule_item ON production_threshold_rule(item_id, day_type);
CREATE INDEX idx_prod_group_item_group ON production_group(item_group_id);
CREATE INDEX idx_prod_group_item ON production_group_item(group_id);
CREATE INDEX idx_prod_group_item_item ON production_group_item(item_id);
CREATE INDEX idx_production_plan_date ON production_plan(plan_date);
CREATE INDEX idx_production_plan_line_plan ON production_plan_line(plan_id);
CREATE INDEX idx_production_plan_line_group ON production_plan_line(group_id);
