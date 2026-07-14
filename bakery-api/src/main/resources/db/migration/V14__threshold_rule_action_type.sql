-- =============================================================
-- V14__threshold_rule_action_type.sql
-- Refactor threshold rule:
--   produce_qty → action_value (merged field)
--   + thêm action_type: PRODUCE_MORE | FILL_TO_TARGET
-- =============================================================

ALTER TABLE production_threshold_rule
    RENAME COLUMN produce_qty TO action_value;

ALTER TABLE production_threshold_rule
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(20) NOT NULL DEFAULT 'PRODUCE_MORE';
