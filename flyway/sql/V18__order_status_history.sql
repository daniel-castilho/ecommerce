-- Timeline entries for the order status history (admin order detail, S18/S19
-- debt). One row per recorded transition; seeded with "Order placed" when the
-- order is created and appended on every status change (see OrderTimelineEntry).
-- NOTE: must be registered manually in flyway_schema_history (no runner; see V5-V17).

CREATE TABLE order_status_history (
    order_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    label VARCHAR(120) NOT NULL,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES tb_order (id)
);

CREATE INDEX idx_order_status_history_order ON order_status_history (order_id);
CREATE INDEX idx_order_status_history_occurred_at ON order_status_history (occurred_at);
