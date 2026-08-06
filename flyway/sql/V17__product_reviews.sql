-- V17__product_reviews.sql
-- Reviews & Ratings module (product-reviews) initial schema.

CREATE TABLE tb_product_review (
    id                  VARCHAR(36)  PRIMARY KEY,
    product_id          VARCHAR(36)  NOT NULL,
    author_id           VARCHAR(36)  NOT NULL,
    rating              SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title               VARCHAR(120),
    body                VARCHAR(2000),
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN')),
    verified_purchase   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL,
    moderated_at        TIMESTAMP,
    rejection_reason    VARCHAR(500),
    version             BIGINT       NOT NULL DEFAULT 0
);

-- One review per (author, product): enforced in the domain AND backstopped here.
CREATE UNIQUE INDEX uk_product_review_author_product
    ON tb_product_review (author_id, product_id);

-- Public product page queries: list + paginate approved reviews for a product.
CREATE INDEX ix_product_review_product_status_created
    ON tb_product_review (product_id, status, created_at DESC);

-- Admin moderation queue: filter by status, newest first.
CREATE INDEX ix_product_review_status_created
    ON tb_product_review (status, created_at DESC);
