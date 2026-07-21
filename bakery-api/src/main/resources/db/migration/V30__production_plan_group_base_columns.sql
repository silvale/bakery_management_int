-- V30: Thêm các cột base entity còn thiếu vào production_plan_group
-- ProductionPlanGroup extends BaseEntity nên cần đủ tất cả cột audit + approval

ALTER TABLE production_plan_group
    ADD COLUMN IF NOT EXISTS created_by     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_by     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS approved_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS rejected_reason VARCHAR(500);

COMMENT ON COLUMN production_plan_group.status          IS 'Luôn ACTIVE — dùng để tương thích với BaseEntity.';
COMMENT ON COLUMN production_plan_group.approval_status IS 'Không dùng cho approval flow — chỉ để tương thích BaseEntity (default DRAFT).';
