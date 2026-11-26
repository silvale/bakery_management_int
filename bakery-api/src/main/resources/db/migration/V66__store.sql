-- V66 (23/BE-1): cửa hàng chỉ là nhóm để gom kho khi hiển thị; không đổi cách bán hàng / sản xuất (22 §5.3).

CREATE TABLE store (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(20)  NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    -- BaseEntity audit
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    approval_status  VARCHAR(30)  NOT NULL DEFAULT 'APPROVED',
    approved_at      TIMESTAMPTZ,
    approved_by      VARCHAR(100),
    rejected_reason  VARCHAR(500)
);

ALTER TABLE warehouse ADD COLUMN store_id UUID REFERENCES store(id) ON DELETE RESTRICT;
CREATE INDEX idx_warehouse_store ON warehouse (store_id);

-- Nơi nhận ngoài không thuộc cửa hàng nào
ALTER TABLE warehouse ADD CONSTRAINT ck_warehouse_external_no_store CHECK (kind = 'INTERNAL' OR store_id IS NULL);

-- Seed theo mã (chạy được cả trên production): 4 kho hiện có vào Cửa hàng 1; kho khác để null
INSERT INTO store (code, name, sort_order) VALUES ('CH1', 'Cửa hàng 1', 10);
UPDATE warehouse SET store_id = (SELECT id FROM store WHERE code = 'CH1')
 WHERE code IN ('MAIN', 'KITCHEN', 'COLD', 'SHOP') AND kind = 'INTERNAL';
