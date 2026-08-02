-- Add created_at to tb_order so order history can be listed newest-first.
-- Existing rows fall back to CURRENT_TIMESTAMP via the DEFAULT.
-- NOTE: must be registered manually in flyway_schema_history (no runner; see V5/V6/V7).

ALTER TABLE tb_order ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
