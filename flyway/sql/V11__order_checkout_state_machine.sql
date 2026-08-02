-- S1: order state machine + line snapshot columns.
-- The order status enum moved from OPEN/CONFIRMED/CANCELLED to the 7-state
-- machine (PENDING is the new initial state). Line items now carry a product
-- name snapshot and a stable position for ordering.
-- NOTE: must be registered manually in flyway_schema_history (no runner; see V5/V6/V7).

UPDATE tb_order SET status = 'PENDING' WHERE status = 'OPEN';

ALTER TABLE tb_order_item ADD COLUMN product_name VARCHAR(255);
ALTER TABLE tb_order_item ADD COLUMN position INTEGER;
