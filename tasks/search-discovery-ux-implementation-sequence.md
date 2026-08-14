### 4. `tasks/search-discovery-ux-implementation-sequence.md`

```markdown
# Implementation Sequence — Search & Discovery UX (MVP)

## Phase 0 — Recon

1. Read `ProductRepositoryAdapter` FTS page/count SQL (V31 path), hit mapping, catalog bean,
   `catalog.xhtml`, `ProductSortField` (or equivalent).
2. Confirm how total count and sort are already exposed to the UI.
3. Note design tokens used on catalog empty areas if any.

## Phase 1 — Headline (S1)

1. Extend page SELECT (or secondary query limited to page ids) with:
   `ts_headline('english', coalesce(short_description, name, ''), websearch_to_tsquery('english', ?), 'StartSel=<mark>, StopSel=</mark>, MaxWords=32, MinWords=12, MaxFragments=1')`
2. Keep **positional** binds; share query text with existing FTS binds via CTE if it simplifies.
3. Map to hit DTO `snippet`.
4. Implement sanitizer (allow only `<mark>` … `</mark>` or strip all tags for v1 plain snippet).
5. Update `catalog.xhtml` card: show snippet when non-null/non-blank.

## Phase 2 — Empty state + count (S2, S4)

1. Bean: `isSearchEmpty()`, `resultCount` (or reuse total).
2. XHTML: `rendered` empty panel vs grid.
3. Clear-search action clears term and resets list (same as clearing the input + search).
4. Count line above grid when total known.

## Phase 3 — Sort labels (S3)

1. Add label helper or `Map`/method on bean for each sort value used in the select.
2. Replace raw enum text in `f:selectItems` / options.

## Phase 4 — Tests

1. Adapter IT: term search returns snippet for a fixture with matching short_description.
2. Existing RELEVANCE ranking ITs still green.
3. Bean test: empty state true only when term non-blank and total 0.
4. Fast unit + product-catalog IT suite green; ArchUnit green.

## Phase 5 — Docs & release

1. `docs/releases/v0.20.x.md` (or next patch) — UX MVP only; reference FTS v0.20.0 as base.
2. CHANGELOG + README Current State one bullet under catalog/search.
3. Mark backlog stories done; leave debt list.
4. Commit example: `feat(product-catalog): search discovery UX (headline, empty state, labels)`

## Phase 6 — Smoke

1. Search known seed term → snippets visible, count > 0, sort labels readable.
2. Search nonsense → empty state + clear works.
3. Browse without term → no forced empty state, no headline required.
4. Odd input does not 500.

## Stop conditions

- Do not add dependencies or `pg_trgm`.
- Do not redesign the whole catalog layout.
- Do not change V31 ranking weights or fallback policy except to attach headline safely.
```
