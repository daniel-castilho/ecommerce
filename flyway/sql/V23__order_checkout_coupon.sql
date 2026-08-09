-- V23__order_checkout_coupon.sql
-- Snapshot the coupon applied at checkout onto the order (denormalized).

ALTER TABLE tb_order ADD COLUMN coupon_code VARCHAR(36);
ALTER TABLE tb_order ADD COLUMN discount_amount NUMERIC(19, 2);
