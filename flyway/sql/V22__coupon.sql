-- V22__coupon.sql
-- Promotions module initial schema: a discount coupon code.

CREATE TABLE tb_coupon (
    id              VARCHAR(36)     PRIMARY KEY,
    code            VARCHAR(50)     NOT NULL,
    type            VARCHAR(20)     NOT NULL,
    value           NUMERIC(19, 2)  NOT NULL,
    active          BOOLEAN         NOT NULL,
    valid_from      TIMESTAMP,
    valid_to        TIMESTAMP,
    max_total_uses  INTEGER,
    used_count      INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL
);

-- Coupon codes are unique: enforced in the application AND backstopped here.
CREATE UNIQUE INDEX uk_coupon_code ON tb_coupon (code);

-- "List all coupons" queries: newest first.
CREATE INDEX ix_coupon_created ON tb_coupon (created_at DESC);
