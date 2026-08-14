### 2. `tasks/search-discovery-ux-module-spec.md`

```markdown
# Module Spec — Search & Discovery UX (MVP)

> UX layer on `product-catalog` storefront + FTS read path. Historical FTS ranking design:
> git history / `docs/releases/v0.20.0.md`.

## Purpose

Make catalog text search feel intentional: visible relevance (snippet), honest empty
results, readable sort labels, and a clear result count — without changing FTS ranking
semantics established in V31.

## Scope

| In scope                                                  | Out of scope                         |
| --------------------------------------------------------- | ------------------------------------ |
| Page-level `ts_headline` (or equivalent safe highlight)   | `pg_trgm` / did-you-mean             |
| Catalog empty state for zero search hits                  | Search history                       |
| Human labels for `ProductSortField` (or UI layer mapping) | Skeleton loaders                     |
| Result total line on `catalog.xhtml`                      | Sticky filters, infinite scroll      |
| XSS-safe rendering                                        | Admin catalog, product-detail search |

## Architecture

- **Domain:** no new aggregates.
- **Application / ports:** extend search hit read model / repository result type with optional
  `snippet` (String, may contain only safe mark-up or plain text + separate ranges — prefer
  plain text + pre-sanitized HTML produced in adapter with fixed tags).
- **Adapter:** Postgres `ts_headline('english', coalesce(short_description, name, ''), query,
'StartSel=<mark>, StopSel=</mark>, MaxWords=32, MinWords=12, MaxFragments=1')` (tune
  options as needed). Same `websearch_to_tsquery` value as the filter. Positional binds.
- **Web:** `catalog.xhtml` + catalog bean only (unless a shared empty-state facelet already
  exists — reuse).

## Behavior

### Headline

- Computed when search term is non-blank and hit came from search path.
- Omitted on pure browse (no text query).
- Fallback path (prefix/ILIKE) may still show headline if a tsquery is available; otherwise
  omit snippet (name-only is fine).

### Empty state

- Condition: `queryText` non-blank AND `totalCount == 0`.
- Content: title, short explanation, echoed query, button/link “Clear search” resetting term
  and refreshing list.
- Do not show product grid when empty state is active.

### Sort labels

| Enum (illustrative) | UI label                            |
| ------------------- | ----------------------------------- |
| RELEVANCE           | Most relevant                       |
| PRICE_ASC           | Price: low to high                  |
| PRICE_DESC          | Price: high to low                  |
| NAME_ASC            | Name: A–Z                           |
| …                   | Match actual enum constants in code |

Map in bean or small helper; keep enum names in code English.

### Result count

- Use existing total from search/list port.
- Copy examples: “1 product found” / “N products found” when searching; “N products” when
  browsing — pick one consistent pattern.

## Security

- Sanitize headline before `escape="false"`, or use `escape="true"` with non-HTML snippet.
- No user HTML stored.

## Testing

- IT: matching product returns non-null snippet containing mark or expected lexeme context.
- IT/unit: empty search term does not force headline.
- Bean/page: labels resolve; empty state flag when total 0 and term present.
- Regression: FTS ranking order unchanged for RELEVANCE.

## References

- PostgreSQL `ts_headline` options (MaxWords, StartSel, StopSel)
- `docs/releases/v0.20.0.md` debt S4
- `docs/design-system.md` tokens for empty state
```
