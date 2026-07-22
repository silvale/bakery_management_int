-- V33: Enhance command_request for full activity log
-- 1. Add entity_label (human-readable name of affected entity)
-- 2. Add actor_name (username, not userId)

ALTER TABLE command_request
    ADD COLUMN IF NOT EXISTS entity_label VARCHAR(255),
    ADD COLUMN IF NOT EXISTS actor_name   VARCHAR(100);

-- Backfill actor_name = actor for existing rows (actor was userId before, accept as-is)
UPDATE command_request SET actor_name = actor WHERE actor_name IS NULL;

CREATE INDEX IF NOT EXISTS idx_cmd_req_actor_name   ON command_request (actor_name);
CREATE INDEX IF NOT EXISTS idx_cmd_req_action       ON command_request (action);
CREATE INDEX IF NOT EXISTS idx_cmd_req_created_desc ON command_request (created_at DESC);
