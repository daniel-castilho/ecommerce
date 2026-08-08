# Product Catalog Module — Technical Specification (As-Built)

**Status:** Living document reflecting the implemented module (epic S1–S9 delivered 2026-07-31; inventory reservation and later polish included).
**Companion docs:** `product-catalog-backlog.md` · `product-catalog-implementation-sequence.md`

The original long-form pre-implementation specification is preserved in git history of this file.

---

## 1. Purpose & Architectural Role

`product-catalog` owns products, categories, images, stock primitives, and inventory **reservation** for checkout.

- Public storefront: searchable catalog + product detail
- Admin: create / edit / publish / archive / reactivate, image upload
- Integrates with **order-checkout** via ports (stock decrement + reservation)
- Integrates with **product-reviews** only as a product lookup target (ports)

---

## 2. Package Layout (actual)

Single Maven module **`product-catalog`**, package root `com.loja.productcatalog`:

```
domain/
  model/          → Product, ProductImage, Category, Sku, Slug, ProductStatus, …
  port/in/        → Create/Update/Publish/Archive/Search/Upload/… use cases
  port/out/       → ProductRepositoryPort, CategoryRepositoryPort,
                    ProductImageStoragePort, InventoryReservationPort
  exception/
application/
  service/        → ProductApplicationService, reservation expiry service, …
  dto/            → commands, ProductSearchCriteria, PageResult, …
adapter/
  in/web/         → ManageProductBean, ProductCatalogBean, detail beans
  out/persistence/→ JPA entities, mappers, CategoryTreeCache
  out/storage/    → ProductImageStorageS3Adapter (+ CDI config producer)
```

Pages live under `web/src/main/webapp/product-catalog/`.

**Hard rules:** domain free of frameworks; depend only on other modules’ ports; ArchUnit `ProductHexagonalArchitectureTest`.

---

## 3. Domain Model (as implemented)

### Product

- Identity: **String UUID**
- Value objects: `Sku`, `Slug`; price via **`com.loja.shared.domain.Money`**
- Status machine: DRAFT → ACTIVE → ARCHIVED (reactivation **ARCHIVED → ACTIVE** allowed)
- Images: primary flag invariant; alt-text required for publish
- Stock: `decrementStock` / reservation helpers
- Optimistic locking: **version** round-tripped domain ↔ JPA

### Category

- Tree (parent/children); active flag for public tree
- Cached via `@ApplicationScoped` `CategoryTreeCache` with invalidation on mutation

### Inventory reservation

- Separate aggregate/port: reserve / confirm / release
- TTL + lazy expiry on next reserve; **plus** scheduled global `expireExpired()` every 60s
- Atomic stock updates to prevent oversell

---

## 4. Application Layer

| Concern        | Implementation                                                            |
| -------------- | ------------------------------------------------------------------------- |
| CRUD + publish | `ProductApplicationService` implementing the inbound use-case ports       |
| Search         | Criteria-based `search()` (ACTIVE by default on public path)              |
| Images         | Upload validation (size/type/count); S3 key + public URL via storage port |
| Description    | OWASP Java HTML sanitizer (application layer)                             |
| Categories     | Existence checks on publish; admin multi-select                           |

DTOs live in `application/dto` only (never nested in ports).

---

## 5. Persistence & Storage

- Tables: `tb_product`, images, categories, inventory reservation (Flyway; early catalog extension e.g. `V7`)
- Criteria API search + pagination; ARCHIVED excluded unless explicitly filtered
- S3-compatible storage: LocalStack in dev (`localstack/localstack:3.8.1`), real S3 via env props
- Config: env / system properties (`s3.endpoint-override`, bucket, keys, …) — no MicroProfile Config requirement
- `forcePathStyle(true)` required for LocalStack hostname setups

---

## 6. Web / UI

| Page                   | Role                                                                            |
| ---------------------- | ------------------------------------------------------------------------------- |
| `catalog.xhtml`        | Public search / filter / pagination; product cards                              |
| `product-detail.xhtml` | ACTIVE by slug; gallery, price, stock, buy link; reviews section composed later |
| `manageProduct.xhtml`  | Admin create/edit/publish/archive; image management                             |

- Admin: `@RolesAllowed("ADMIN")` + `web.xml`
- Manage bean is **`@ViewScoped`** (multi-step image workflow)
- Design-system tokens only

---

## 7. What Differs From the Original Spec

| Original aspiration                                               | As built                                         |
| ----------------------------------------------------------------- | ------------------------------------------------ | ---- |
| Split `catalog-core` / `catalog-adapters` / `catalog-web` modules | Single `product-catalog` module + pages in `web` |
| Long id types                                                     | String UUID                                      |
| Ports under `application/port`                                    | Ports under `domain/port/in                      | out` |
| Inventory reservation “out of scope”                              | Implemented and used by checkout                 |
| HTML sanitizer “open”                                             | OWASP sanitizer adopted                          |
| Full-text engine (Elasticsearch)                                  | DB Criteria search; FTS optional later           |

---

## 8. Explicit Debt / Optional Next Steps

- PostgreSQL Full-Text Search / ranking if catalog size requires it
- Subcategory rollup in search filters
- Bulk product import
- Product variants (size/color) — schema should not block, not implemented

---

## 9. Testing

- Domain: pure unit tests, no mocks
- Application: mocked ports
- Adapters: Testcontainers Postgres + LocalStack ITs
- ArchUnit: 8 rules green

```bash
mvn -pl product-catalog test -Dtest='*Test' -DfailIfNoTests=false
mvn -pl product-catalog test -Dtest='*IT'
```

---

## 10. Definition of Done (module)

- [x] Searchable public catalog + product detail
- [x] Admin CRUD + images + publish/archive
- [x] Category tree + cache
- [x] Stock decrement + inventory reservation + expiry
- [x] Hexagonal boundaries enforced
- [ ] Optional advanced search ranking

---

_This document describes the module as implemented. For the original pre-build design (split modules, full code sketches, open questions), see the git history of this file._

```

```
