-- V26__notification_delivery_log.sql
-- Phase B: audit trail + idempotency for transactional notification emails.
-- One row per business event (ORDER_CONFIRMED:{orderId}, ...). The unique
-- idempotency_key makes a duplicate event a no-op instead of a second email.

CREATE TABLE tb_notification_delivery_log (
    id              VARCHAR(36)  PRIMARY KEY,
    event_type      VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(36)  NOT NULL,
    channel         VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempt_count   INT          NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(120) NOT NULL,
    error_message   VARCHAR(2000),
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX uk_notification_delivery_idempotency
    ON tb_notification_delivery_log (idempotency_key);
