-- =============================================================================
-- V15: Production Planning Engine + Custom Order Addon
-- Idempotent — safe to re-run
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. DROP deprecated tables
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS semi_product_cost CASCADE;
DROP TABLE IF EXISTS ingredient_group CASCADE;

-- Bỏ cột group_code khỏi ingredient (nếu còn tồn tại)
DO $$ BEGIN
    ALTER TABLE ingredient DROP COLUMN group_code;
EXCEPTION WHEN undefined_column THEN
    RAISE NOTICE 'SKIP: ingredient.group_code không tồn tại';
END $$;

-- ---------------------------------------------------------------------------
-- 2. production_group — nhóm sản phẩm dùng chung phôi (GROUP_SUBTRACT)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS production_group (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_code            VARCHAR(50)  NOT NULL,
    group_name            VARCHAR(100) NOT NULL,
    -- FK mềm sang semi_product (phôi dùng chung)
    main_semi_product_code VARCHAR(50) NOT NULL,
    -- Số lượng target theo ngày (weekday / weekend)
    weekday_target        NUMERIC(12,3) NOT NULL DEFAULT 0,
    weekend_target        NUMERIC(12,3) NOT NULL DEFAULT 0,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_production_group_code UNIQUE (group_code)
);

-- ---------------------------------------------------------------------------
-- 3. production_group_member — thành viên trong nhóm
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS production_group_member (
    group_id   UUID NOT NULL REFERENCES production_group(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id),
    PRIMARY KEY (group_id, product_id)
);

-- ---------------------------------------------------------------------------
-- 4. batch_formula_config — cấu hình LAN_XUAT / LAN_MAM
--
--  formula_type = 'LAN_MAM'   → tính số mâm (Bento, mỗi mâm tối đa N cái)
--  formula_type = 'LAN_XUAT'  → tính số cối bông lan theo ma trận size
--
--  input_variables JSONB:
--    LAN_MAM   : {"multiplier": 1.0}
--    LAN_XUAT  : {"multiplier": 1.5}   ← hệ số (PK + PL) × 1.5
--
--  output_yield_mapping JSONB:
--    LAN_MAM   : {"PK-BENTO": 12}       ← 1 mâm = tối đa 12 cái
--    LAN_XUAT  : {"PK-SIZE-12": 3, "PK-SIZE-14": 3, "PK-SIZE-18": 3, "PK-SIZE-20": 2}
--                  Khi ra khuôn bắp miếng thêm: "BMM-BANH-BAP-MIENG": 8, "PK-SIZE-20" = 1
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS batch_formula_config (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    formula_code          VARCHAR(50)  NOT NULL,
    formula_name          VARCHAR(100) NOT NULL,
    formula_type          VARCHAR(20)  NOT NULL,          -- 'LAN_MAM' | 'LAN_XUAT'
    -- Prefix sản phẩm đầu vào (dùng để lấy demand + daily_inventory)
    target_product_prefix VARCHAR(20)  NOT NULL,
    -- Số lượng tối đa / chuẩn mỗi cối/mâm (để tính ceil)
    max_qty_per_batch     INT          NOT NULL DEFAULT 1,
    -- Hệ số và tham số động
    input_variables       JSONB,
    -- Ma trận đầu ra: product_code → số lượng per cối/mâm
    output_yield_mapping  JSONB        NOT NULL,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_batch_formula_code UNIQUE (formula_code)
);

-- ---------------------------------------------------------------------------
-- 5. customer_order_line_addon — topping/NL add-on đặc thù cho SHEET_CAKE
--
--  Mỗi line của đơn SHEET_CAKE có thể có nhiều addon:
--    - ingredient_id != NULL  → thêm NL thô (VD: 50g dâu tây)
--    - product_id != NULL     → thêm sản phẩm phụ kiện ACCESSORY
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_order_line_addon (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    line_id       UUID          NOT NULL REFERENCES customer_order_line(id) ON DELETE CASCADE,
    addon_type    VARCHAR(20)   NOT NULL DEFAULT 'INGREDIENT', -- 'INGREDIENT' | 'ACCESSORY'
    ingredient_id UUID          REFERENCES ingredient(id),
    product_id    UUID          REFERENCES product(id),
    qty           NUMERIC(12,3) NOT NULL,
    unit          VARCHAR(20)   NOT NULL DEFAULT 'g',
    note          TEXT,
    created_by    VARCHAR(100)  NOT NULL DEFAULT 'system',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_addon_has_item CHECK (
        (ingredient_id IS NOT NULL AND product_id IS NULL)
        OR (ingredient_id IS NULL AND product_id IS NOT NULL)
    )
);

-- ---------------------------------------------------------------------------
-- 6. Seed data — batch_formula_config
-- ---------------------------------------------------------------------------

-- LAN_MAM: Bento (tối đa 12 cái/mâm)
INSERT INTO batch_formula_config
    (formula_code, formula_name, formula_type, target_product_prefix,
     max_qty_per_batch, input_variables, output_yield_mapping, created_by)
VALUES
    ('FORMULA_BENTO', 'Lan Mâm Bento', 'LAN_MAM', 'PK-BENTO',
     12, '{"multiplier": 1.0}', '{"PK-BENTO": 12}', 'system')
ON CONFLICT (formula_code) DO NOTHING;

-- LAN_XUAT: Bánh Bông Lan — 1 cối ra cả bộ size (cối bắp chuẩn)
INSERT INTO batch_formula_config
    (formula_code, formula_name, formula_type, target_product_prefix,
     max_qty_per_batch, input_variables, output_yield_mapping, created_by)
VALUES
    ('FORMULA_LAN_XUAT', 'Lan Xuất Bông Lan', 'LAN_XUAT', 'PK-SIZE',
     1,
     '{"multiplier": 1.5, "extra_bap_coi": true}',
     '{"PK-SIZE-12": 3, "PK-SIZE-14": 3, "PK-SIZE-18": 3, "PK-SIZE-20": 2}',
     'system')
ON CONFLICT (formula_code) DO NOTHING;

-- LAN_XUAT: Biến thể khuôn S20 + bắp miếng
INSERT INTO batch_formula_config
    (formula_code, formula_name, formula_type, target_product_prefix,
     max_qty_per_batch, input_variables, output_yield_mapping, created_by)
VALUES
    ('FORMULA_LAN_XUAT_BAP_MIENG', 'Lan Xuất + Bắp Miếng', 'LAN_XUAT', 'PK-SIZE',
     1,
     '{"multiplier": 1.5, "extra_bap_coi": false}',
     '{"PK-SIZE-12": 3, "PK-SIZE-14": 3, "PK-SIZE-18": 3, "PK-SIZE-20": 1, "BMM-BANH-BAP-MIENG": 8}',
     'system')
ON CONFLICT (formula_code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7. goods_transfer.transfer_source — phân biệt AUTO_PLAN vs MANUAL
-- ---------------------------------------------------------------------------
DO $$ BEGIN
    ALTER TABLE goods_transfer
        ADD COLUMN transfer_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
    ALTER TABLE goods_transfer
        ADD CONSTRAINT chk_gt_source CHECK (transfer_source IN ('AUTO_PLAN', 'MANUAL'));
EXCEPTION WHEN duplicate_column THEN
    RAISE NOTICE 'SKIP: goods_transfer.transfer_source đã tồn tại';
END $$;

-- Verify
SELECT 'production_group'           AS tbl, COUNT(*) FROM production_group
UNION ALL
SELECT 'production_group_member',          COUNT(*) FROM production_group_member
UNION ALL
SELECT 'batch_formula_config',             COUNT(*) FROM batch_formula_config
UNION ALL
SELECT 'customer_order_line_addon',        COUNT(*) FROM customer_order_line_addon
UNION ALL
SELECT 'goods_transfer (transfer_source)', COUNT(*) FROM goods_transfer;
