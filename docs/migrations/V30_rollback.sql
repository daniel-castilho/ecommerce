-- V30 rollback: remove the HTML email body snapshot from the notification outbox.
ALTER TABLE tb_notification_delivery_log DROP COLUMN IF EXISTS body_html;