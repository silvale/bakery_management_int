-- V57 (02/BE-13, D16): phụ kiện bếp thực dùng theo mẫu lúc Hoàn thành (sửa được), trừ kho ngay lúc đó

CREATE TABLE IF NOT EXISTS delivery_record_accessory (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    delivery_record_id UUID          NOT NULL REFERENCES delivery_record (id),
    cake_design_id     UUID          REFERENCES cake_design (id),            -- null = phụ kiện thêm ngoài bộ, không gắn mẫu
    item_id            UUID          NOT NULL REFERENCES item (id),
    qty_default        NUMERIC(15,4) NOT NULL DEFAULT 0,                     -- bộ phụ kiện của mẫu × số bánh; 0 nếu thêm ngoài bộ
    qty_used           NUMERIC(15,4) NOT NULL,                               -- số bếp thực dùng (đã trừ kho)
    unit               VARCHAR(30)
);
CREATE INDEX IF NOT EXISTS idx_dr_accessory_delivery ON delivery_record_accessory (delivery_record_id);
CREATE INDEX IF NOT EXISTS idx_dr_accessory_design   ON delivery_record_accessory (cake_design_id);

-- true = phụ kiện đã trừ lúc bếp Hoàn thành (kể cả khi bếp không dùng phụ kiện nào) → cửa hàng xác nhận không trừ nữa.
-- false (phiếu hoàn thành trước BE-13) → cửa hàng xác nhận vẫn trừ theo mẫu như cũ.
ALTER TABLE delivery_record ADD COLUMN IF NOT EXISTS accessories_deducted BOOLEAN NOT NULL DEFAULT FALSE;
