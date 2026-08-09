DROP INDEX IF EXISTS idx_audit_actor_id;
ALTER TABLE user_audit_log DROP COLUMN IF EXISTS actor_id;
