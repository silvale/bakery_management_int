-- ============================================================
-- PATCH: Thêm cột entity_type còn thiếu vào các bảng admin
-- Chạy file này nếu DB được tạo từ migration cũ (V7 trở về trước)
-- trước khi có entity_type trong command_request / entity_revision_log / activity_log
-- ============================================================

ALTER TABLE command_request
    ADD COLUMN IF NOT EXISTS entity_type VARCHAR(100);

-- Backfill NOT NULL nếu cần (set giá trị tạm cho các row cũ)
UPDATE command_request SET entity_type = 'UNKNOWN' WHERE entity_type IS NULL;

ALTER TABLE command_request
    ALTER COLUMN entity_type SET NOT NULL;

-- -------------------------------------------------------

ALTER TABLE entity_revision_log
    ADD COLUMN IF NOT EXISTS entity_type VARCHAR(100);

UPDATE entity_revision_log SET entity_type = 'UNKNOWN' WHERE entity_type IS NULL;

ALTER TABLE entity_revision_log
    ALTER COLUMN entity_type SET NOT NULL;

-- -------------------------------------------------------

ALTER TABLE activity_log
    ADD COLUMN IF NOT EXISTS entity_type VARCHAR(100);

UPDATE activity_log SET entity_type = 'UNKNOWN' WHERE entity_type IS NULL;

ALTER TABLE activity_log
    ALTER COLUMN entity_type SET NOT NULL;

-- Tạo lại index nếu chưa có
CREATE INDEX IF NOT EXISTS idx_cmd_req_entity  ON command_request(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_rev_log_entity  ON entity_revision_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_al_entity       ON activity_log(entity_type, entity_id);
