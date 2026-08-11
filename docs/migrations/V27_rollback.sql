ALTER TABLE tb_notification_delivery_log DROP COLUMN IF EXISTS body;
ALTER TABLE tb_notification_delivery_log DROP COLUMN IF EXISTS subject;
ALTER TABLE tb_notification_delivery_log DROP COLUMN IF EXISTS recipient_email;