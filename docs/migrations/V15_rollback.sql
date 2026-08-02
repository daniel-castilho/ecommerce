-- Rollback for V15: drop the optimistic-lock version column on tb_order.
ALTER TABLE tb_order DROP COLUMN IF EXISTS version;
