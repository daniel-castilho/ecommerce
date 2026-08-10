-- V25__product_fts.sql
-- PostgreSQL full-text search over the catalog (FTS epic).
-- Expression GIN index backing the search ranking in ProductRepositoryAdapter.
-- The index expression MUST stay identical to the FTS_EXPRESSION SQL fragment
-- built in the adapter (table aliases are equivalent for index matching), so
-- that ts_rank / @@ queries can use this index. Long description is not
-- indexed: it is a LOB column whose type varies between environments.

CREATE INDEX idx_tb_product_fts
    ON tb_product
    USING GIN (to_tsvector('english',
        coalesce(name, '') || ' ' || coalesce(sku, '') || ' ' ||
        coalesce(short_description, '')));
