-- V22: Thêm qty_cancelled vào daily_report_line
-- Nhân viên nhập số bánh hủy cuối ngày (chỉ áp dụng cho sản phẩm shelf_days = 0)

ALTER TABLE daily_report_line
    ADD COLUMN IF NOT EXISTS qty_cancelled NUMERIC(10,3);
