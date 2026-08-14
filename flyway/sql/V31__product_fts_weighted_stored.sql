-- V31__product_fts_weighted_stored.sql
-- Harden catalog full-text search to a reference PostgreSQL implementation
-- (postgres-fts-benchmark epic): a stored, weighted tsvector column + GIN index,
-- letting queries use `websearch_to_tsquery` and `ts_rank_cd` over an indexed column
-- instead of an inline to_tsvector expression (V25).
--
-- Weights:
--   A = name, sku        (the most specific text a customer searches)
--   B = short_description
--   (C) long description  = intentionally NOT indexed: `description` is a @Lob column
--       whose physical type varies between environments (see adapter javadoc / V25);
--       omitting it keeps the generated column portable. Promoted as explicit debt.
--
-- Backfill note: with a GENERATED ... STORED column the vector is computed and stored
-- on insert/update by PostgreSQL; existing rows are backfilled by the ALTER. For very
-- large catalogs ALTER + GIN build holds a table rewrite — estimate before running in
-- production (same cost as the V25 expression index, now done once at rest instead of
-- per query for the rank part).

ALTER TABLE tb_product
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(sku, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(short_description, '')), 'B')
        ) STORED;

CREATE INDEX idx_tb_product_search_vector
    ON tb_product USING GIN (search_vector);

-- Retire the V25 expression index (single clean cut): only one FTS index strategy
-- stays active, the stored-column GIN above.
DROP INDEX IF EXISTS idx_tb_product_fts;