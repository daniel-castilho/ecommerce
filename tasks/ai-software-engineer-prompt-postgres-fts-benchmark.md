### 1. `tasks/ai-software-engineer-prompt-postgres-fts-benchmark.md`

```markdown
# AI Software Engineer Prompt — Postgres FTS Benchmark Evolution

## Role

You are implementing a **benchmark-grade PostgreSQL full-text search** evolution for the
`product-catalog` module of a Jakarta EE 11 hexagonal monolith on Open Liberty.

Follow `AGENTS.md` and `docs/lessons.md` strictly. English for all code, commits, and docs.
Zero new Maven dependencies. Domain stays free of Jakarta/JPA. Prefer Flyway + pure SQL +
existing adapter patterns.

## Context (as-built)

- Catalog text search already exists (v0.17.0 / v0.17.1):
  - Native query with `to_tsvector('english', name || sku || short_description)`
  - Ranking via `ts_rank`
  - Prefix `tsquery` (`token:*`) + **ILIKE fallback** so former LIKE hits are not lost
  - GIN **expression** index in `V25__product_fts.sql`
  - Default sort `ProductSortField.RELEVANCE`
  - Positional JDBC parameters only (EclipseLink does not bind named params in native SQL —
    lesson #30)
- Long description is **not** in the current index (LOB / env variance).
- Payment, shipping, ElasticSearch, and new search engines are **out of scope**.

## Goal

Raise catalog search to a **reference Postgres FTS** implementation still entirely inside
PostgreSQL:

1. **Stored weighted `tsvector` column** (`GENERATED ALWAYS AS … STORED`) with `setweight`
2. **GIN index on that column** (replace or supersede the V25 expression index cleanly)
3. User query via **`websearch_to_tsquery`** (safe for raw input; `"phrase"`, `or`, `-`)
4. Ranking via **`ts_rank_cd`** with normalization (e.g. `32` or `1|32`)
5. Optional **`ts_headline`** snippet **after** filter/limit (never on full candidate set)
6. Keep **prefix / ILIKE fallback** behavior where it still adds value (zero-result or SKU-like
   tokens), documented and tested — do not regress “no former LIKE result lost” without an
   explicit design note in the release notes
7. Preserve hexagonal boundaries, ArchUnit, and EclipseLink-safe positional binds

## Non-goals

- Elasticsearch, Meilisearch, ParadeDB, `pg_search`, or any new extension that is not already
  standard Postgres (optional `unaccent` only if you can enable it safely in this project’s
  Docker/Postgres image and document it; if risky for the lab image, defer `unaccent` and note
  it as debt)
- Semantic / hybrid vector search
- Changing default language to Portuguese unless migration + seed + tests are fully updated and
  behavior is explicit in release notes (default stay: `english`, same as today, unless human
  approves PT config in-session)
- Refactoring unrelated catalog features

## Implementation constraints

- New Flyway version after current head (expect **V30+**; verify latest migration in repo before
  choosing number). Rollback script under `docs/migrations/`.
- Prefer `GENERATED ALWAYS AS (… ) STORED` over application-maintained triggers.
- Weights (suggested):
  - **A**: `name`, `sku`
  - **B**: `short_description`
  - **C**: long description **only if** column type is safely `text` in all envs; otherwise omit
    and document debt (same reason long desc was excluded from V25)
- Adapter native SQL: **positional parameters only**
- `ts_headline` output is not XSS-safe — escape or strip in the web/DTO layer before render
- Tests: domain/application unchanged if pure infra; adapter IT for ranking order, websearch
  phrases, empty query, and EclipseLink-compatible binds; unit tests for any query-building helper
- Docs: release note, CHANGELOG, README Current State one bullet, backlog/spec status

## Acceptance criteria

- [ ] Weighted `search_vector` (or equivalent name) STORED column + GIN
- [ ] Search orders by `ts_rank_cd` relevance when sort is RELEVANCE
- [ ] Name/SKU matches rank above short-description-only matches for the same term
- [ ] `websearch_to_tsquery` used for primary user text path
- [ ] Headline optional but if present: only on page window; sanitized for UI
- [ ] No named parameters in native SQL
- [ ] No new Maven deps
- [ ] Unit + product-catalog IT + ArchUnit green; WAR builds
- [ ] Release notes + CHANGELOG + README updated

## Done when

Catalog search is a clear **reference implementation** of Postgres FTS (stored weighted vector,
GIN, websearch query, cover-density rank) without leaving the monolith stack or breaking
existing storefront flows.
```
