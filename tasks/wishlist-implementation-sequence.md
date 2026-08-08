### 4. `tasks/wishlist-implementation-sequence.md`

```markdown
# Wishlist — Implementation Sequence

**Companions:** `wishlist-module-spec.md` · `wishlist-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not invent scope.

---

## Step 0 — Module skeleton

1. Create Maven module `wishlist` (mirror `product-reviews` POM shape)
2. Add to root `pom.xml` + `web` dependency
3. Package tree `com.loja.wishlist`
4. Confirm next Flyway version

**Done when:** `mvn -pl wishlist test-compile` succeeds.

---

## Step 1 — Domain + exceptions

`WishlistItem`, factory/guards, domain exceptions. Pure unit tests. Zero framework imports.

**Done when:** domain tests green.

---

## Step 2 — Ports + DTOs

Inbound use cases; `WishlistRepositoryPort`; `ProductLookupPort`; DTOs in `application/dto`.

**Done when:** interfaces compile.

---

## Step 3 — Application service

`WishlistApplicationService`: add (lookup ACTIVE → uniqueness → save), remove, list. Unit tests with mocked ports.

**Done when:** service tests green.

---

## Step 4 — Persistence + Flyway

Entity, mapper, adapter, migration, unique `(user_id, product_id)`, ITs.

**Done when:** ITs green; unique constraint enforced.

---

## Step 5 — Product lookup adapter

Thin adapter over `product-catalog` ports only.

**Done when:** compiles; covered by service IT or adapter test.

---

## Step 6 — Web UI

- `WishlistBean`
- Toggle on `product-detail.xhtml`
- `wishlist.xhtml` list + remove
- Register entity in `persistence.xml`
- Nav link for logged-in users

**Done when:** manual smoke on Open Liberty.

---

## Step 7 — ArchUnit + polish

`WishlistHexagonalArchitectureTest`; empty states; FacesMessages; English only.

**Done when:** `mvn -pl wishlist,web -am test` (relevant tests) green; full package succeeds.

---

## Step 8 — Release prep

Update README current state; optional `docs/releases/v0.X.0.md` when DoD met; tag only if human requests.

---

## Smoke path

1. Login as customer
2. Product detail → Add to wishlist
3. Open wishlist page → item visible with link to detail
4. Remove → list empty / item gone
5. Add again → no duplicate row (or clear message)

---

_Pre-implementation sequence. After delivery, replace with an as-built status note and keep this plan in git history if desired._
```
