-- V51: kho mặc định của mặt hàng (tùy chọn, chỉ để điền sẵn kho nhận khi lập phiếu)
ALTER TABLE item     ADD COLUMN IF NOT EXISTS default_warehouse_id UUID REFERENCES warehouse(id);
ALTER TABLE item_his ADD COLUMN IF NOT EXISTS default_warehouse_id UUID;

CREATE INDEX IF NOT EXISTS idx_item_default_warehouse
    ON item (default_warehouse_id) WHERE default_warehouse_id IS NOT NULL;
