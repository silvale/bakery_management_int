-- =============================================================
-- alter_v16_user_branch_scope.sql
-- Data-Scope Authorization: gắn branch_id vào user_profile
--
-- SUPER_ADMIN & KHO_TRUONG → branch_id = NULL (xem tất cả)
-- BEP_TRUONG / BEP_VIEN   → branch_id = UUID của KHO_BEP
-- NHAN_VIEN_BH             → branch_id = UUID của SHOP branch
-- =============================================================

-- 1. Thêm cột branch_id vào user_profile (nullable)
DO $$ BEGIN
    ALTER TABLE user_profile
        ADD COLUMN branch_id UUID REFERENCES branch(id);
EXCEPTION WHEN duplicate_column THEN
    RAISE NOTICE 'Column branch_id already exists in user_profile, skipping.';
END $$;

-- Index để tăng tốc lookup theo branch
CREATE INDEX IF NOT EXISTS idx_user_profile_branch ON user_profile(branch_id);
