-- Manual rollback for V8__order_checkout_schema.sql.
-- Run only if a rollback is actually needed (drops the two tb_* tables created by V8).
DROP TABLE IF EXISTS tb_order_item;
DROP TABLE IF EXISTS tb_order;
