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

- [ ] S1–S9 done
- [ ] Smoke: login → add on detail → see on wishlist page → remove
- [ ] `mvn clean package -pl web -am` succeeds

---

*Planning backlog. Update status lines as stories ship; full historical wording can live in git history once implemented.*
```
