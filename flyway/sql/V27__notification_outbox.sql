-- V27__notification_outbox.sql
-- Phase C: the delivery log doubles as a transactional outbox. The email payload
-- (recipient + subject + body) is snapshotted when the event is claimed, inside
-- the business transaction; a scheduled task dispatches it asynchronously so SMTP
-- latency never touches the request thread.

ALTER TABLE tb_notification_delivery_log ADD COLUMN recipient_email VARCHAR(255);
ALTER TABLE tb_notification_delivery_log ADD COLUMN subject VARCHAR(255);
ALTER TABLE tb_notification_delivery_log ADD COLUMN body TEXT;
