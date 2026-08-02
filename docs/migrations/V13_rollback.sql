-- Rollback for V13: drop the customer email column.
ALTER TABLE tb_order DROP COLUMN customer_email;
