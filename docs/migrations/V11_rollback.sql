-- Rollback for V11: restore the 3-state enum and drop the line snapshot columns.
ALTER TABLE tb_order_item DROP COLUMN position;
ALTER TABLE tb_order_item DROP COLUMN product_name;
UPDATE tb_order SET status = 'OPEN' WHERE status = 'PENDING';
