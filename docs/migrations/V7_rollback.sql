-- Manual rollback for V7__product_catalog_extension.sql.
-- Run only if a rollback is actually needed (drops the four tb_* tables created by V7).
DROP TABLE IF EXISTS tb_product_category;
DROP TABLE IF EXISTS tb_product_image;
DROP TABLE IF EXISTS tb_category;
DROP TABLE IF EXISTS tb_product;
