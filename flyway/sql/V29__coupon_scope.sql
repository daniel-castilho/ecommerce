-- V29__coupon_scope.sql
-- Optional coupon depth: category/product eligibility scope + per-user redemption cap.
--
-- tb_coupon gains:
--   scope            ALL (whole order) | PRODUCT (only given products) | CATEGORY (only given categories)
--   product_ids      comma-separated product ids, only when scope = PRODUCT
--   category_ids     comma-separated category ids, only when scope = CATEGORY
--   max_uses_per_user optional cap on how many times ONE user may redeem the coupon
--
-- Existing coupons keep their whole-order behavior (scope = ALL, no targets).

ALTER TABLE tb_coupon ADD COLUMN scope            VARCHAR(20) NOT NULL DEFAULT 'ALL';
ALTER TABLE tb_coupon ADD COLUMN product_ids      VARCHAR(2000);
ALTER TABLE tb_coupon ADD COLUMN category_ids     VARCHAR(2000);
ALTER TABLE tb_coupon ADD COLUMN max_uses_per_user INTEGER;

-- Per-user redemption ledger. A row is inserted for every successful redemption;
-- the per-user cap is enforced by counting rows for (coupon, user) before
-- allowing another use.
CREATE TABLE tb_coupon_redemption (
    id          VARCHAR(36)     PRIMARY KEY,
    coupon_id   VARCHAR(36)     NOT NULL REFERENCES tb_coupon (id) ON DELETE CASCADE,
    user_id     VARCHAR(36)     NOT NULL,
    redeemed_at TIMESTAMP       NOT NULL
);

CREATE INDEX ix_coupon_redemption_coupon_user
    ON tb_coupon_redemption (coupon_id, user_id, redeemed_at);
