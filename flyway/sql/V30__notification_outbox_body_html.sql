-- V30__notification_outbox_body_html.sql
-- Phase E: the outbox snapshots an HTML variant alongside the plain-text body at claim
-- time, so the poller can send multipart/alternative (text/plain + text/html) without
-- re-rendering from live order state. Existing rows keep body_html NULL and are sent
-- text-only; new claims always carry both bodies.

ALTER TABLE tb_notification_delivery_log ADD COLUMN body_html TEXT;