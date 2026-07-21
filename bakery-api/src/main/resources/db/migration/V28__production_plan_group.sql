-- V28: ProductionPlanGroup + cấu hình item trong group (config_type, default_qty_per_batch)
-- production_plan_group: lưu planned_qty cho từng group trong kế hoạch SX
--   FREE_GROUP  → planned_qty = số sản phẩm mục tiêu (đã resolve weekday/weekend)
--   BATCH_FORMULA → planned_qty = số cối (num_batches), do admin nhập

CREATE TABLE production_plan_group (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id             UUID         NOT NULL REFERENCES production_plan(id),
    group_id            UUID         NOT NULL REFERENCES production_group(id),
    planned_qty         INTEGER      NOT NULL DEFAULT 1,
    note                TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (plan_id, group_id)
);

COMMENT ON TABLE  production_plan_group                IS 'Số lượng kế hoạch (cối hoặc target qty) cho từng group trong plan.';
COMMENT ON COLUMN production_plan_group.planned_qty    IS 'FREE_GROUP: tổng sản phẩm mục tiêu; BATCH_FORMULA: số cối (num_batches).';

-- production_group_item: thêm cấu hình cách tính default qty mỗi cối
ALTER TABLE production_group_item
    ADD COLUMN config_type           VARCHAR(20) NOT NULL DEFAULT 'BY_WEIGHT',
    ADD COLUMN default_qty_per_batch INTEGER;

COMMENT ON COLUMN production_group_item.config_type           IS 'BY_WEIGHT: default qty tính từ batch_weight ÷ grams_per_unit; BY_COUNT: admin set cứng.';
COMMENT ON COLUMN production_group_item.default_qty_per_batch IS 'Số lượng default mỗi cối — dùng để pre-fill kế hoạch khi admin nhập num_batches.';
