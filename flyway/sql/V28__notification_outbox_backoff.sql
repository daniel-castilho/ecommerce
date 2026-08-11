-- V28__notification_outbox_backoff.sql
-- Phase D: backoff scheduling + exhaustion. next_attempt_at gates when a FAILED row may
-- be polled again (exponential-ish backoff); a delivery that fails MAX_ATTEMPTS times is
-- escalated to EXHAUSTED and never polled again (visible in the admin delivery log for
-- manual resend).
-- Existing rows are backfilled so anything still pending/retryable is due immediately.

ALTER TABLE tb_notification_delivery_log ADD COLUMN next_attempt_at TIMESTAMP;

UPDATE tb_notification_delivery_log SET next_attempt_at = created_at
 WHERE next_attempt_at IS NULL;
