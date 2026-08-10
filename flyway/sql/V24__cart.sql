-- V24__cart.sql
-- Persistent cart: one active cart per user (MVP), one line per product.
-- Prices are never stored here — the application resolves them live from the
-- catalog at read time.

CREATE TABLE tb_cart (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(36) NOT NULL,
    version     BIGINT      NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

-- MVP keeps a single active cart per user; enforced here as a safety net.
-- The unique index also serves the "my cart" lookup by user.
CREATE UNIQUE INDEX uk_cart_user
    ON tb_cart (user_id);

CREATE TABLE tb_cart_line (
    cart_id     VARCHAR(36) NOT NULL REFERENCES tb_cart(id) ON DELETE CASCADE,
    product_id  VARCHAR(36) NOT NULL,
    quantity    INT         NOT NULL CHECK (quantity >= 1),
    PRIMARY KEY (cart_id, product_id)
);
