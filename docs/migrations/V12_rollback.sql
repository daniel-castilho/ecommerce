-- Rollback for V12: drop the full-aggregate columns and the status index.
DROP INDEX idx_tb_order_status;

ALTER TABLE tb_order DROP COLUMN payment_status;
ALTER TABLE tb_order DROP COLUMN capture_time;
ALTER TABLE tb_order DROP COLUMN authorization_time;
ALTER TABLE tb_order DROP COLUMN refunded_amount;
ALTER TABLE tb_order DROP COLUMN captured_amount;
ALTER TABLE tb_order DROP COLUMN authorized_amount;
ALTER TABLE tb_order DROP COLUMN gateway_transaction_id;
ALTER TABLE tb_order DROP COLUMN capture_id;
ALTER TABLE tb_order DROP COLUMN authorization_id;
ALTER TABLE tb_order DROP COLUMN payment_method;

ALTER TABLE tb_order DROP COLUMN phone_number;
ALTER TABLE tb_order DROP COLUMN postal_code;
ALTER TABLE tb_order DROP COLUMN state;
ALTER TABLE tb_order DROP COLUMN city;
ALTER TABLE tb_order DROP COLUMN neighborhood;
ALTER TABLE tb_order DROP COLUMN complement;
ALTER TABLE tb_order DROP COLUMN number;
ALTER TABLE tb_order DROP COLUMN street;
ALTER TABLE tb_order DROP COLUMN recipient_name;

ALTER TABLE tb_order DROP COLUMN tracking_number;
ALTER TABLE tb_order DROP COLUMN shipping_cost;
ALTER TABLE tb_order DROP COLUMN updated_at;
