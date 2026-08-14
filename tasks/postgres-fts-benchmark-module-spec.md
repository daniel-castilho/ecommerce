### 2. `tasks/postgres-fts-benchmark-module-spec.md`

````markdown
# Module Spec — Postgres FTS Benchmark Evolution (`product-catalog`)

> As-built target for the catalog search upgrade. Historical V25 expression-index design remains
> in git history.

## Purpose

Provide **production-shaped PostgreSQL full-text search** for the product catalog: weighted
document vectors, GIN index, web-style query parsing, cover-density ranking, and optional
highlighted snippets — without external search infrastructure.

## Scope

| In scope                                            | Out of scope                       |
| --------------------------------------------------- | ---------------------------------- |
| `product-catalog` persistence + search API behavior | New modules                        |
| Flyway migration for STORED `tsvector` + GIN        | Payment / shipping / notifications |
| Ranking & sort `RELEVANCE`                          | Elasticsearch / vector hybrid      |
| Optional headline in search hit DTO                 | Admin-only search redesign         |
| ILIKE/prefix fallback policy (documented)           | Multi-language runtime switcher UI |

## Domain / application

- No new domain aggregates required.
- Search remains a **query** use case on existing `Product` read model / repository port.
- Port methods stay framework-free; native SQL stays in the JPA adapter.

## Data model

### Column (illustrative)

```sql
-- name may match project naming (search_vector / fts_document)
search_vector tsvector
  GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(sku, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(short_description, '')), 'B')
    -- optional C: long_description if TEXT everywhere
  ) STORED
```
````

### Index

```sql
CREATE INDEX … ON … USING GIN (search_vector);
```

Drop or replace V25 expression GIN so only one FTS index strategy remains active (document
choice in migration comments).

## Query design

1. **Primary filter:** `search_vector @@ websearch_to_tsquery('english', :q)`
2. **Rank:** `ts_rank_cd(search_vector, websearch_to_tsquery('english', :q), 32)`
   (or `1|32` if length normalization is desired)
3. **Sort:** `RELEVANCE` → `rank DESC`, then stable tie-break (e.g. name/id)
4. **Fallback (policy):**
   - If primary returns empty **or** for SKU-like tokens, optional prefix `tsquery` / ILIKE path
     aligned with v0.17 behavior — must be covered by tests and release notes
5. **Headline (optional):** `ts_headline('english', short_description, query, '…')`
   applied only to rows already selected for the page

## API / DTO

- Existing catalog search endpoint/bean keeps the same external contract where possible.
- Optional new field on hit view: `highlight` / `snippet` (nullable).
- Empty or blank query: existing catalog listing behavior (no FTS rank required).

## Security & correctness

- Positional parameters only in native SQL (EclipseLink).
- Sanitize headline HTML before Faces render.
- Status filter (e.g. ACTIVE only on storefront) unchanged.

## Testing

| Layer      | Focus                                                                 |
| ---------- | --------------------------------------------------------------------- |
| Adapter IT | Weighted rank (name > description), phrase query, empty q, pagination |
| Unit       | Query string builder / bind order helpers                             |
| Smoke      | Catalog search box: term ranks Smartphone-style fixtures first        |

## Observability

No new metrics required. Migration comments must state index rebuild cost for large catalogs.

## References (implementation authority)

- PostgreSQL Chapter 12 (FTS), especially controls, indexes, dictionaries
- Prefer GIN; stored weighted column; `websearch_to_tsquery`; `ts_rank_cd`

```

```
