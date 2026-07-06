-- =============================================================
-- 01_init.sql  —  Schema V2 (Single-Table Ledger Architecture)
--
-- Thứ tự tạo bảng theo dependency:
--   1.  Master data (branch, product, ingredient, supplier, semi_product)
--   2.  Config / Pricing
--   3.  Recipe (BOM)
--   4.  Product mapping
--   5.  Production config (template, group, batch_formula)
--   6.  Production operations (order, lot, plan, request)
--   7.  Customer orders
--   8.  ── INVENTORY LEDGER (CORE) ──
--       8a. inventory              ← Số dư tồn kho theo lô
--       8b. inventory_transaction  ← Phiếu (PO / Transfer / Adjustment)
--       8c. inventory_transaction_line
--       8d. inventory_transaction_payment
--   9.  ── REPORTING (độc lập) ──
--       9a. daily_shop_report      ← Báo cáo hủy cuối ngày của Shop
--       9b. pos_sales_data         ← Dữ liệu POS do Chính upload
--  10.  Batch / Import logs
--  11.  Admin framework
--  12.  Auth / RBAC
--  13.  Activity log
--
-- DROPPED vs V1 schema:
--   purchase_order, purchase_order_line,
--   ingredient_stock, ingredient_stock_lot,
--   goods_transfer, goods_transfer_line,
--   goods_transfer_allocation, goods_transfer_return, goods_transfer_return_line,
--   stock_lot, product_stock_lot, stock_transfer,
--   daily_inventory, pos_transaction, daily_reconcile,
--   cancel_log, cancel_log_detail,
--   inventory_write_off, inventory_adjustment, inventory_movement,
--   fifo_allocation_log, supplier_return, accessory_stocktake_log,
--   ingredient_transfer
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
    branch_type   branch_type   NOT NULL DEFAULT 'SHOP',
    entity_status entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(100)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100),
    updated_at    TIMESTAMPTZ,
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
    email         VARCHAR(100),
    contact_name  VARCHAR(100),
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
    id            UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(50)        NOT NULL,
    name          VARCHAR(200)       NOT NULL,
    semi_type     semi_product_type  NOT NULL,
    base_unit     base_unit          NOT NULL DEFAULT 'GRAM',
    is_active     BOOLEAN            NOT NULL DEFAULT TRUE,
    entity_status entity_status      NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(100)       NOT NULL,
    created_at    TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT uq_semi_product_code UNIQUE (code)
);

-- ---------------------------------------------------------------------------
-- 2. CONFIG / PRICING
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS product_expiry_config (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id        UUID         NOT NULL REFERENCES product(id),
    shelf_life_hours  INTEGER      NOT NULL,
    created_by        VARCHAR(100) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pec_product UNIQUE (product_id)
);

CREATE TABLE IF NOT EXISTS product_prefix (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    prefix        VARCHAR(20)  NOT NULL,
    product_type  VARCHAR(50)  NOT NULL,
    description   TEXT,
    created_by    VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_prefix UNIQUE (prefix)
);

CREATE TABLE IF NOT EXISTS ingredient_price (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ingredient_id  UUID          NOT NULL REFERENCES ingredient(id),
    supplier_id    UUID          REFERENCES supplier(id),
    price_per_unit NUMERIC(18,4) NOT NULL,
    unit           VARCHAR(20)   NOT NULL,
    effective_date DATE          NOT NULL,
    note           TEXT,
    entity_status  entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by     VARCHAR(100)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    approved_by    VARCHAR(100),
    approved_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ing_price_ing  ON ingredient_price(ingredient_id, effective_date DESC);

CREATE TABLE IF NOT EXISTS unit_conversion (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ingredient_id   UUID          NOT NULL REFERENCES ingredient(id),
    purchase_unit   VARCHAR(20)   NOT NULL,
    purchase_unit_label VARCHAR(50),
    base_quantity   NUMERIC(18,6) NOT NULL,
    is_default      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(100)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_unit_conversion UNIQUE (ingredient_id, purchase_unit)
);

CREATE TABLE IF NOT EXISTS product_price (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id     UUID          NOT NULL REFERENCES product(id),
    price          NUMERIC(18,2) NOT NULL,
    effective_date DATE          NOT NULL,
    note           TEXT,
    entity_status  entity_status NOT NULL DEFAULT 'ACTIVE',
    created_by     VARCHAR(100)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    approved_by    VARCHAR(100),
    approved_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_pro_price_pro ON product_price(product_id, effective_date DESC);

-- ---------------------------------------------------------------------------
-- 3. RECIPE (BOM)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS recipe_line_semi (
    id             UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    semi_id        UUID             NOT NULL REFERENCES semi_product(id),
    ingredient_id  UUID             NOT NULL REFERENCES ingredient(id),
    quantity_gram  NUMERIC(18,4)    NOT NULL,
    line_type      recipe_line_type NOT NULL,
    created_by     VARCHAR(100)     NOT NULL,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rls UNIQUE (semi_id, ingredient_id)
);

CREATE TABLE IF NOT EXISTS recipe (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID          NOT NULL REFERENCES product(id),
    version     INTEGER       NOT NULL DEFAULT 1,
    is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
    note        TEXT,
    created_by  VARCHAR(100)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT uq_recipe_product_version UNIQUE (product_id, version)
);

CREATE TABLE IF NOT EXISTS recipe_line (
    id             UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id      UUID             NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,
    ingredient_id  UUID             REFERENCES ingredient(id),
    semi_product_id UUID            REFERENCES semi_product(id),
    quantity_gram  NUMERIC(18,4)    NOT NULL,
    line_type      recipe_line_type NOT NULL,
    created_by     VARCHAR(100)     NOT NULL,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rl_item CHECK (
        (ingredient_id IS NOT NULL AND semi_product_id IS NULL)
        OR (ingredient_id IS NULL AND semi_product_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_recipe_line_recipe ON recipe_line(recipe_id);

-- ---------------------------------------------------------------------------
-- 4. PRODUCT MAPPING (EX_CODE → IN_CODE)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS product_mapping (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID         NOT NULL REFERENCES product(id),
    sku_code    VARCHAR(50)  NOT NULL,
    sku_source  VARCHAR(20)  NOT NULL DEFAULT 'POS',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_mapping_sku UNIQUE (sku_code)
);

-- ---------------------------------------------------------------------------
-- 5. PRODUCTION CONFIG
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS production_template (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    description  TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by   VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_production_template_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS production_group (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(30)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by   VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_production_group_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS production_group_member (
    group_id    UUID NOT NULL REFERENCES production_group(id) ON DELETE CASCADE,
    product_id  UUID NOT NULL REFERENCES product(id),
    CONSTRAINT pk_pgm PRIMARY KEY (group_id, product_id)
);

CREATE TABLE IF NOT EXISTS batch_formula_config (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID          NOT NULL REFERENCES product(id),
    batch_size_units    NUMERIC(12,3) NOT NULL,
    batch_unit          VARCHAR(20)   NOT NULL DEFAULT 'PCS',
    bowl_size_label     VARCHAR(30),
    bowls_per_batch     INTEGER       NOT NULL DEFAULT 1,
    note                TEXT,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by          VARCHAR(100)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bfc_product UNIQUE (product_id)
);

-- ---------------------------------------------------------------------------
-- 6. PRODUCTION OPERATIONS
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS production_order (
    id           UUID                    PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id    UUID                    NOT NULL REFERENCES branch(id),
    order_date   DATE                    NOT NULL,
    status       production_order_status NOT NULL DEFAULT 'PENDING',
    source_file  VARCHAR(500),
    created_by   VARCHAR(100)            NOT NULL,
    created_at   TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
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

CREATE INDEX IF NOT EXISTS idx_pl_product_date ON production_lot(product_id, production_date DESC);
CREATE INDEX IF NOT EXISTS idx_pl_status        ON production_lot(status);

CREATE TABLE IF NOT EXISTS production_plan (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_date     DATE          NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    note          TEXT,
    approved_by   VARCHAR(100),
    approved_at   TIMESTAMPTZ,
    rejection_reason TEXT,
    entity_status entity_status NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100)  NOT NULL DEFAULT 'system',
    CONSTRAINT uq_production_plan_date UNIQUE (plan_date)
);

CREATE INDEX IF NOT EXISTS idx_pp_date   ON production_plan(plan_date);
CREATE INDEX IF NOT EXISTS idx_pp_status ON production_plan(status);

CREATE TABLE IF NOT EXISTS production_plan_line (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id      UUID          NOT NULL REFERENCES production_plan(id) ON DELETE CASCADE,
    product_id   UUID          NOT NULL REFERENCES product(id),
    qty_planned  NUMERIC(12,3) NOT NULL,
    qty_adjusted NUMERIC(12,3),
    note         TEXT
);

CREATE TABLE IF NOT EXISTS production_request (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(30)   NOT NULL,
    request_type     request_type  NOT NULL DEFAULT 'DAILY',
    status           request_status NOT NULL DEFAULT 'PENDING',
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
-- 7. CUSTOMER ORDERS
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS customer_order (
    id              UUID                  PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(30)           NOT NULL,
    customer_name   VARCHAR(200),
    customer_phone  VARCHAR(20),
    delivery_date   DATE                  NOT NULL,
    note            TEXT,
    status          customer_order_status NOT NULL DEFAULT 'PENDING',
    payment_status  payment_status        NOT NULL DEFAULT 'UNPAID',
    total_amount    NUMERIC(18,2),
    deposit_amount  NUMERIC(18,2)         NOT NULL DEFAULT 0,
    paid_amount     NUMERIC(18,2)         NOT NULL DEFAULT 0,
    entity_status   entity_status         NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(100)          NOT NULL,
    created_at      TIMESTAMPTZ           NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_customer_order_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_co_delivery ON customer_order(delivery_date);
CREATE INDEX IF NOT EXISTS idx_co_status   ON customer_order(status);

CREATE TABLE IF NOT EXISTS customer_order_line (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID          NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    product_id  UUID          NOT NULL REFERENCES product(id),
    recipe_id   UUID          REFERENCES recipe(id),
    qty         NUMERIC(12,3) NOT NULL,
    unit_price  NUMERIC(18,2) NOT NULL,
    total_price NUMERIC(18,2) GENERATED ALWAYS AS (qty * unit_price) STORED,
    note        TEXT
);

CREATE TABLE IF NOT EXISTS customer_order_payment (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID          NOT NULL REFERENCES customer_order(id),
    payment_type VARCHAR(20)   NOT NULL DEFAULT 'BANK_TRANSFER',
    amount       NUMERIC(18,2) NOT NULL,
    payment_date DATE          NOT NULL,
    note         TEXT,
    recorded_by  VARCHAR(100)  NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS customer_order_line_addon (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    line_id       UUID          NOT NULL REFERENCES customer_order_line(id) ON DELETE CASCADE,
    addon_type    VARCHAR(20)   NOT NULL DEFAULT 'INGREDIENT',
    ingredient_id UUID          REFERENCES ingredient(id),
    product_id    UUID          REFERENCES product(id),
    qty           NUMERIC(12,3) NOT NULL,
    unit          VARCHAR(20)   NOT NULL DEFAULT 'g',
    note          TEXT,
    created_by    VARCHAR(100)  NOT NULL DEFAULT 'system',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_addon_item CHECK (
        (ingredient_id IS NOT NULL AND product_id IS NULL)
        OR (ingredient_id IS NULL  AND product_id IS NOT NULL)
    )
);

-- ---------------------------------------------------------------------------
-- 8a. INVENTORY — Số dư tồn kho theo lô (Single-Table Ledger)
--
--  Mỗi row = 1 lô (lot) của 1 mặt hàng tại 1 chi nhánh.
--  item_type = INGREDIENT → item_id trỏ đến ingredient.id
--  item_type = PRODUCT    → item_id trỏ đến product.id
--  (Polymorphic FK — không dùng DB-level FK vì 2 bảng đích)
--
--  Tổng tồn kho item X tại branch Y:
--    SELECT SUM(qty_available) FROM inventory
--    WHERE item_id = X AND branch_id = Y AND qty_available > 0
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS inventory (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id      UUID          NOT NULL REFERENCES branch(id),
    item_id        UUID          NOT NULL,
    item_type      VARCHAR(20)   NOT NULL,          -- INGREDIENT | PRODUCT
    qty_available  NUMERIC(12,4) NOT NULL DEFAULT 0,
    lot_number     VARCHAR(50),                     -- Mã lô để chạy FEFO
    expiry_date    DATE,                             -- Hạn sử dụng (dùng cho FEFO sort)
    cost_per_unit  NUMERIC(18,6),                   -- Giá vốn nhập của lô để tính COGS
    source_tx_id   UUID,                            -- inventory_transaction.id tạo ra lô này
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_inv_item_type CHECK (item_type IN ('INGREDIENT', 'PRODUCT')),
    CONSTRAINT chk_inv_qty       CHECK (qty_available >= 0)
);

CREATE INDEX IF NOT EXISTS idx_inv_item_branch ON inventory(item_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_inv_fefo        ON inventory(branch_id, item_id, expiry_date ASC NULLS LAST)
    WHERE qty_available > 0;
CREATE INDEX IF NOT EXISTS idx_inv_branch_type ON inventory(branch_id, item_type);

-- ---------------------------------------------------------------------------
-- 8b. INVENTORY_TRANSACTION — Sổ cái phiếu (PO / Transfer / Adjustment)
--
--  transaction_type  : IMPORT | TRANSFER | ADJUSTMENT
--  transaction_reason: PURCHASE | PRODUCTION | RESTOCK | LOSS |
--                      STOCKTAKE | SUPPLIER_RETURN | WRITE_OFF
--  status            : PENDING | READY | ACTIVE | REJECTED
--
--  IMPORT   : supplier_id + to_branch_id (nhập vào kho)
--  TRANSFER : from_branch_id + to_branch_id
--  ADJUSTMENT: to_branch_id (kho bị điều chỉnh)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS inventory_transaction (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(30)   NOT NULL,
    transaction_type    VARCHAR(20)   NOT NULL,
    transaction_reason  VARCHAR(30)   NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    transaction_date    DATE          NOT NULL DEFAULT CURRENT_DATE,

    -- Đối tác / Chi nhánh
    supplier_id         UUID          REFERENCES supplier(id),
    from_branch_id      UUID          REFERENCES branch(id),
    to_branch_id        UUID          REFERENCES branch(id),

    -- Tài chính (dùng cho IMPORT)
    total_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    payment_status      payment_status,             -- NULL nếu không phải IMPORT

    -- AdminBase lifecycle (mirrors BaseAdminEntity)
    entity_status       entity_status NOT NULL DEFAULT 'ACTIVE',

    -- Audit
    note                TEXT,
    rejection_reason    TEXT,
    created_by          VARCHAR(100)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    approved_by         VARCHAR(100),
    approved_at         TIMESTAMPTZ,

    CONSTRAINT uq_inv_tx_code        UNIQUE (code),
    CONSTRAINT chk_inv_tx_type       CHECK (transaction_type IN ('IMPORT', 'TRANSFER', 'ADJUSTMENT')),
    CONSTRAINT chk_inv_tx_status     CHECK (status IN ('PENDING', 'READY', 'ACTIVE', 'REJECTED')),
    CONSTRAINT chk_inv_tx_reason     CHECK (transaction_reason IN (
        'PURCHASE', 'PRODUCTION', 'RESTOCK', 'LOSS', 'STOCKTAKE', 'SUPPLIER_RETURN', 'WRITE_OFF'
    ))
);

CREATE INDEX IF NOT EXISTS idx_inv_tx_type_status ON inventory_transaction(transaction_type, status);
CREATE INDEX IF NOT EXISTS idx_inv_tx_date        ON inventory_transaction(transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_inv_tx_from_branch ON inventory_transaction(from_branch_id) WHERE from_branch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inv_tx_to_branch   ON inventory_transaction(to_branch_id)   WHERE to_branch_id   IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inv_tx_supplier    ON inventory_transaction(supplier_id)    WHERE supplier_id    IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 8c. INVENTORY_TRANSACTION_LINE
--
--  item_type = INGREDIENT → item_id = ingredient.id
--  item_type = PRODUCT    → item_id = product.id
--  qty_approved: điền sau khi nhận hàng/duyệt (có thể khác qty_requested)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS inventory_transaction_line (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID          NOT NULL REFERENCES inventory_transaction(id) ON DELETE CASCADE,
    item_id         UUID          NOT NULL,
    item_type       VARCHAR(20)   NOT NULL,          -- INGREDIENT | PRODUCT
    qty_requested   NUMERIC(12,4) NOT NULL,
    qty_approved    NUMERIC(12,4),                   -- NULL = chưa duyệt
    unit            VARCHAR(20)   NOT NULL,
    unit_price      NUMERIC(18,6) NOT NULL DEFAULT 0,
    lot_id          UUID          REFERENCES inventory(id), -- lô bị tác động (khi deduct)
    note            TEXT,
    CONSTRAINT chk_itl_item_type CHECK (item_type IN ('INGREDIENT', 'PRODUCT'))
);

CREATE INDEX IF NOT EXISTS idx_itl_tx   ON inventory_transaction_line(transaction_id);
CREATE INDEX IF NOT EXISTS idx_itl_item ON inventory_transaction_line(item_id, item_type);

-- ---------------------------------------------------------------------------
-- 8d. INVENTORY_TRANSACTION_PAYMENT — Thanh toán nhiều đợt cho IMPORT
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS inventory_transaction_payment (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID          NOT NULL REFERENCES inventory_transaction(id),
    payment_date    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    amount          NUMERIC(18,2) NOT NULL,
    payment_method  VARCHAR(20)   NOT NULL DEFAULT 'CASH', -- CASH | BANK_TRANSFER
    note            TEXT,
    created_by      VARCHAR(100)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_itp_method CHECK (payment_method IN ('CASH', 'BANK_TRANSFER')),
    CONSTRAINT chk_itp_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_itp_tx ON inventory_transaction_payment(transaction_id);

-- ---------------------------------------------------------------------------
-- 9a. DAILY_SHOP_REPORT — Báo cáo hủy cuối ngày của nhân viên Shop
--
--  Nguồn độc lập cho 3-way reconciliation:
--    Variance = qty_pos_sold + qty_destroyed_actual - qty_bep_delivered
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS daily_shop_report (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    report_date              DATE          NOT NULL,
    branch_id                UUID          NOT NULL REFERENCES branch(id),
    item_id                  UUID          NOT NULL,  -- product.id (PRO-*)
    item_type                VARCHAR(20)   NOT NULL DEFAULT 'PRODUCT',
    qty_leftover_theoretical NUMERIC(10,4) NOT NULL,  -- Tồn sổ sách hệ thống tại thời điểm tạo
    qty_destroyed_actual     NUMERIC(10,4) NOT NULL,  -- Nhân viên đếm và đem hủy
    submitted_by             VARCHAR(100)  NOT NULL,
    submitted_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    note                     TEXT
);

CREATE INDEX IF NOT EXISTS idx_dsr_date_branch ON daily_shop_report(report_date, branch_id);
CREATE INDEX IF NOT EXISTS idx_dsr_item        ON daily_shop_report(item_id, report_date DESC);

-- ---------------------------------------------------------------------------
-- 9b. POS_SALES_DATA — Dữ liệu doanh số từ máy POS (upload bởi Chính)
--
--  Nguồn độc lập cho 3-way reconciliation.
--  item_id đã được map từ SKU POS sang product.id qua product_mapping.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pos_sales_data (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_date    DATE          NOT NULL,
    branch_id     UUID          NOT NULL REFERENCES branch(id),
    item_id       UUID          NOT NULL,  -- product.id sau khi map từ SKU
    item_type     VARCHAR(20)   NOT NULL DEFAULT 'PRODUCT',
    qty_sold_pos  NUMERIC(10,4) NOT NULL,
    revenue       NUMERIC(18,2) NOT NULL DEFAULT 0,
    uploaded_by   VARCHAR(100)  NOT NULL,
    uploaded_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_psd_date_branch ON pos_sales_data(sales_date, branch_id);
CREATE INDEX IF NOT EXISTS idx_psd_item        ON pos_sales_data(item_id, sales_date DESC);

-- ---------------------------------------------------------------------------
-- 10. BATCH / IMPORT LOGS
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

-- ---------------------------------------------------------------------------
-- 11. ADMIN FRAMEWORK
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

CREATE INDEX IF NOT EXISTS idx_cmd_req_entity  ON command_request(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_cmd_req_status  ON command_request(status);
CREATE INDEX IF NOT EXISTS idx_cmd_req_created ON command_request(created_at DESC);

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

CREATE INDEX IF NOT EXISTS idx_rev_log_entity  ON entity_revision_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_rev_log_created ON entity_revision_log(created_at DESC);

-- ---------------------------------------------------------------------------
-- 12. AUTH / RBAC
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS user_role (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT uq_user_role_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS user_profile (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(200),
    full_name     VARCHAR(200),
    password_hash VARCHAR(255) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    role_id       UUID         NOT NULL REFERENCES user_role(id),
    branch_id     UUID         REFERENCES branch(id), -- NULL = không bị giới hạn branch
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT uq_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS screen_registry (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    module      VARCHAR(50)  NOT NULL,
    description TEXT,
    sort_order  SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uq_screen_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id      UUID    NOT NULL REFERENCES user_role(id),
    screen_id    UUID    NOT NULL REFERENCES screen_registry(id),
    can_view     BOOLEAN NOT NULL DEFAULT FALSE,
    can_create   BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit     BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete   BOOLEAN NOT NULL DEFAULT FALSE,
    can_approve  BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, screen_id)
);

CREATE TABLE IF NOT EXISTS refresh_token (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES user_profile(id),
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    is_revoked  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON refresh_token(token_hash);

-- ---------------------------------------------------------------------------
-- 13. ACTIVITY LOG
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS activity_log (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
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

-- ---------------------------------------------------------------------------
-- RECONCILIATION VIEW (3-way: BEP → SHOP → POS)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_reconciliation AS
SELECT
    COALESCE(bep.item_id, pos.item_id, rpt.item_id)        AS item_id,
    COALESCE(bep.transaction_date, pos.sales_date, rpt.report_date) AS recon_date,
    COALESCE(bep.to_branch_id, pos.branch_id, rpt.branch_id)        AS branch_id,
    COALESCE(bep.qty_bep_delivered, 0)                               AS qty_bep_delivered,
    COALESCE(pos.qty_pos_sold,      0)                               AS qty_pos_sold,
    COALESCE(rpt.qty_destroyed,     0)                               AS qty_destroyed,
    (COALESCE(pos.qty_pos_sold, 0) + COALESCE(rpt.qty_destroyed, 0))
        - COALESCE(bep.qty_bep_delivered, 0)                         AS variance
FROM
    -- Bếp giao bánh sang Shop (TRANSFER ACTIVE)
    (
        SELECT
            t.transaction_date,
            t.to_branch_id,
            l.item_id,
            SUM(l.qty_approved) AS qty_bep_delivered
        FROM inventory_transaction t
        JOIN inventory_transaction_line l ON l.transaction_id = t.id
        WHERE t.transaction_type   = 'TRANSFER'
          AND t.transaction_reason = 'RESTOCK'
          AND t.status             = 'ACTIVE'
        GROUP BY t.transaction_date, t.to_branch_id, l.item_id
    ) bep
    FULL OUTER JOIN (
        SELECT sales_date, branch_id, item_id, SUM(qty_sold_pos) AS qty_pos_sold
        FROM pos_sales_data
        GROUP BY sales_date, branch_id, item_id
    ) pos ON  pos.item_id    = bep.item_id
          AND pos.sales_date = bep.transaction_date
          AND pos.branch_id  = bep.to_branch_id
    FULL OUTER JOIN (
        SELECT report_date, branch_id, item_id, SUM(qty_destroyed_actual) AS qty_destroyed
        FROM daily_shop_report
        GROUP BY report_date, branch_id, item_id
    ) rpt ON  rpt.item_id     = COALESCE(bep.item_id, pos.item_id)
          AND rpt.report_date = COALESCE(bep.transaction_date, pos.sales_date)
          AND rpt.branch_id   = COALESCE(bep.to_branch_id, pos.branch_id);
