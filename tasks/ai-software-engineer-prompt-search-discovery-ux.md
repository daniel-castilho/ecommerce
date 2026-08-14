### 1. `tasks/ai-software-engineer-prompt-search-discovery-ux.md`

```markdown
# AI Software Engineer Prompt — Search & Discovery UX (MVP)

## Role

You implement a **narrow UX polish** epic on top of the existing Postgres FTS benchmark
(v0.20.0 / V31) for the product catalog storefront of a Jakarta EE 11 hexagonal monolith
on Open Liberty (JSF / Facelets).

Follow `AGENTS.md` and `docs/lessons.md`. English for code, commits, and docs.
Zero new Maven dependencies. Domain stays free of Jakarta. Design-system tokens only
(no one-off colors). Do not expand into payment, shipping, or new search engines.

## Context (as-built)

- Catalog FTS is **reference-grade**: STORED weighted `search_vector` (A = name/sku, B =
  short_description), GIN, `websearch_to_tsquery`, `ts_rank_cd(…, 32)`, prefix+ILIKE
  fallback only when primary FTS returns zero hits (V31).
- Storefront page: `web/.../product-catalog/catalog.xhtml` driven by the existing catalog
  bean / search use path.
- Explicit debt from v0.20.0: **`ts_headline` on search hits** (DTO + XSS-safe render).
- Reviews, wishlist hearts, add-to-cart on cards may already exist — **do not regress**.

## Goal (MVP only)

Improve **search & discovery UX** with exactly these outcomes:

1. **Headline / highlight** — when a non-blank search term is active, each hit can show a
   short snippet from `ts_headline` (prefer short_description; fall back sensibly). Render
   marks in a **XSS-safe** way on the catalog UI.
2. **Empty state** — when the user submitted a non-blank query and the result set is empty,
   show a clear empty state (message + the query echoed + control to clear search / browse
   all), not a blank grid.
3. **Human sort labels** — sort dropdown (or equivalent) uses readable labels
   (e.g. “Most relevant”, “Price: low to high”), not raw enum names.
4. **Result count** — when a search (or filtered list) is shown, display a simple count line
   such as “N products found” / “N products” consistent with pagination totals already
   available from the query path.

## Explicit non-goals (do not implement in this epic)

- “Did you mean” / `pg_trgm` / fuzzy suggestions
- Search history (session/localStorage)
- Skeleton/shimmer loading
- Sticky filter chrome or major catalog layout redesign
- `unaccent` / Portuguese FTS config
- Indexing long description
- Elasticsearch or any new dependency
- Admin search changes

## Design rules

1. **Headline only on the current page of hits** — never run `ts_headline` over the full
   candidate set before LIMIT. Prefer including it in the page SELECT or a tight follow-up
   by ids.
2. **XSS** — `ts_headline` HTML is not safe. Whitelist tags (e.g. only `<b>`/`<mark>` from
   your StartSel/StopSel) or escape and apply marks in Java with escaped text. Never bind
   raw headline into `escape="false"` without sanitization.
3. **Positional parameters only** in any native SQL (EclipseLink lesson #30).
4. Reuse design tokens for empty-state spacing/typography; keep catalog card structure.
5. Blank query / default browse: no headline required; empty state for “no products at all”
   may stay as today or share the same component — do not break non-search listing.
6. Tests: adapter/IT for headline present when term matches; UI/bean tests if the project
   already tests beans; no 500 on odd queries (`!!!`, blank, long strings).

## Acceptance criteria

- [ ] Active text search shows optional snippet/highlight on hits (or name mark) without XSS risk
- [ ] Zero hits + non-blank query → dedicated empty state with clear-search path
- [ ] Sort options show human-readable labels
- [ ] Result count visible and consistent with total used for pagination
- [ ] No new Maven deps; ArchUnit green; product-catalog tests green; WAR builds
- [ ] Release note + CHANGELOG + README bullet; backlog marked shipped for MVP stories

## Done when

A shopper can search, see **why** a product matched (snippet), understand **how many**
results, read **sort** labels, and recover gracefully from **no results** — without any
scope creep into fuzzy search or layout rewrites.
```
