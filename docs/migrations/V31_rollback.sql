-- V31_rollback.sql
-- Undo V31__product_fts_weighted_stored.sql: restore the V25 expression GIN index and
-- drop the stored weighted vector column.

DROP INDEX IF EXISTS idx_tb_product_search_vector;

ALTER TABLE tb_product DROP COLUMN IF EXISTS search_vector;

CREATE INDEX idx_tb_product_fts
    ON tb_product USING GIN (to_tsvector('english',
        coalesce(name, '') || ' ' || coalesce(sku, '') || ' ' ||
        coalesce(short_description, '')));