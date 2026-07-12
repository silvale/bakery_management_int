-- V9: Daily report, POS sales, production adjustment
-- ─────────────────────────────────────────────────

-- 1. Điều chỉnh sản lượng (bếp complete lệch plannedQty, hoặc admin sửa sau)
CREATE TABLE production_adjustment (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_record_id UUID NOT NULL REFERENCES delivery_record(id),
    adjustment_type  VARCHAR(30) NOT NULL,   -- INGREDIENT_VARIANCE | PRODUCTION_WASTAGE
    source           VARCHAR(30) NOT NULL,   -- KITCHEN_COMPLETE | ADMIN_CORRECTION
    original_qty     NUMERIC(10,3) NOT NULL,
    adjusted_qty     NUMERIC(10,3) NOT NULL,
    delta            NUMERIC(10,3) NOT NULL, -- adjusted_qty - original_qty
    reason           VARCHAR(500),
    approval_status  VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_at      TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    rejected_reason  VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100)
);

-- 2. Dữ liệu bán hàng từ máy POS
CREATE TABLE pos_daily_sale (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_date    DATE NOT NULL,
    ex_code      VARCHAR(100) NOT NULL,  -- mã EX từ POS
    item_name    VARCHAR(255),
    qty_sold     NUMERIC(10,3) NOT NULL,
    unit_price   NUMERIC(15,2),          -- total_amount / qty_sold
    total_amount NUMERIC(15,2),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   VARCHAR(100)
);

CREATE INDEX idx_pos_daily_sale_date ON pos_daily_sale(sale_date);

-- 3. Header báo cáo ngày
CREATE TABLE daily_report (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_date    DATE NOT NULL UNIQUE,
    status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT | FINALIZED
    note           VARCHAR(500),
    finalized_at   TIMESTAMPTZ,
    finalized_by   VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100)
);

-- 4. Chi tiết báo cáo ngày theo từng sản phẩm
CREATE TABLE daily_report_line (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    daily_report_id       UUID NOT NULL REFERENCES daily_report(id),
    item_id               UUID NOT NULL REFERENCES item(id),

    -- Số liệu sản xuất (snapshot từ DeliveryRecord)
    qty_produced          NUMERIC(10,3),   -- tổng qtyProduced sau adjust
    qty_received          NUMERIC(10,3),   -- tổng qtyReceived shop xác nhận

    -- Số liệu cửa hàng
    qty_remaining_actual  NUMERIC(10,3),   -- nhân viên nhập tay cuối ngày
    qty_sold_implied      NUMERIC(10,3),   -- qty_received - qty_remaining_actual

    -- Số liệu POS
    qty_sold_pos          NUMERIC(10,3),   -- từ pos_daily_sale (sau map EX_CODE)

    -- Đối chiếu
    discrepancy_kitchen   NUMERIC(10,3),   -- qty_produced - qty_received
    discrepancy_pos       NUMERIC(10,3),   -- qty_sold_implied - qty_sold_pos

    -- Snapshot giá tại thời điểm chốt
    unit_cost             NUMERIC(15,2),
    selling_price         NUMERIC(15,2),

    note                  VARCHAR(500),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (daily_report_id, item_id)
);
