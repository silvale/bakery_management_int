-- V19: Thêm approved_at, approved_by, rejected_reason vào các table V8
-- production_request_line và delivery_record được tạo ở V8 trước khi BaseEntity
-- bổ sung 3 cột approval này → Hibernate INSERT thất bại khi deploy mới.

ALTER TABLE production_request_line
    ADD COLUMN IF NOT EXISTS approved_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS rejected_reason VARCHAR(500);

ALTER TABLE delivery_record
    ADD COLUMN IF NOT EXISTS approved_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS rejected_reason VARCHAR(500);
