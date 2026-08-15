-- V41: Thêm base_unit vào item
-- Dùng kết hợp với unit_size để convert đơn vị đóng gói.
-- Ví dụ: unit=HOP, unit_size=5, base_unit=KG → hệ thống hiểu 1 HOP = 5 KG.
-- Khi công thức dùng G và item lưu theo HOP:
--   factor(G→HOP) = factor(G→KG) / unit_size = 0.001 / 5 = 0.0002
ALTER TABLE item ADD COLUMN IF NOT EXISTS base_unit VARCHAR(20);
