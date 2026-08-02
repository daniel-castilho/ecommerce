-- Order checkout schema.
-- Created for the order-checkout module (OrderJpaEntity / OrderItemEmbeddable).
-- Follows the tb_* convention introduced in V7 (tb_product, tb_category).
-- NOTE: must be registered manually in flyway_schema_history (no runner; see V5/V6/V7).

CREATE TABLE tb_order (
    id       VARCHAR(36)   PRIMARY KEY,
    user_id  VARCHAR(36)   NOT NULL,
    status   VARCHAR(20)   NOT NULL DEFAULT 'OPEN'
);

CREATE TABLE tb_order_item (
    order_id    VARCHAR(36)   NOT NULL REFERENCES tb_order (id) ON DELETE CASCADE,
    product_id  VARCHAR(36)   NOT NULL,
    quantity    INTEGER       NOT NULL,
    unit_price  NUMERIC(19,2) NOT NULL
);

CREATE INDEX idx_tb_order_user_id ON tb_order (user_id);
CREATE INDEX idx_tb_order_item_order_id ON tb_order_item (order_id);
