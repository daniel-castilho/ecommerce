ALTER TABLE user_audit_log ADD COLUMN entity_type VARCHAR(50);
ALTER TABLE user_audit_log ADD COLUMN entity_id VARCHAR(100);
CREATE INDEX idx_audit_entity_id ON user_audit_log (entity_id);
