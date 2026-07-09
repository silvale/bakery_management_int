-- =============================================================
-- V4__command_request.sql
-- Audit log for every state-changing admin operation
-- =============================================================

CREATE TABLE IF NOT EXISTS command_request (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    entity_name   VARCHAR(100) NOT NULL,               -- e.g. "Ingredient", "Product"
    entity_id     UUID,                                 -- null on CREATE before first save
    action        VARCHAR(20)  NOT NULL,               -- CREATE | UPDATE | DELETE | APPROVE | REJECT
    actor         VARCHAR(100),                        -- username who triggered
    note          VARCHAR(500),                        -- e.g. rejection reason
    status        VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS', -- SUCCESS | FAILED
    error_detail  VARCHAR(1000),                       -- filled on FAILED
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cmd_req_entity       ON command_request (entity_name, entity_id);
CREATE INDEX IF NOT EXISTS idx_cmd_req_actor        ON command_request (actor);
CREATE INDEX IF NOT EXISTS idx_cmd_req_created_at   ON command_request (created_at DESC);
