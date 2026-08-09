DROP INDEX IF EXISTS idx_audit_entity_id;
ALTER TABLE user_audit_log DROP COLUMN IF EXISTS entity_id;
ALTER TABLE user_audit_log DROP COLUMN IF EXISTS entity_type;
