### 2. `tasks/wishlist-module-spec.md`

```markdown
# Wishlist Module — Technical Specification

**Status:** Draft for implementation (not yet built)
**Module:** `wishlist` · **Package:** `com.loja.wishlist`
**Companions:** `wishlist-backlog.md` · `wishlist-implementation-sequence.md`

---

## 1. Purpose & scope

Personal product shortlist per customer.

**In scope (MVP):**

- Add product to wishlist (authenticated)
- Remove product
- List current user’s items (newest first)
- Product existence / ACTIVE check via catalog port
- Snapshot fields for display (name, slug, price, primary image URL if port provides them)

**Out of scope:**

- Multiple named lists / folders
- Public / shared wishlists
- Guest wishlist (session-only) — deferred
- Price-drop notifications
- “Add all to cart” bulk action (optional later)
- Admin management UI

---

## 2. Architecture
```

com.loja.wishlist/
├── domain/
│ ├── model/ → WishlistItem (or Wishlist + items)
│ ├── port/in/ → Add, Remove, ListMyWishlist
│ ├── port/out/ → WishlistRepositoryPort, ProductLookupPort
│ └── exception/ → DuplicateWishlistItemException, ProductNotAvailableException, …
├── application/
│ ├── service/ → WishlistApplicationService
│ └── dto/
└── adapter/
├── in/web/ → WishlistBean (+ optional fragment on product-detail)
└── out/ → JPA adapter + ProductLookupAdapter

```

Depends only on **ports** of `product-catalog` (and session/current user from `user-account` at the web edge).

---

## 3. Domain model

**WishlistItem** (simple aggregate is enough for MVP):

| Field | Notes |
|-------|--------|
| id | UUID string (project convention) |
| userId | Owner |
| productId | Target product |
| createdAt | Instant |

**Invariants:**
- One row per `(userId, productId)`
- Only ACTIVE products may be added (check via `ProductLookupPort`)
- Remove is idempotent

Optional richer model (Wishlist aggregate with collection) is unnecessary for MVP.

**Exceptions:** `DuplicateWishlistItemException`, `WishlistItemNotFoundException`, `ProductNotAvailableException`.

---

## 4. Ports

**Inbound:**
- `AddToWishlistUseCase`
- `RemoveFromWishlistUseCase`
- `ListMyWishlistUseCase`

**Outbound:**
- `WishlistRepositoryPort` — save, delete, findByUser, exists(user, product)
- `ProductLookupPort` — exists/active + display snapshot (name, slug, price, image URL)

---

## 5. Persistence

- Table e.g. `tb_wishlist_item`
- Unique `(user_id, product_id)`
- Index on `user_id` + `created_at DESC`
- Flyway: next free version (after current head, e.g. post-`V17`)
- Register entity in `persistence.xml`

---

## 6. Web / UI

| Surface | Behaviour |
|---------|-----------|
| Product detail | “Add to wishlist” / “Remove” toggle for logged-in users; login prompt for guests |
| `wishlist.xhtml` | List items with link to product-detail; remove action |
| Optional | Heart icon on catalog cards (same use cases) |

- Authenticated only for mutations
- Design-system tokens only

---

## 7. Security

- Add/remove/list: authenticated customer (session / `SecurityContext`)
- Ownership always enforced server-side (userId from security context, never from the form alone)

---

## 8. Testing

- Domain unit tests (uniqueness rules if modeled there)
- Application tests with mocked ports
- Adapter ITs (Testcontainers)
- `WishlistHexagonalArchitectureTest` (mirror reviews/catalog)

---

## 9. Definition of Done

- [ ] MVP flows work on Open Liberty smoke
- [ ] Unique constraint + domain guards
- [ ] ArchUnit + tests green
- [ ] No new dependencies
```
