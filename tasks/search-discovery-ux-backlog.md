### 3. `tasks/search-discovery-ux-backlog.md`

```markdown
# Backlog — Search & Discovery UX (MVP)

> Concise board. FTS engine work is **done** (v0.20.0). This epic is storefront UX only.

## Epic

**Search & discovery UX (MVP)** — headline snippet, empty state, human sort labels, result count.

## Status

- [x] Benchmark FTS (V31, websearch, ts_rank_cd, fallback) — prerequisite
- [x] S1 — Headline on search hits (safe render)
- [x] S2 — Empty state for zero hits
- [x] S3 — Human sort labels
- [x] S4 — Result count line
- [x] S5 — Tests, release notes, README

## Stories

### S1 — Headline / highlight

- [x] Adapter: `ts_headline` on page query (or by-id follow-up)
- [x] Hit DTO/view field `snippet` (optional)
- [x] Catalog card/list renders snippet under name when present
- [x] XSS policy enforced

### S2 — Empty state

- [x] Detect non-blank query + zero total
- [x] Empty state markup + clear-search action
- [x] Hide grid when empty state shown

### S3 — Sort labels

- [x] Label map for all catalog sort options used in UI
- [x] Dropdown shows labels, submits enum values

### S4 — Result count

- [x] Count line bound to existing total
- [x] Sensible singular/plural (English UI)

### S5 — Hardening & docs

- [x] Tests for snippet + empty path
- [x] No regression on RELEVANCE ranking ITs
- [x] Release note, CHANGELOG, README
- [x] Mark MVP shipped

## Explicit debt (not this epic)

- [ ] Did-you-mean / `pg_trgm`
- [ ] Search history
- [ ] Skeleton loading
- [ ] Sticky filters
- [ ] `unaccent` / PT config
- [ ] Long-description weight C

## Priority

S1 → S2 → S3 → S4 → S5 (S3/S4 may ship in parallel with S2 if low risk).
```
