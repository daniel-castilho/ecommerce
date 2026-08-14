### 3. `tasks/postgres-fts-benchmark-backlog.md`

```markdown
# Backlog — Postgres FTS Benchmark Evolution

> Concise status board. Full history of the original V25 FTS epic lives in git.

## Epic

**Benchmark-grade Postgres FTS for product catalog** — stored weighted `tsvector`, GIN,
`websearch_to_tsquery`, `ts_rank_cd`, optional headline; no external search engine.

## Status legend

- `[x]` done (as-built before this epic)
- `[ ]` todo
- `[~]` partial / keep with policy

## Already done (baseline)

- [x] Native FTS path with `ts_rank` (v0.17.0)
- [x] GIN index V25 (expression form)
- [x] Prefix `tsquery` + ILIKE fallback
- [x] `ProductSortField.RELEVANCE` default
- [x] EclipseLink positional parameter fix (v0.17.1 / lesson #30)

## Stories

### S1 — STORED weighted search document

- [x] Flyway V31: generated `search_vector` with `setweight` A/B (name+sku / short_description)
- [x] GIN on `search_vector` (`idx_tb_product_search_vector`)
- [x] Retire V25 expression index (single clean cut, `DROP INDEX IF EXISTS`)
- [x] Rollback script (`docs/migrations/V31_rollback.sql`)

### S2 — Query + rank upgrade

- [x] Primary match: `search_vector @@ websearch_to_tsquery`
- [x] Order: `ts_rank_cd(search_vector, tsquery, 32)`
- [x] Preserve RELEVANCE sort wiring in application/UI (default sort unchanged)
- [x] Positional binds only; shared bind order for count + page queries (`FtsBindSlots`)

### S3 — Fallback policy

- [x] Policy: fallback runs only when the primary FTS pass returns 0 hits — prefix tsquery
      (`token:* & token:*`) ORed with ILIKE name/sku (interior-fragment recall);
      stopword-only/punctuation terms degenerate to ILIKE, blank prefix = empty tsquery
- [x] Tests: no silent 500, SKU/digit tokens, interior fragments, prefix recall
- [x] Documented in release notes (v0.20.0)

### S4 — Headline (deferred → explicit debt)

- [ ] Snippet field on search hits — deferred: needs a hit DTO surface for `PageResult<Product>`
      and an XSS-safe render path; not required for the reference FTS path. Promoted to debt.

### S5 — Hardening & docs

- [x] Adapter IT: name ranks above description-only (`ts_rank_cd` weights A > B)
- [x] Adapter IT: quoted phrase via `websearch_to_tsquery`
- [x] Adapter IT: empty/blank query, punctuation-only input, pagination under RELEVANCE
- [x] ArchUnit green (8/8); WAR package
- [x] `docs/releases/v0.20.0.md`, CHANGELOG, README bullet
- [x] Mark this backlog shipped (S1–S3 + S5 shipped; S4 in debt)

## Explicit debt (not blocking)

- [ ] `ts_headline` snippet on search hits (S4 above)
- [ ] `unaccent` + Portuguese configuration (lab image / ops approval)
- [ ] Weight C for long description (`description` is `@Lob`, type varies per environment — same
      reason V25 excluded it)
- [ ] `pg_trgm` “did you mean”
- [ ] Hybrid FTS + embeddings

## Priority

S1 → S2 → S3 → S5 shipped in v0.20.0. S4 moved to debt (not blocking).
```
