-- V20: Thêm qty_produced vào production_request_line
-- qty_produced ban đầu chỉ nằm ở delivery_record, gây khó query.
-- Thêm vào đây để truy vấn trực tiếp không cần JOIN.

ALTER TABLE production_request_line
    ADD COLUMN IF NOT EXISTS qty_produced DECIMAL(15,4);
