-- Manual rollback for V9__add_password_reset_to_user_account.sql.
-- Run only if a rollback is actually needed (drops the two columns added by V9).
ALTER TABLE user_account DROP COLUMN IF EXISTS password_reset_token_expires_at;
ALTER TABLE user_account DROP COLUMN IF EXISTS password_reset_token;
