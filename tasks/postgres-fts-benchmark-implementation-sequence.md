### 4. `tasks/postgres-fts-benchmark-implementation-sequence.md`

```markdown
# Implementation Sequence — Postgres FTS Benchmark Evolution

> Ordered steps for OpenCode / human implementers. Verify current Flyway head before picking
> the next version number (do not assume V30 if head already moved).

## Phase 0 — Recon

1. Read `ProductRepositoryAdapter` FTS methods, `ProductSortField`, V25 migration, lesson #30.
2. Confirm product table column names/types for `name`, `sku`, `short_description`, long body.
3. Note latest Flyway version under `db/migration` (or project equivalent).

## Phase 1 — Schema (S1)

1. Add migration `Vxx__product_fts_weighted_stored.sql`:
   - `search_vector tsvector GENERATED ALWAYS AS (setweight… || …) STORED`
   - `CREATE INDEX … ON … USING GIN (search_vector)`
   - `DROP INDEX` for the old V25 expression index (if present)
2. Add `docs/migrations/Vxx_rollback.sql`.
3. Migrate local Postgres; spot-check `search_vector` on seed products.

## Phase 2 — Adapter query (S2–S3)

1. Rewrite FTS native SQL:
   - `WHERE search_vector @@ websearch_to_tsquery('english', ?)`
   - `ORDER BY ts_rank_cd(search_vector, websearch_to_tsquery('english', ?), 32) DESC`
     (bind the same query text twice with positional indices — or use a SQL CTE `q AS (…)`)
2. Prefer a CTE so `websearch_to_tsquery` is evaluated once per statement if it simplifies binds.
3. Keep status / category filters as today.
4. Implement fallback policy explicitly (separate branch or `OR` with care for performance).
5. Count query must use the same filter predicates (no rank needed).

## Phase 3 — Headline (S4, optional)

1. After fetching the page of IDs/rows, either:
   - include `ts_headline(...)` in the page SELECT only, or
   - map snippets in a second tight query by ids
2. Expose snippet on the catalog hit DTO/view.
3. Escape in the Faces layer.

## Phase 4 — Tests

1. IT: term present in name ranks higher than description-only peer.
2. IT: `websearch_to_tsquery` phrase `"…"` returns expected fixture.
3. IT: blank search does not 500; listing still works.
4. IT: pagination stable under RELEVANCE.
5. Fast unit suite + `product-catalog` ITs green.

## Phase 5 — Docs & release

1. Release notes: what changed vs V25, weights, rank function, fallback policy, any debt
   (`unaccent`, long desc).
2. CHANGELOG + README Current State bullet.
3. Update this backlog to shipped; leave debt list.
4. Commit message example:
   `feat(product-catalog): weighted stored FTS + ts_rank_cd (benchmark path)`

## Phase 6 — Smoke

1. `./scripts/run-liberty.sh` + catalog search for known seed terms.
2. Confirm RELEVANCE default and sensible top hit.
3. Confirm no 500 on odd input (`%%%`, empty, very long string).

## Stop conditions

- Do not add Maven dependencies.
- Do not introduce Elastic or external search.
- Do not break guest catalog browsing or existing non-text sorts (price, name, etc.).
```
