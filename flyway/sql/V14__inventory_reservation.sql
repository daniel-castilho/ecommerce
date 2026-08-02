-- S10: inventory reservation table. Each row is a hold of `quantity` units of a
-- product for a given checkout (reservation_id = the order id). The reserved
-- units are already subtracted from tb_product.stock; releasing the hold (order
-- cancelled, payment failed, or expiry) adds them back.
-- NOTE: must be registered manually in flyway_schema_history (no runner; see V5-V13).

CREATE TABLE inventory_reservation (
    id BIGSERIAL PRIMARY KEY,
    reservation_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_inventory_reservation_product UNIQUE (reservation_id, product_id)
);

CREATE INDEX idx_inventory_reservation_expiry ON inventory_reservation (expires_at);
CREATE INDEX idx_inventory_reservation_product ON inventory_reservation (product_id);
