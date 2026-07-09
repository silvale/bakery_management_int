-- =============================================================
-- V6__product_mapping.sql
-- Bảng map EX_CODE (POS) → IN_CODE (item.code)
-- 1 item có thể có nhiều EX_CODE (nhiều size, nhiều POS khác nhau)
-- =============================================================

CREATE TABLE IF NOT EXISTS product_mapping (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    item_id         UUID         NOT NULL REFERENCES item (id),
    ex_code         VARCHAR(50)  NOT NULL UNIQUE,
    -- Ngày SX cố định của SKU này: 0=mỗi ngày, 2=T2..7=T7, 8=CN, NULL=không ràng buộc
    production_day  SMALLINT,
    note            VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    approval_status VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    approved_at     TIMESTAMPTZ,
    approved_by     VARCHAR(100),
    rejected_reason VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_product_mapping_item_id ON product_mapping (item_id);
CREATE INDEX IF NOT EXISTS idx_product_mapping_ex_code ON product_mapping (ex_code);
