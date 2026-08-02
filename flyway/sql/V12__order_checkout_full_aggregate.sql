-- S2: persist the full Order aggregate on tb_order.
-- Adds the shipping address, shipping cost, tracking number, payment state and
-- updated_at columns required by the S1 domain model (OrderJpaEntity embeddables).
-- NOTE: must be registered manually in flyway_schema_history (no runner; see V5/V6/V7).

ALTER TABLE tb_order ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE tb_order ADD COLUMN shipping_cost NUMERIC(19,2);
ALTER TABLE tb_order ADD COLUMN tracking_number VARCHAR(64);

ALTER TABLE tb_order ADD COLUMN recipient_name VARCHAR(120);
ALTER TABLE tb_order ADD COLUMN street VARCHAR(255);
ALTER TABLE tb_order ADD COLUMN number VARCHAR(20);
ALTER TABLE tb_order ADD COLUMN complement VARCHAR(120);
ALTER TABLE tb_order ADD COLUMN neighborhood VARCHAR(120);
ALTER TABLE tb_order ADD COLUMN city VARCHAR(120);
ALTER TABLE tb_order ADD COLUMN state VARCHAR(2);
ALTER TABLE tb_order ADD COLUMN postal_code VARCHAR(9);
ALTER TABLE tb_order ADD COLUMN phone_number VARCHAR(30);

ALTER TABLE tb_order ADD COLUMN payment_method VARCHAR(20);
ALTER TABLE tb_order ADD COLUMN authorization_id VARCHAR(64);
ALTER TABLE tb_order ADD COLUMN capture_id VARCHAR(64);
ALTER TABLE tb_order ADD COLUMN gateway_transaction_id VARCHAR(255);
ALTER TABLE tb_order ADD COLUMN authorized_amount NUMERIC(19,2);
ALTER TABLE tb_order ADD COLUMN captured_amount NUMERIC(19,2);
ALTER TABLE tb_order ADD COLUMN refunded_amount NUMERIC(19,2);
ALTER TABLE tb_order ADD COLUMN authorization_time TIMESTAMP;
ALTER TABLE tb_order ADD COLUMN capture_time TIMESTAMP;
ALTER TABLE tb_order ADD COLUMN payment_status VARCHAR(20);

CREATE INDEX idx_tb_order_status ON tb_order (status);
