-- Product Catalog extension.
-- NOTE: the legacy DB (from the old java-ee-online-shop app) has orphaned tables
-- (product, product_entity, category_entity, product_image_entity, product_category,
-- sequence) that no current code uses. The modern repo convention is tb_* tables
-- (tb_product, tb_order), so V7 CREATES the real tables rather than extending the
-- legacy ones. Legacy tables are left untouched (cleanup is a separate task).

CREATE TABLE tb_product (
    id                VARCHAR(36)   PRIMARY KEY,
    sku               VARCHAR(64)   NOT NULL,
    slug              VARCHAR(160)  NOT NULL,
    name              VARCHAR(200)  NOT NULL,
    short_description VARCHAR(500),
    description       TEXT,
    price             NUMERIC(19,2) NOT NULL,
    compare_at_price  NUMERIC(19,2),
    stock             INTEGER       NOT NULL DEFAULT 0,
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    weight_grams      INTEGER,
    meta_title        VARCHAR(160),
    meta_description  VARCHAR(300),
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_tb_product_sku  UNIQUE (sku),
    CONSTRAINT uk_tb_product_slug UNIQUE (slug)
);

CREATE INDEX idx_tb_product_status ON tb_product (status);

CREATE TABLE tb_product_image (
    id          BIGSERIAL    PRIMARY KEY,
    product_id  VARCHAR(36)  NOT NULL REFERENCES tb_product (id) ON DELETE CASCADE,
    object_key  VARCHAR(512) NOT NULL,
    alt_text    VARCHAR(200),
    position    INTEGER      NOT NULL DEFAULT 0,
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE tb_category (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(160) NOT NULL,
    parent_id  BIGINT       REFERENCES tb_category (id),
    position   INTEGER      NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_tb_category_slug UNIQUE (slug)
);

CREATE TABLE tb_product_category (
    product_id  VARCHAR(36) NOT NULL REFERENCES tb_product (id) ON DELETE CASCADE,
    category_id BIGINT      NOT NULL REFERENCES tb_category (id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, category_id)
);
