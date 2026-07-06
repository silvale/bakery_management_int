-- V13: Cập nhật GoodsTransfer flow mới
-- PENDING → READY → COMPLETED / REJECTED
-- ADJUSTMENT: to_branch_id nullable, Chính duyệt

-- 1. to_branch_id → nullable (ADJUSTMENT không có kho nhận)
ALTER TABLE goods_transfer ALTER COLUMN to_branch_id DROP NOT NULL;

-- 2. Status constraint mới
ALTER TABLE goods_transfer DROP CONSTRAINT IF EXISTS chk_gt_status;
ALTER TABLE goods_transfer ADD CONSTRAINT chk_gt_status
    CHECK (status IN ('PENDING','READY','COMPLETED','REJECTED','CANCELLED'));

-- 3. Thêm ready_by/ready_at (Cường mark READY)
ALTER TABLE goods_transfer
    ADD COLUMN IF NOT EXISTS ready_by  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ready_at  TIMESTAMPTZ;

-- 4. Thêm rejected_by/rejected_at (BEP reject với lý do)
ALTER TABLE goods_transfer
    ADD COLUMN IF NOT EXISTS rejected_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ;

-- confirmed_by/confirmed_at giữ nguyên → dùng cho BEP approve (COMPLETED)

-- 5. goods_transfer_line: qty_from_recipe (trước rounding)
ALTER TABLE goods_transfer_line
    ADD COLUMN IF NOT EXISTS qty_from_recipe NUMERIC(18,4);

-- 6. Update comment status
COMMENT ON COLUMN goods_transfer.status IS
'PENDING   : chờ Cường chuẩn bị (thấy ở KHO_TONG)
 READY     : Cường đã chuẩn bị, chờ BEP nhận (thấy ở KHO_BEP)
 COMPLETED : BEP đã nhận, kho đã cập nhật (atomic)
 REJECTED  : BEP từ chối có lý do → thấy ở KHO_TONG rejected
 CANCELLED : hủy trước khi READY';

SELECT 'V13 migration done' AS result;

-- 7. cloned_from_id — trace phiếu clone từ phiếu REJECTED
ALTER TABLE goods_transfer
    ADD COLUMN IF NOT EXISTS cloned_from_id UUID REFERENCES goods_transfer(id);
