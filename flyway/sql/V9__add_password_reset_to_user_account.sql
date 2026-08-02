ALTER TABLE user_account ADD COLUMN password_reset_token VARCHAR(500);
ALTER TABLE user_account ADD COLUMN password_reset_token_expires_at TIMESTAMP;
