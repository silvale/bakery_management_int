-- =============================================================
-- 01_init.sql  —  Final consolidated DDL (V1 → V15)
-- Thứ tự tạo bảng đã sắp xếp đúng theo dependency:
--   1. Master data
--   2. Config / Pricing
--   3. Recipe
--   4. Product mapping
--   5. Purchasing
--   6. Ingredient stock (summary)
--   7. GOODS TRANSFER core  ← PHẢI trước ingredient_stock_lot
--   8. Ingredient stock lots (references goods_transfer)
--   9. Product stock lots
--  10. Production (template, group, batch formula)
--  11. Stock transfer
--  12. Goods transfer continuation (allocation, return)
--  13. Daily ops / Reconcile
--  14. Inventory write-off / adjustment / movement
--  15. Supplier return
--  16. Customer orders + addon
--  17. Batch / logs
--  18. Admin framework
--  19. Auth / RBAC
--  20. Activity log
--
-- V15 changes:
--   - REMOVED: ingredient_group, semi_product_cost
--   - ADDED:   production_group, production_group_member
--   - ADDED:   batch_formula_config
--   - ADDED:   customer_order_line_addon
-- =============================================================

-- ---------------------------------------------------------------------------
-- 1. MASTER DATA
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS branch (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(20)   NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    address       TEXT,
    is_main       BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    branch_type   VARCHAR(20)   NOT NULL DEFAULT 'SHOP',
    entity_status entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(100)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100),
    updated_at    TIMESTAMPTZ,
    approved_by   VARCHAR(100),
    approved_at   TIMESTAMPTZ,
    CONSTRAINT uq_branch_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS product (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(50)   NOT NULL,
    name                VARCHAR(200)  NOT NULL,
    product_type        product_type  NOT NULL,
    unit                VARCHAR(20)   NOT NULL,
    tolerance_rate      NUMERIC(5,4)  NOT NULL DEFAULT 0,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    purchase_unit       VARCHAR(50),
    units_per_purchase  NUMERIC(12,4),
    sell_unit           VARCHAR(50),
    entity_status       entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(100)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    approved_by         VARCHAR(100),
    approved_at         TIMESTAMPTZ,
    CONSTRAINT uq_product_code UNIQUE (code)
);

-- ingredient_group REMOVED (V15) — phân nhóm qua prefix code (ING-BOT-*, ING-DUONG-*)

CREATE TABLE IF NOT EXISTS ingredient (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(50)   NOT NULL,
    name                VARCHAR(200)  NOT NULL,
    base_unit           base_unit     NOT NULL DEFAULT 'GRAM',
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    packaging_unit      VARCHAR(30),
    packaging_qty       NUMERIC(18,4),
    is_whole_unit_only  BOOLEAN       NOT NULL DEFAULT FALSE,
    entity_status       entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(100)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    approved_by         VARCHAR(100),
    approved_at         TIMESTAMPTZ,
    CONSTRAINT uq_ingredient_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS supplier (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(20)   NOT NULL,
    name          VARCHAR(200)  NOT NULL,
    address       TEXT,
    phone         VARCHAR(20),
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    entity_status entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(100)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100),
    updated_at    TIMESTAMPTZ,
    approved_by   VARCHAR(100),
    approved_at   TIMESTAMPTZ,
    CONSTRAINT uq_supplier_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS semi_product (
    id              UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)        NOT NULL,
    name            VARCHAR(200)       NOT NULL,
    type            semi_product_type  NOT NULL,
    total_yield_kg  NUMERIC(12,4),
    is_active       BOOLEAN            NOT NULL DEFAULT TRUE,
    entity_status   entity_status      NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(100)       NOT NULL,
    created_at      TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMPTZ,
    CONSTRAINT uq_semi_product_code UNIQUE (code)
);

-- ---------------------------------------------------------------------------
-- 2. CONFIG / PRICING
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS product_expiry_config (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID          NOT NULL REFERENCES product(id),
    shelf_days    INTEGER       NOT NULL DEFAULT 1,
    note          TEXT,
    entity_status entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(100)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT uq_product_expiry_config_product UNIQUE (product_id)
);

CREATE TABLE IF NOT EXISTS product_prefix (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    prefix      VARCHAR(20)  NOT NULL,
    description VARCHAR(200),
    product_id  UUID         REFERENCES product(id),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    note        TEXT,
    created_by  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT uq_product_prefix UNIQUE (prefix)
);

CREATE TABLE IF NOT EXISTS ingredient_price (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ingredient_id  UUID          NOT NULL REFERENCES ingredient(id),
    price_per_kg   NUMERIC(18,4) NOT NULL,
    version        INTEGER       NOT NULL,
    effective_date DATE          NOT NULL,
    note           TEXT,
    entity_status  entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by     VARCHAR(100)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ,
    approved_by    VARCHAR(100),
    approved_at    TIMESTAMPTZ,
    CONSTRAINT uq_ingredient_price_version UNIQUE (ingredient_id, version)
);

CREATE TABLE IF NOT EXISTS unit_conversion (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ingredient_id       UUID          NOT NULL REFERENCES ingredient(id),
    purchase_unit       VARCHAR(50)   NOT NULL,
    purchase_unit_label VARCHAR(100),
    base_quantity       NUMERIC(18,6) NOT NULL,
    is_default          BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    entity_status       entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(100)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uq_unit_conversion_ingredient_unit UNIQUE (ingredient_id, purchase_unit)
);

-- semi_product_cost REMOVED (V15) — cost tính on-the-fly trong CostCalculationService

CREATE TABLE IF NOT EXISTS product_price (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID          NOT NULL REFERENCES product(id),
    price           NUMERIC(18,2) NOT NULL CHECK (price > 0),
    version         INTEGER       NOT NULL,
    effective_date  DATE          NOT NULL,
    note            TEXT,
    entity_status   entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(100)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMPTZ,
    CONSTRAINT uq_product_price_version UNIQUE (product_id, version)
);

CREATE INDEX IF NOT EXISTS idx_product_price_product_date
    ON product_price (product_id, effective_date DESC);

-- ---------------------------------------------------------------------------
-- 3. RECIPE
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS recipe_line_semi (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    semi_product_id  UUID          NOT NULL REFERENCES semi_product(id),
    ingredient_id    UUID          NOT NULL REFERENCES ingredient(id),
    qty_in_batch     NUMERIC(12,4) NOT NULL,
    unit             VARCHAR(20)   NOT NULL DEFAULT 'KG',
    note             TEXT,
    entity_status    entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by       VARCHAR(100)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    approved_at      TIMESTAMPTZ,
    CONSTRAINT uq_recipe_line_semi UNIQUE (semi_product_id, ingredient_id)
);

CREATE TABLE IF NOT EXISTS recipe (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id       UUID          NOT NULL REFERENCES product(id),
    version          INTEGER       NOT NULL,
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    effective_date   DATE,
    note             TEXT,
    recipe_type      VARCHAR(10)   NOT NULL DEFAULT 'BASE',
    base_recipe_id   UUID          REFERENCES recipe(id),
    is_custom        BOOLEAN       NOT NULL DEFAULT FALSE,
    parent_recipe_id UUID          REFERENCES recipe(id),
    reason           TEXT,
    entity_status    entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by       VARCHAR(100)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    approved_at      TIMESTAMPTZ,
    CONSTRAINT uq_recipe_product_version UNIQUE (product_id, version)
);

CREATE TABLE IF NOT EXISTS recipe_line (
    id              UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id       UUID             NOT NULL REFERENCES recipe(id),
    ingredient_id   UUID             REFERENCES ingredient(id),
    semi_product_id UUID             REFERENCES semi_product(id),
    quantity_gram   NUMERIC(12,4)    NOT NULL,
    line_type       recipe_line_type NOT NULL,
    note            TEXT,
    entity_status   entity_status    NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(100)     NOT NULL,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMPTZ,
    CONSTRAINT chk_recipe_line_xor CHECK (
        (ingredient_id IS NOT NULL AND semi_product_id IS NULL) OR
        (ingredient_id IS NULL     AND semi_product_id IS NOT NULL)
    )
);

-- ---------------------------------------------------------------------------
-- 4. PRODUCT MAPPING / TEMPLATE
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS product_mapping (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id       UUID          NOT NULL REFERENCES product(id),
    sku_code         VARCHAR(50)   NOT NULL,
    sku_source       VARCHAR(50)   NOT NULL DEFAULT 'POS',
    production_day   SMALLINT,
    recipe_addon_id  UUID          REFERENCES recipe(id),
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    note             TEXT,
    entity_status    entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by       VARCHAR(100)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    approved_at      TIMESTAMPTZ,
    CONSTRAINT uq_product_mapping_sku UNIQUE (sku_code)
);

CREATE TABLE IF NOT EXISTS production_template (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id       UUID          NOT NULL REFERENCES product(id),
    weekday_qty      NUMERIC(12,3) NOT NULL DEFAULT 0,
    weekend_qty      NUMERIC(12,3) NOT NULL DEFAULT 0,
    default_qty      NUMERIC(12,3) NOT NULL DEFAULT 0,
    production_rule  VARCHAR(20)   NOT NULL DEFAULT 'SUBTRACT',
    rule_config      JSONB,
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    note             TEXT,
    entity_status    entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by       VARCHAR(100)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    approved_at      TIMESTAMPTZ,
    CONSTRAINT uq_production_template_product UNIQUE (product_id)
);

-- Production Group — nhóm sản phẩm dùng chung phôi (GROUP_SUBTRACT)
CREATE TABLE IF NOT EXISTS production_group (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    group_code             VARCHAR(50)   NOT NULL,
    group_name             VARCHAR(100)  NOT NULL,
    main_semi_product_code VARCHAR(50)   NOT NULL, -- FK mềm → semi_product.code
    weekday_target         NUMERIC(12,3) NOT NULL DEFAULT 0,
    weekend_target         NUMERIC(12,3) NOT NULL DEFAULT 0,
    is_active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by             VARCHAR(100)  NOT NULL DEFAULT 'system',
    CONSTRAINT uq_production_group_code UNIQUE (group_code)
);

-- Thành viên trong nhóm
CREATE TABLE IF NOT EXISTS production_group_member (
    group_id   UUID NOT NULL REFERENCES production_group(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id),
    PRIMARY KEY (group_id, product_id)
);

-- Cấu hình công thức LAN_XUAT / LAN_MAM
CREATE TABLE IF NOT EXISTS batch_formula_config (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    formula_code          VARCHAR(50)  NOT NULL,
    formula_name          VARCHAR(100) NOT NULL,
    formula_type          VARCHAR(20)  NOT NULL,   -- 'LAN_MAM' | 'LAN_XUAT'
    target_product_prefix VARCHAR(20)  NOT NULL,   -- VD: 'PK-BENTO', 'PK-SIZE'
    max_qty_per_batch     INT          NOT NULL DEFAULT 1,
    input_variables       JSONB,                   -- {"multiplier": 1.5, "extra_bap_coi": true}
    output_yield_mapping  JSONB        NOT NULL,   -- {"PK-SIZE-12": 3, "PK-SIZE-14": 3, ...}
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_batch_formula_code UNIQUE (formula_code)
);

-- ---------------------------------------------------------------------------
-- 5. PURCHASING
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS purchase_order (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(30)    NOT NULL,
    supplier_id     UUID           NOT NULL REFERENCES supplier(id),
    branch_id       UUID           NOT NULL REFERENCES branch(id),
    order_date      DATE           NOT NULL,
    received_date   DATE,
    total_amount    NUMERIC(18,2),
    paid_amount     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    payment_status  payment_status NOT NULL DEFAULT 'UNPAID',
    note            TEXT,
    entity_status   entity_status  NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(100)   NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMPTZ,
    CONSTRAINT uq_purchase_order_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS purchase_order_line (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id    UUID          NOT NULL REFERENCES purchase_order(id),
    ingredient_id        UUID          REFERENCES ingredient(id),
    product_id           UUID          REFERENCES product(id),
    purchase_unit        VARCHAR(50)   NOT NULL,
    qty_ordered          NUMERIC(12,3) NOT NULL,
    qty_received         NUMERIC(12,3),
    unit_price           NUMERIC(18,2) NOT NULL,
    total_price          NUMERIC(18,2) GENERATED ALWAYS AS (qty_ordered * unit_price) STORED,
    qty_in_base_unit     NUMERIC(18,4),
    note                 TEXT,
    created_by           VARCHAR(100)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by           VARCHAR(100),
    updated_at           TIMESTAMPTZ,
    CONSTRAINT chk_pol_item CHECK (
        (ingredient_id IS NOT NULL AND product_id IS NULL) OR
        (ingredient_id IS NULL     AND product_id IS NOT NULL)
    )
);

-- ---------------------------------------------------------------------------
-- 6. INGREDIENT STOCK SUMMARY
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ingredient_stock (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ingredient_id   UUID          NOT NULL REFERENCES ingredient(id),
    branch_id       UUID          NOT NULL REFERENCES branch(id),
    qty_on_hand     NUMERIC(18,4) NOT NULL DEFAULT 0,
    qty_reserved    NUMERIC(18,4) NOT NULL DEFAULT 0,
    qty_available   NUMERIC(18,4) GENERATED ALWAYS AS (qty_on_hand - qty_reserved) STORED,
    last_updated    TIMESTAMPTZ,
    created_by      VARCHAR(100)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    CONSTRAINT uq_ingredient_stock_ingredient_branch UNIQUE (ingredient_id, branch_id)
);

-- ---------------------------------------------------------------------------
-- 7. GOODS TRANSFER — core tables
--    PHẢI đứng trước ingredient_stock_lot vì lot có FK → goods_transfer
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS goods_transfer (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code             VARCHAR(30) NOT NULL,
    from_branch_id   UUID        NOT NULL REFERENCES branch(id),
    -- NULL khi transfer_reason = ADJUSTMENT (không có kho nhận)
    to_branch_id     UUID        REFERENCES branch(id),
    transfer_date    DATE        NOT NULL,
    -- PRODUCTION | RESTOCK | RETURN | ADJUSTMENT | WASTE_DISPOSAL
    transfer_reason  VARCHAR(30) NOT NULL DEFAULT 'PRODUCTION',
    -- PENDING   : Vừa tạo, Cường (KHO_TONG) thấy để chuẩn bị
    -- READY     : Cường chuẩn bị xong, BEP kiểm tra
    -- COMPLETED : BEP approve → inventory đã được cập nhật atomic
    -- REJECTED  : BEP từ chối (lý do bắt buộc)
    -- CANCELLED : Huỷ trước khi READY
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ready_by         VARCHAR(100),
    ready_at         TIMESTAMPTZ,
    confirmed_by     VARCHAR(100),
    confirmed_at     TIMESTAMPTZ,
    rejected_by      VARCHAR(100),
    rejected_at      TIMESTAMPTZ,
    rejection_reason TEXT,
    note             TEXT,
    -- Traceability: phiếu này được clone từ phiếu REJECTED nào
    cloned_from_id   UUID        REFERENCES goods_transfer(id),
    -- AUTO_PLAN: sinh từ production plan | MANUAL: nhân viên tạo thủ công
    transfer_source  VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    created_by       VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_goods_transfer_code UNIQUE (code),
    CONSTRAINT fk_gt_from        FOREIGN KEY (from_branch_id) REFERENCES branch(id),
    CONSTRAINT fk_gt_to          FOREIGN KEY (to_branch_id)   REFERENCES branch(id),
    CONSTRAINT fk_gt_cloned_from FOREIGN KEY (cloned_from_id) REFERENCES goods_transfer(id),
    CONSTRAINT chk_gt_reason CHECK (transfer_reason IN (
        'PRODUCTION', 'RESTOCK', 'RETURN', 'ADJUSTMENT', 'WASTE_DISPOSAL'
    )),
    CONSTRAINT chk_gt_status CHECK (status IN (
        'PENDING', 'READY', 'COMPLETED', 'REJECTED', 'CANCELLED'
    )),
    CONSTRAINT chk_gt_source CHECK (transfer_source IN ('AUTO_PLAN', 'MANUAL')),
    CONSTRAINT chk_gt_adjustment_no_dest CHECK (
        transfer_reason != 'ADJUSTMENT' OR to_branch_id IS NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_gt_date   ON goods_transfer(transfer_date);
CREATE INDEX IF NOT EXISTS idx_gt_status ON goods_transfer(status);

CREATE TABLE IF NOT EXISTS goods_transfer_line (
    id               UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    transfer_id      UUID          NOT NULL REFERENCES goods_transfer(id),
    ingredient_id    UUID          REFERENCES ingredient(id),
    product_id       UUID          REFERENCES product(id),
    unit             VARCHAR(20)   NOT NULL DEFAULT 'GRAM',
    -- Lượng công thức tính ra (trước khi làm tròn whole-unit)
    qty_from_recipe  NUMERIC(18,4),
    -- Lượng thực xuất (sau whole-unit rounding)
    qty_requested    NUMERIC(18,4) NOT NULL,
    -- Giá bình quân FEFO, tính khi COMPLETED
    avg_unit_price   NUMERIC(18,6),
    note             TEXT,
    CONSTRAINT fk_gtl_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id),
    CONSTRAINT chk_gtl_item CHECK (
        (ingredient_id IS NOT NULL AND product_id IS NULL) OR
        (ingredient_id IS NULL     AND product_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_gtl_transfer   ON goods_transfer_line(transfer_id);
CREATE INDEX IF NOT EXISTS idx_gtl_ingredient ON goods_transfer_line(ingredient_id) WHERE ingredient_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_gtl_product    ON goods_transfer_line(product_id)    WHERE product_id    IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 8. INGREDIENT STOCK LOTS  (references goods_transfer — defined above)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ingredient_stock_lot (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ingredient_id        UUID          NOT NULL REFERENCES ingredient(id),
    branch_id            UUID          NOT NULL REFERENCES branch(id),
    purchase_order_id    UUID          REFERENCES purchase_order(id),
    import_date          DATE          NOT NULL,
    expiry_date          DATE,
    qty_imported         NUMERIC(18,4) NOT NULL,
    qty_remaining        NUMERIC(18,4) NOT NULL,
    unit_price           NUMERIC(18,6) NOT NULL,
    is_depleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    is_backdate          BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Lô này đến từ phiếu chuyển kho nào (chỉ có khi nhận từ GoodsTransfer)
    source_transfer_id   UUID          REFERENCES goods_transfer(id),
    note                 TEXT,
    entity_status        entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by           VARCHAR(100)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by           VARCHAR(100),
    updated_at           TIMESTAMPTZ,
    approved_by          VARCHAR(100),
    approved_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_isl_expiry ON ingredient_stock_lot(expiry_date) WHERE expiry_date IS NOT NULL;

CREATE TABLE IF NOT EXISTS stock_lot (
    id                      UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    barcode                 VARCHAR(50),
    purchase_order_line_id  UUID             REFERENCES purchase_order_line(id),
    ingredient_id           UUID             NOT NULL REFERENCES ingredient(id),
    branch_id               UUID             NOT NULL REFERENCES branch(id),
    qty_in_base_unit        NUMERIC(18,4)    NOT NULL,
    qty_remaining           NUMERIC(18,4)    NOT NULL,
    unit_price              NUMERIC(18,2)    NOT NULL,
    price_per_base_unit     NUMERIC(18,6),
    received_date           DATE             NOT NULL,
    expiry_date             DATE,
    issued_to               VARCHAR(200),
    issued_date             DATE,
    issued_qty              NUMERIC(18,4)    NOT NULL DEFAULT 0,
    status                  stock_lot_status NOT NULL DEFAULT 'AVAILABLE',
    entity_status           entity_status    NOT NULL DEFAULT 'ACTIVE',
    created_by              VARCHAR(100)     NOT NULL,
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_by              VARCHAR(100),
    updated_at              TIMESTAMPTZ,
    approved_by             VARCHAR(100),
    approved_at             TIMESTAMPTZ,
    CONSTRAINT uq_stock_lot_barcode UNIQUE (barcode)
);

-- ---------------------------------------------------------------------------
-- 9. PRODUCT STOCK LOTS
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS product_stock_lot (
    id                UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id        UUID          NOT NULL REFERENCES product(id),
    branch_id         UUID          NOT NULL REFERENCES branch(id),
    purchase_order_id UUID          REFERENCES purchase_order(id),
    import_date       DATE          NOT NULL,
    expiry_date       DATE,
    qty_imported      NUMERIC(12,4) NOT NULL,
    qty_remaining     NUMERIC(12,4) NOT NULL,
    unit_price        NUMERIC(18,2) NOT NULL,
    is_depleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    note              TEXT,
    entity_status     entity_status NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(100)  NOT NULL DEFAULT 'system'
);

CREATE INDEX IF NOT EXISTS idx_psl_product  ON product_stock_lot(product_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_psl_depleted ON product_stock_lot(product_id, branch_id) WHERE is_depleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_psl_expiry   ON product_stock_lot(expiry_date) WHERE expiry_date IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 10. PRODUCTION
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS production_order (
    id           UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id    UUID              NOT NULL REFERENCES branch(id),
    order_date   DATE              NOT NULL,
    status       reconcile_status  NOT NULL DEFAULT 'PENDING',
    source_file  VARCHAR(500),
    created_by   VARCHAR(100)      NOT NULL,
    created_at   TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    updated_by   VARCHAR(100),
    updated_at   TIMESTAMPTZ,
    CONSTRAINT uq_production_order_branch_date UNIQUE (branch_id, order_date)
);

CREATE TABLE IF NOT EXISTS production_order_line (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID          NOT NULL REFERENCES production_order(id),
    product_id    UUID          NOT NULL REFERENCES product(id),
    qty_requested NUMERIC(12,3) NOT NULL,
    qty_actual    NUMERIC(12,3),
    unit          VARCHAR(20)   NOT NULL,
    created_by    VARCHAR(100)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT uq_production_order_line UNIQUE (order_id, product_id)
);

CREATE TABLE IF NOT EXISTS production_lot (
    id                  UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    lot_number          VARCHAR(60)      NOT NULL,
    product_id          UUID             NOT NULL REFERENCES product(id),
    branch_id           UUID             NOT NULL REFERENCES branch(id),
    production_date     DATE             NOT NULL,
    expiry_date         DATE             NOT NULL,
    qty_produced        NUMERIC(12,3)    NOT NULL,
    qty_sold            NUMERIC(12,3)    NOT NULL DEFAULT 0,
    qty_cancelled       NUMERIC(12,3)    NOT NULL DEFAULT 0,
    qty_remaining       NUMERIC(12,3)    GENERATED ALWAYS AS (qty_produced - qty_sold - qty_cancelled) STORED,
    cost_per_unit       NUMERIC(18,6)    NOT NULL DEFAULT 0,
    cost_status         lot_cost_status  NOT NULL DEFAULT 'CONFIRMED',
    parent_lot_id       UUID             REFERENCES production_lot(id),
    weight_kg           NUMERIC(10,4),
    production_order_id UUID             REFERENCES production_order(id),
    status              lot_status       NOT NULL DEFAULT 'ACTIVE',
    note                TEXT,
    entity_status       entity_status    NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(100)     NOT NULL,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uq_production_lot_number UNIQUE (lot_number)
);

CREATE TABLE IF NOT EXISTS production_plan (
    id               UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    plan_date        DATE          NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    note             TEXT,
    approved_by      VARCHAR(100),
    approved_at      TIMESTAMPTZ,
    rejection_reason TEXT,
    entity_status    entity_status NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100)  NOT NULL DEFAULT 'system',
    CONSTRAINT uq_production_plan_date UNIQUE (plan_date)
);

CREATE INDEX IF NOT EXISTS idx_pp_date   ON production_plan(plan_date);
CREATE INDEX IF NOT EXISTS idx_pp_status ON production_plan(status);

CREATE TABLE IF NOT EXISTS production_plan_line (
    id           UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    plan_id      UUID          NOT NULL REFERENCES production_plan(id) ON DELETE CASCADE,
    product_id   UUID          NOT NULL REFERENCES product(id),
    qty_planned  NUMERIC(12,3) NOT NULL,
    qty_adjusted NUMERIC(12,3),
    note         TEXT
);

CREATE TABLE IF NOT EXISTS production_request (
    id               UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code             VARCHAR(30)   NOT NULL,
    request_type     VARCHAR(20)   NOT NULL DEFAULT 'DAILY',
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    plan_id          UUID          REFERENCES production_plan(id),
    product_id       UUID          NOT NULL REFERENCES product(id),
    recipe_id        UUID          REFERENCES recipe(id),
    price_override   NUMERIC(18,2),
    qty_planned      NUMERIC(12,3) NOT NULL,
    qty_actual       NUMERIC(12,3),
    variance_reason  TEXT,
    note             TEXT,
    requested_by     VARCHAR(100)  NOT NULL,
    approved_by      VARCHAR(100),
    approved_at      TIMESTAMPTZ,
    rejection_reason TEXT,
    entity_status    entity_status NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_production_request_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_prq_status ON production_request(status);
CREATE INDEX IF NOT EXISTS idx_prq_type   ON production_request(request_type);
CREATE INDEX IF NOT EXISTS idx_prq_plan   ON production_request(plan_id) WHERE plan_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 11. STOCK TRANSFER (product inter-branch)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS stock_transfer (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    from_branch_id    UUID          NOT NULL REFERENCES branch(id),
    to_branch_id      UUID          NOT NULL REFERENCES branch(id),
    product_id        UUID          NOT NULL REFERENCES product(id),
    transfer_date     DATE          NOT NULL,
    qty_sent          NUMERIC(12,3) NOT NULL,
    qty_received      NUMERIC(12,3),
    qty_discrepancy   NUMERIC(12,3) GENERATED ALWAYS AS (qty_sent - COALESCE(qty_received, qty_sent)) STORED,
    unit              VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    confirmed_by      VARCHAR(100),
    confirmed_at      TIMESTAMPTZ,
    rejection_reason  TEXT,
    created_by        VARCHAR(100)  NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMPTZ,
    CONSTRAINT uq_stock_transfer UNIQUE (from_branch_id, to_branch_id, product_id, transfer_date)
);

CREATE INDEX IF NOT EXISTS idx_st_status ON stock_transfer(status);

-- ---------------------------------------------------------------------------
-- 12. GOODS TRANSFER — continuation (references ingredient_stock_lot)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS goods_transfer_allocation (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    transfer_id         UUID          NOT NULL REFERENCES goods_transfer(id),
    transfer_line_id    UUID          NOT NULL REFERENCES goods_transfer_line(id),
    source_lot_id       UUID          NOT NULL REFERENCES ingredient_stock_lot(id),
    destination_lot_id  UUID          REFERENCES ingredient_stock_lot(id),
    qty_consumed        NUMERIC(18,4) NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gta_transfer   ON goods_transfer_allocation(transfer_id);
CREATE INDEX IF NOT EXISTS idx_gta_source_lot ON goods_transfer_allocation(source_lot_id);

CREATE TABLE IF NOT EXISTS goods_transfer_return (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code                 VARCHAR(30)  NOT NULL,
    original_transfer_id UUID         NOT NULL REFERENCES goods_transfer(id),
    from_branch_id       UUID         NOT NULL REFERENCES branch(id),
    to_branch_id         UUID         NOT NULL REFERENCES branch(id),
    return_date          DATE         NOT NULL,
    reason               TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    requested_by         VARCHAR(100) NOT NULL,
    approved_by          VARCHAR(100),
    approved_at          TIMESTAMPTZ,
    rejection_reason     TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gtr_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS goods_transfer_return_line (
    id            UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    return_id     UUID          NOT NULL REFERENCES goods_transfer_return(id),
    ingredient_id UUID          REFERENCES ingredient(id),
    product_id    UUID          REFERENCES product(id),
    source_lot_id UUID          NOT NULL REFERENCES ingredient_stock_lot(id),
    qty           NUMERIC(18,4) NOT NULL,
    unit          VARCHAR(20)   NOT NULL DEFAULT 'GRAM',
    note          TEXT,
    CONSTRAINT chk_gtrl_item CHECK (
        (ingredient_id IS NOT NULL AND product_id IS NULL) OR
        (ingredient_id IS NULL     AND product_id IS NOT NULL)
    )
);

-- ---------------------------------------------------------------------------
-- 13. DAILY OPS / RECONCILE
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS daily_inventory (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID          NOT NULL REFERENCES branch(id),
    product_id          UUID          NOT NULL REFERENCES product(id),
    inventory_date      DATE          NOT NULL,
    qty_opening         NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_received        NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_cancelled       NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_closing         NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_sold_reported   NUMERIC(12,3) NOT NULL DEFAULT 0,
    source_file         VARCHAR(500),
    created_by          VARCHAR(100)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uq_daily_inventory UNIQUE (branch_id, product_id, inventory_date)
);

CREATE TABLE IF NOT EXISTS pos_transaction (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id        UUID          NOT NULL REFERENCES branch(id),
    product_id       UUID          NOT NULL REFERENCES product(id),
    transaction_date DATE          NOT NULL,
    qty_sold         NUMERIC(12,3) NOT NULL,
    unit_price       NUMERIC(18,4),
    revenue          NUMERIC(18,4),
    source_file      VARCHAR(500),
    created_by       VARCHAR(100)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMPTZ,
    CONSTRAINT uq_pos_transaction UNIQUE (branch_id, product_id, transaction_date)
);

CREATE TABLE IF NOT EXISTS daily_reconcile (
    id                          UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id                   UUID             NOT NULL REFERENCES branch(id),
    product_id                  UUID             NOT NULL REFERENCES product(id),
    recon_date                  DATE             NOT NULL,
    recipe_id                   UUID             REFERENCES recipe(id),
    qty_requested               NUMERIC(12,3),
    qty_produced                NUMERIC(12,3),
    production_vs_order_diff    NUMERIC(12,3),
    production_vs_order_status  reconcile_status NOT NULL DEFAULT 'PENDING',
    qty_sent                    NUMERIC(12,3),
    qty_received                NUMERIC(12,3),
    delivery_vs_receipt_diff    NUMERIC(12,3),
    delivery_vs_receipt_status  reconcile_status NOT NULL DEFAULT 'PENDING',
    qty_sold_pos                NUMERIC(12,3),
    qty_opening                 NUMERIC(12,3),
    qty_cancelled               NUMERIC(12,3),
    qty_closing                 NUMERIC(12,3),
    qty_sold_reported           NUMERIC(12,3),
    qty_sold_derived            NUMERIC(12,3),
    pos_vs_inventory_diff       NUMERIC(12,3),
    pos_vs_inventory_status     reconcile_status NOT NULL DEFAULT 'PENDING',
    ingredient_price_version    INTEGER,
    semi_product_cost_version   INTEGER,
    cost_per_unit               NUMERIC(18,4),
    unit_price                  NUMERIC(18,4),
    revenue                     NUMERIC(18,4),
    sales_cost                  NUMERIC(18,4),
    cancelled_cost              NUMERIC(18,4),
    gross_profit                NUMERIC(18,4),
    overall_status              reconcile_status NOT NULL DEFAULT 'PENDING',
    discrepancy_note            TEXT,
    created_by                  VARCHAR(100)     NOT NULL,
    created_at                  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_by                  VARCHAR(100),
    updated_at                  TIMESTAMPTZ,
    CONSTRAINT uq_daily_reconcile UNIQUE (branch_id, product_id, recon_date)
);

CREATE TABLE IF NOT EXISTS cancel_log (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id        UUID          NOT NULL REFERENCES branch(id),
    product_id       UUID          NOT NULL REFERENCES product(id),
    cancel_date      DATE          NOT NULL,
    qty_reported     NUMERIC(12,3) NOT NULL,
    qty_cancelled    NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_discrepancy  NUMERIC(12,3) GENERATED ALWAYS AS (qty_reported - qty_cancelled) STORED,
    cancelled_cost   NUMERIC(18,4) NOT NULL DEFAULT 0,
    cancel_status    VARCHAR(20)   NOT NULL DEFAULT 'OK',
    warning_note     TEXT,
    created_by       VARCHAR(100)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS cancel_log_detail (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    cancel_log_id     UUID          NOT NULL REFERENCES cancel_log(id),
    production_lot_id UUID          NOT NULL REFERENCES production_lot(id),
    qty_cancelled     NUMERIC(12,3) NOT NULL,
    cost_per_unit     NUMERIC(18,6) NOT NULL,
    cancelled_cost    NUMERIC(18,6) GENERATED ALWAYS AS (qty_cancelled * cost_per_unit) STORED,
    lot_expiry_date   DATE,
    created_by        VARCHAR(100)  NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- 14. INVENTORY WRITE-OFF / ADJUSTMENT / MOVEMENT
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS inventory_write_off (
    id            UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code          VARCHAR(30)   NOT NULL,
    branch_id     UUID          NOT NULL REFERENCES branch(id),
    item_type     VARCHAR(20)   NOT NULL,
    ingredient_id UUID          REFERENCES ingredient(id),
    product_id    UUID          REFERENCES product(id),
    lot_id        UUID          NOT NULL,
    qty           NUMERIC(18,4) NOT NULL,
    unit          VARCHAR(20)   NOT NULL,
    reason_type   VARCHAR(20)   NOT NULL,
    reason_note   TEXT,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_by    VARCHAR(100)  NOT NULL,
    approved_by   VARCHAR(100),
    approved_at   TIMESTAMPTZ,
    entity_status entity_status NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_write_off_code UNIQUE (code),
    CONSTRAINT chk_wof_item CHECK (
        (ingredient_id IS NOT NULL AND product_id IS NULL) OR
        (ingredient_id IS NULL     AND product_id IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS inventory_adjustment (
    id            UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code          VARCHAR(30)   NOT NULL,
    branch_id     UUID          NOT NULL REFERENCES branch(id),
    item_type     VARCHAR(20)   NOT NULL,
    ingredient_id UUID          REFERENCES ingredient(id),
    product_id    UUID          REFERENCES product(id),
    lot_id        UUID          NOT NULL,
    qty_before    NUMERIC(18,4) NOT NULL,
    qty_after     NUMERIC(18,4) NOT NULL,
    qty_delta     NUMERIC(18,4) GENERATED ALWAYS AS (qty_after - qty_before) STORED,
    reason        TEXT          NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_by    VARCHAR(100)  NOT NULL,
    approved_by   VARCHAR(100),
    approved_at   TIMESTAMPTZ,
    entity_status entity_status NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_adjustment_code UNIQUE (code),
    CONSTRAINT chk_adj_item CHECK (
        (ingredient_id IS NOT NULL AND product_id IS NULL) OR
        (ingredient_id IS NULL     AND product_id IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS inventory_movement (
    id               UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    branch_id        UUID          NOT NULL REFERENCES branch(id),
    item_type        VARCHAR(20)   NOT NULL,  -- INGREDIENT | PRODUCT
    ingredient_id    UUID          REFERENCES ingredient(id),
    product_id       UUID          REFERENCES product(id),
    lot_id           UUID,
    -- Loại giao dịch: IMPORT | EXPORT | RETURN | ADJUSTMENT | DISCARD | STOCK_COUNT
    transaction_type VARCHAR(20)   NOT NULL,
    -- Sub-type: DAILY | TO_KITCHEN | DAMAGED | INCREASE | END_OF_DAY | v.v.
    reference_type   VARCHAR(30)   NOT NULL,
    qty              NUMERIC(18,4) NOT NULL,
    unit             VARCHAR(20)   NOT NULL,
    -- Trỏ về document gốc (GOODS_TRANSFER | ADJUSTMENT | WRITE_OFF | ...)
    source_type      VARCHAR(50),
    source_id        UUID,
    -- Mã human-readable của document gốc — hiển thị UI không cần join
    reference_code   VARCHAR(50),
    note             TEXT,
    created_by       VARCHAR(100)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_im_branch      ON inventory_movement(branch_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_im_ingredient  ON inventory_movement(ingredient_id) WHERE ingredient_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_im_product     ON inventory_movement(product_id)    WHERE product_id    IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 15. SUPPLIER RETURN
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS supplier_return (
    id                UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code              VARCHAR(30)  NOT NULL,
    supplier_id       UUID         NOT NULL REFERENCES supplier(id),
    original_po_id    UUID         NOT NULL REFERENCES purchase_order(id),
    write_off_id      UUID         REFERENCES inventory_write_off(id),
    return_date       DATE         NOT NULL,
    reason            TEXT         NOT NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    replacement_po_id UUID         REFERENCES purchase_order(id),
    note              TEXT,
    created_by        VARCHAR(100) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_supplier_return_code UNIQUE (code)
);

-- ---------------------------------------------------------------------------
-- 16. CUSTOMER ORDERS
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS customer_order (
    id              UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code            VARCHAR(30)   NOT NULL,
    customer_name   VARCHAR(200),
    customer_phone  VARCHAR(20),
    delivery_date   DATE          NOT NULL,
    note            TEXT,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    payment_status  VARCHAR(20)   NOT NULL DEFAULT 'UNPAID',
    total_amount    NUMERIC(18,2),
    deposit_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    paid_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    entity_status   entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(100)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_customer_order_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_co_delivery ON customer_order(delivery_date);
CREATE INDEX IF NOT EXISTS idx_co_status   ON customer_order(status);

CREATE TABLE IF NOT EXISTS customer_order_line (
    id          UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id    UUID          NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    product_id  UUID          NOT NULL REFERENCES product(id),
    recipe_id   UUID          REFERENCES recipe(id),
    qty         NUMERIC(12,3) NOT NULL,
    unit_price  NUMERIC(18,2) NOT NULL,
    total_price NUMERIC(18,2) GENERATED ALWAYS AS (qty * unit_price) STORED,
    note        TEXT
);

CREATE TABLE IF NOT EXISTS customer_order_payment (
    id           UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id     UUID          NOT NULL REFERENCES customer_order(id),
    payment_type VARCHAR(20)   NOT NULL DEFAULT 'BANK_TRANSFER',
    amount       NUMERIC(18,2) NOT NULL,
    payment_date DATE          NOT NULL,
    note         TEXT,
    recorded_by  VARCHAR(100)  NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Topping / NL add-on đặc thù cho đơn SHEET_CAKE
-- addon_type = 'INGREDIENT' → ingredient_id, 'ACCESSORY' → product_id (ACCESSORY type)
CREATE TABLE IF NOT EXISTS customer_order_line_addon (
    id            UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    line_id       UUID          NOT NULL REFERENCES customer_order_line(id) ON DELETE CASCADE,
    addon_type    VARCHAR(20)   NOT NULL DEFAULT 'INGREDIENT',
    ingredient_id UUID          REFERENCES ingredient(id),
    product_id    UUID          REFERENCES product(id),
    qty           NUMERIC(12,3) NOT NULL,
    unit          VARCHAR(20)   NOT NULL DEFAULT 'g',
    note          TEXT,
    created_by    VARCHAR(100)  NOT NULL DEFAULT 'system',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_addon_has_item CHECK (
        (ingredient_id IS NOT NULL AND product_id IS NULL)
        OR (ingredient_id IS NULL  AND product_id IS NOT NULL)
    )
);

-- ---------------------------------------------------------------------------
-- 17. BATCH / IMPORT LOGS
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS batch_run (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    run_type      batch_run_type NOT NULL,
    process_date  DATE           NOT NULL,
    is_rerun      BOOLEAN        NOT NULL DEFAULT FALSE,
    status        batch_status   NOT NULL DEFAULT 'RUNNING',
    triggered_by  VARCHAR(100),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    error_summary TEXT,
    created_by    VARCHAR(100)   NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS file_import_log (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_run_id      UUID         REFERENCES batch_run(id),
    file_name         VARCHAR(500) NOT NULL,
    file_type         file_type    NOT NULL,
    status            batch_status NOT NULL DEFAULT 'RUNNING',
    rows_total        INTEGER,
    rows_ok           INTEGER,
    rows_error        INTEGER,
    error_detail      TEXT,
    error_row_indices JSONB,
    imported_at       TIMESTAMPTZ,
    created_by        VARCHAR(100) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS txt_import_log (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name    VARCHAR(255) NOT NULL,
    file_hash    VARCHAR(64),
    process_date DATE,
    rows_parsed  INTEGER      NOT NULL DEFAULT 0,
    rows_ok      INTEGER      NOT NULL DEFAULT 0,
    rows_error   INTEGER      NOT NULL DEFAULT 0,
    error_detail TEXT,
    imported_at  TIMESTAMPTZ,
    created_by   VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_txt_import_log_hash UNIQUE (file_hash)
);

CREATE TABLE IF NOT EXISTS fifo_allocation_log (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    production_lot_id       UUID          NOT NULL REFERENCES production_lot(id),
    ingredient_stock_lot_id UUID          NOT NULL REFERENCES ingredient_stock_lot(id),
    ingredient_id           UUID          NOT NULL REFERENCES ingredient(id),
    qty_allocated           NUMERIC(18,4) NOT NULL,
    unit_price              NUMERIC(18,6) NOT NULL,
    cost_contribution       NUMERIC(18,6) NOT NULL,
    is_recalculated         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by              VARCHAR(100)  NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- 18. ADMIN FRAMEWORK
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS command_request (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type   VARCHAR(100)   NOT NULL,
    entity_id     UUID,
    action        command_action NOT NULL,
    status        command_status NOT NULL DEFAULT 'PENDING',
    payload       JSONB          NOT NULL,
    note          TEXT,
    created_by    VARCHAR(100)   NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    reviewed_by   VARCHAR(100),
    reviewed_at   TIMESTAMPTZ,
    reject_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_cmd_req_entity  ON command_request (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_cmd_req_status  ON command_request (status);
CREATE INDEX IF NOT EXISTS idx_cmd_req_created ON command_request (created_at DESC);

CREATE TABLE IF NOT EXISTS entity_revision_log (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type        VARCHAR(100)   NOT NULL,
    entity_id          UUID           NOT NULL,
    action             command_action NOT NULL,
    command_request_id UUID,
    snapshot_before    JSONB,
    snapshot_after     JSONB,
    created_by         VARCHAR(100)   NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rev_log_entity  ON entity_revision_log (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_rev_log_created ON entity_revision_log (created_at DESC);

-- ---------------------------------------------------------------------------
-- 19. AUTH / RBAC
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS user_role (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT uq_user_role_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS user_profile (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(200),
    full_name     VARCHAR(200),
    password_hash VARCHAR(255) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    role_id       UUID         NOT NULL,
    branch_id     UUID,                          -- NULL = không bị giới hạn branch (SUPER_ADMIN, KHO_TRUONG)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT uq_user_username UNIQUE (username),
    CONSTRAINT fk_user_role   FOREIGN KEY (role_id)   REFERENCES user_role(id),
    CONSTRAINT fk_user_branch FOREIGN KEY (branch_id) REFERENCES branch(id)
);

CREATE TABLE IF NOT EXISTS screen_registry (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    module      VARCHAR(50)  NOT NULL,
    description TEXT,
    sort_order  SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uq_screen_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id      UUID    NOT NULL,
    screen_id    UUID    NOT NULL,
    can_view     BOOLEAN NOT NULL DEFAULT FALSE,
    can_create   BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit     BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete   BOOLEAN NOT NULL DEFAULT FALSE,
    can_approve  BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, screen_id),
    CONSTRAINT fk_rp_role   FOREIGN KEY (role_id)   REFERENCES user_role(id),
    CONSTRAINT fk_rp_screen FOREIGN KEY (screen_id) REFERENCES screen_registry(id)
);

CREATE TABLE IF NOT EXISTS refresh_token (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    is_revoked  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES user_profile(id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON refresh_token(token_hash);

-- ---------------------------------------------------------------------------
-- 20. ACTIVITY LOG
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS activity_log (
    id           UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    performed_by VARCHAR(100) NOT NULL,
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    UUID         NOT NULL,
    entity_code  VARCHAR(100),
    old_status   VARCHAR(50),
    new_status   VARCHAR(50),
    note         TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_al_performed_by ON activity_log(performed_by, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_al_entity        ON activity_log(entity_type, entity_id);
