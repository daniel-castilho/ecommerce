### 3. `tasks/wishlist-backlog.md`

```markdown
# Wishlist — Backlog

**Companions:** `wishlist-module-spec.md` · `wishlist-implementation-sequence.md`
**Epic goal:** Customers save products for later and manage a personal wishlist.

**MVP:** S1–S8 · **Optional:** S9–S10

---

## Story map
```

FOUNDATION
S1 Domain + exceptions
S2 Ports + DTOs
S3 Application service
S4 Persistence + Flyway

CUSTOMER FLOW
S5 Add to wishlist
S6 Remove from wishlist
S7 List my wishlist
S8 UI (product-detail toggle + wishlist page)

QUALITY
S9 ArchUnit + auth guards
S10 (Optional) Catalog-card heart icon

```

---

## Stories

| ID | Story | Priority | Notes |
|----|--------|----------|--------|
| S1 | Domain model + exceptions | Must | WishlistItem; unique user+product invariant |
| S2 | Ports in/out + DTOs | Must | ProductLookupPort for ACTIVE + snapshot |
| S3 | WishlistApplicationService | Must | Orchestrates lookup, uniqueness, persistence |
| S4 | JPA + Flyway | Must | Unique index; ITs |
| S5 | Add to wishlist | Must | Auth required; reject non-ACTIVE; idempotent or clear duplicate error |
| S6 | Remove from wishlist | Must | Auth + ownership; idempotent |
| S7 | List my wishlist | Must | Newest first; display DTOs |
| S8 | Public UI | Must | Detail toggle + `wishlist.xhtml` |
| S9 | ArchUnit + security | Must | No cross-adapter imports; server-side userId |
| S10 | Catalog card icon | Should | Same use cases; optional polish |

---

## Definition of Done (epic)

- [x] S1–S10 done
- [x] Smoke: login → add on detail → see on wishlist page → remove
- [x] `mvn clean package -pl web -am` succeeds

---

## Status

**Wishlist epic S1–S10 delivered.** Module lives in `wishlist/` (committed 2026-08-06, `f9e65d0`); the
public UI (S8) and the catalog-card heart icon (S10) landed 2026-08-07 and passed browser smoke
(guest login prompts / login-link hearts, detail toggle with FacesMessage, wishlist list with
name/price/date, remove to empty state, catalog ♥/♡ toggle across reloads).

- **S1** — Domain model + 3 exceptions (`wishlist-item` module; 12 tests)
- **S2** — Ports + DTOs (`WishlistItemDTO`, `ProductSnapshot`; `ProductLookupPort`)
- **S3** — `WishlistApplicationService` (18 tests)
- **S4** — JPA + Flyway `V20`; 12 ITs + mapper tests
- **S5** — Add to wishlist
- **S6** — Remove from wishlist
- **S7** — List my wishlist (newest first)
- **S8** — Public UI: toggle on `product-detail.xhtml` (guests see "Log in to save") + `wishlist/wishlist.xhtml`
- **S9** — `WishlistHexagonalArchitectureTest` (8 rules); userId always from `SessionPort`
- **S10** — Catalog-card heart icon — **done** (2026-08-07): ♥/♡ toggle per card via
  `WishlistBean.toggleFor(product.id)`; guests get a login link; browser-smoke verified

*Planning backlog. Update status lines as stories ship; full historical wording can live in git history once implemented.*
```
