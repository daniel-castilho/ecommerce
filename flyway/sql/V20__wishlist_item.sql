-- V20__wishlist_item.sql
-- Wishlist module initial schema: one row per (user, product).

CREATE TABLE tb_wishlist_item (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    product_id  VARCHAR(36)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL
);

-- One wishlist entry per (user, product): enforced in the application AND backstopped here.
CREATE UNIQUE INDEX uk_wishlist_item_user_product
    ON tb_wishlist_item (user_id, product_id);

-- "My wishlist" queries: list a user's items newest first.
CREATE INDEX ix_wishlist_item_user_created
    ON tb_wishlist_item (user_id, created_at DESC);
