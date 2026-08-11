-- V28 rollback: remove the backoff gate added for outbox retry scheduling.
ALTER TABLE tb_notification_delivery_log DROP COLUMN next_attempt_at;