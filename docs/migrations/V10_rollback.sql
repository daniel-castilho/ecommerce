-- Rollback for V10: drop created_at from tb_order.
ALTER TABLE tb_order DROP COLUMN created_at;
