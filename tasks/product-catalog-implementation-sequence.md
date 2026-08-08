# Product Catalog — Implementation Sequence (As-Built)

**Companion docs:** `product-catalog-module-spec.md` · `product-catalog-backlog.md`

This document records the **actual delivery order** of the product-catalog work.
The original step-by-step pre-build plan is preserved in git history of this file.

> **Epic S1–S9 delivered 2026-07-31.**
> Later work (inventory reservation, scheduled expiry, archive/reactivate, product-detail polish) is listed as follow-on phases.

---

## Guiding principles used

- Single Maven module: **`product-catalog`** under `com.loja.productcatalog`
- Hexagonal layout: `domain` → `application` → `adapter`
- LocalStack **3.8.1** for S3 (not `latest` / 4.x — token required)
- DTOs in `application/dto`; ports are pure interfaces
- `@Version` always round-tripped in mappers (lesson #1)
- Manual Flyway registration pattern for early migrations (e.g. `V7`)

---

## Actual delivery sequence

### Phase 0 — Environment

- LocalStack service already in `docker/docker-compose.yaml`
- `scripts/bootstrap-localstack.sh` — creates `product-images` bucket + public-read policy (idempotent)

---

### Phase 1 — Core epic (Steps 1–9)

**Completed: 2026-07-31**

| Step | What landed                                                                                                                          |
| ---- | ------------------------------------------------------------------------------------------------------------------------------------ |
| 1    | Domain: `Product`, `Sku`, `Slug`, `ProductImage`, status machine, pure `ProductTest`                                                 |
| 2    | Ports in/out + command DTOs; `Category` domain type for port compilation                                                             |
| 3    | JPA entity/mapper/adapter, `search()`, `decrementStock`, Flyway catalog extension, ITs (incl. concurrency)                           |
| 4    | Category tree adapter + `@ApplicationScoped` cache with invalidation                                                                 |
| 5    | `ProductImageStorageS3Adapter` + CDI config; LocalStack ITs; `forcePathStyle(true)`                                                  |
| 6    | `ProductApplicationService` (create/update/publish/archive/search/upload + image meta/reorder); OWASP HTML sanitizer for description |
| 7    | Admin UI `manageProduct.xhtml` + `ManageProductBean` (`@ViewScoped`, `@RolesAllowed("ADMIN")`); manual QA on Liberty + LocalStack    |
| 8    | Public `catalog.xhtml` — search/filter/pagination, ACTIVE only                                                                       |
| 9    | `ProductHexagonalArchitectureTest` (8/8 ArchUnit rules)                                                                              |

**Documented deviations from the original plan (kept intentionally):**

- Admin bean is `@ViewScoped` (multi-step image management), not `@RequestScoped`
- Create-then-edit for images (DRAFT first, then upload on edit) — later also optional image on create
- No legacy “cutover” from an old entity package — adapter layout was already correct
- Category filter dropdown injects `CategoryRepositoryPort` (no separate listing use case)

---

### Phase 2 — Storefront & ops follow-ons

**Early August 2026**

| Item                           | Notes                                                           |
| ------------------------------ | --------------------------------------------------------------- |
| Product-detail page            | By slug; ACTIVE only; gallery, price, stock, buy link           |
| Primary image on catalog cards | Public S3 URL                                                   |
| Design-system rollout          | Shared template + tokens on catalog/manage pages                |
| Inventory reservation          | Port + table + TTL; wired into order-checkout                   |
| Scheduled reservation expiry   | Daemon every 60s (`expireExpired`)                              |
| Archive / reactivate           | Admin path; `ARCHIVED → ACTIVE` legal; transition tests updated |
| Real Jakarta Security          | Admin pages behind container RBAC (user-account + `web.xml`)    |

Reviews on product-detail are owned by **`product-reviews`** (v0.10.0), not this module’s domain.

---

## Recommended order for any _new_ work

1. Change domain invariants only in `domain/model` + update pure unit tests in the same change
2. Expose new behaviour via ports/use cases; keep adapters thin
3. Register new JPA entities in `web` `persistence.xml`
4. Prefer filtered unit tests; use `*IT` + Testcontainers for persistence/S3
5. Browser-smoke admin + public catalog after UI changes
6. New AWS/S3 or search libraries → **ask human** before adding dependencies

---

## Useful commands

```bash
# Fast unit + ArchUnit (no containers)
mvn -pl product-catalog test -Dtest='ProductTest,SkuTest,SlugTest,ProductJpaMapperTest,ProductApplicationServiceTest,CategoryTreeCacheTest,ProductHexagonalArchitectureTest' -DfailIfNoTests=false

# Full module tests (includes Testcontainers)
mvn -pl product-catalog test

# LocalStack
docker compose -f docker/docker-compose.yaml up -d localstack
./scripts/bootstrap-localstack.sh

# WAR + run
mvn clean package -pl web -am
./scripts/run-liberty.sh
```

Smoke path (core):

1. Admin: create product → upload image → publish → ACTIVE
2. Public catalog: search/paginate → open detail by slug
3. Checkout path: stock reservation / decrement still works

---

## Definition of Done (sequence)

- [x] Domain + persistence + search + categories + images
- [x] Admin CRUD + public catalog + ArchUnit
- [x] Stock decrement + inventory reservation + expiry job
- [x] Product-detail + design-system alignment
- [ ] Optional: Postgres FTS / advanced ranking when catalog scale requires it

---

_This is the as-built execution record. For the original detailed step checklists and code sketches, see the git history of this file._

```

```
