ALTER TABLE user_audit_log ADD COLUMN actor_id VARCHAR(36);
CREATE INDEX idx_audit_actor_id ON user_audit_log (actor_id);
