-- V35: Bánh Hủy redesign
-- Thêm 3 cột mới vào daily_report_line cho flow hủy bánh mới

ALTER TABLE daily_report_line
    ADD COLUMN IF NOT EXISTS qty_remaining_opening NUMERIC(10, 3),
    ADD COLUMN IF NOT EXISTS qty_system_cancel      NUMERIC(10, 3),
    ADD COLUMN IF NOT EXISTS qty_system_remaining   NUMERIC(10, 3);

COMMENT ON COLUMN daily_report_line.qty_remaining_opening IS
    'Tồn đầu ngày = qty_remaining_actual của ngày hôm trước (cùng item)';

COMMENT ON COLUMN daily_report_line.qty_system_cancel IS
    'Số bánh hủy hệ thống tính = tổng SHOP stock_lot còn lại của các lô hết HSD trong ngày';

COMMENT ON COLUMN daily_report_line.qty_system_remaining IS
    'Còn lại theo HT = qty_remaining_opening + qty_received - qty_sold_pos - qty_system_cancel';

ALTER TABLE daily_report_line
    ADD COLUMN IF NOT EXISTS discrepancy_remaining NUMERIC(10, 3);

COMMENT ON COLUMN daily_report_line.discrepancy_remaining IS
    'Chênh lệch còn lại = qty_remaining_actual - qty_system_remaining; âm = mất hàng, dương = thừa';
