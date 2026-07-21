-- V29: Thêm default_qty_per_batch vào production_plan_line
-- Lưu số lượng default mỗi cối cho BATCH_FORMULA lines
-- UI dùng để tính adjustedQty = default_qty_per_batch × num_batches khi admin đổi số cối

ALTER TABLE production_plan_line
    ADD COLUMN default_qty_per_batch INTEGER;

COMMENT ON COLUMN production_plan_line.default_qty_per_batch IS
    'BATCH_FORMULA only: số lượng default mỗi cối. adjustedQty = default_qty_per_batch × num_batches.';
