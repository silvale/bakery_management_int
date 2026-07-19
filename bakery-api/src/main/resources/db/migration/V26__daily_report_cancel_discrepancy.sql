-- V26: Thêm discrepancy_cancel vào daily_report_line
-- Lưu chênh lệch giữa qty_cancelled (NV nhập) và qty_remaining_actual (hệ thống dự kiến hủy)
-- Chỉ có giá trị sau FINALIZE với các sản phẩm hết HSD trong ngày

ALTER TABLE daily_report_line
    ADD COLUMN IF NOT EXISTS discrepancy_cancel NUMERIC(10, 3);

COMMENT ON COLUMN daily_report_line.discrepancy_cancel IS
    'qty_cancelled - qty_remaining_actual: âm = NV hủy ít hơn dự kiến, dương = hủy nhiều hơn';
