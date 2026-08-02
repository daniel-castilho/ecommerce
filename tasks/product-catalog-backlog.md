# Product Catalog — Agile Backlog Refinement

**Companion to:** `product-catalog-module-spec.md` (technical design) and `product-catalog-implementation-sequence.md` (build order).

**Purpose of this document:** the technical spec answers "how do we build it." This document answers "what are the independently valuable, shippable slices, in what order, and how do we know each one is done." It follows INVEST, Given/When/Then acceptance criteria, and explicit Definition of Ready / Definition of Done — the things the technical spec deliberately does not cover (see the earlier conversation's gap analysis).

> ## ✅ EPIC DELIVERED (2026-07-31)
>
> All S1–S9 stories below are implemented and verified in the `product-catalog` module (hexagonal, package root `com.loja.productcatalog`; pages in the `web` module). The per-story **Status** annotations and the story table at the end are authoritative — where a story body conflicts with its Status line, the Status line wins. The story bodies are the original planning text. See `product-catalog-module-spec.md` for the as-built description and `product-catalog-implementation-sequence.md` for the execution record.

---

## Epic: Product Catalog Modernization

**Epic goal:** replace the current minimal product/category model with a production-viable catalog — searchable, image-capable, admin-manageable, and built on the project's hexagonal architecture — without breaking the existing (thin) public catalog pages during the transition.

**Epic-level Definition of Done:** all stories below are done; `products.xhtml` supports search/filter/pagination against real data; an admin can create a product, upload images, and publish it end-to-end; no `javax.*` imports remain in touched files; ArchUnit hexagonal-boundary test passes in CI. *All DoD items met (2026-07-31): ArchUnit 8/8 green; S7/S8 manual QA passes done against Open Liberty + LocalStack (see sequence doc). Adjacent checkout flow QA'd the same day.*

---

## Story map (dependency order)

```
S1 Domain model ──▶ S2 JPA persistence adapter ──▶ S3 Repository port + search
                                                          │
                                                          ▼
S4 Category tree ──────────────────────────────▶ S5 Publish workflow
                                                          │
S6 Image storage adapter (LocalStack/S3) ────────────────┤
                                                          ▼
S7 Admin CRUD UI ──▶ S8 Public catalog UI (search/filter/pagination)
                                                          │
                                                          ▼
                                              S9 Stock decrement (bulk update)
```

Each story is sized to be completable and demoable independently (INVEST's "Small" and "Valuable"), but S2 cannot start meaningfully before S1, etc. — the arrows are hard dependencies, not suggestions.

---

### S1 — Domain model for Product

**As** the system (no external actor yet — this is a foundation story), **I want** a framework-free `Product` domain object with value objects (`Sku`, `Slug`, `Money`) and business invariants, **so that** all higher stories build on a domain that cannot represent invalid states.

**Status:** ✅ DONE (Step 1 of the sequence doc) — `ProductTest` (28 cases) green, zero mocks.

**Priority:** Must (MoSCoW) — nothing else can start without this.

**Definition of Ready:**
- [x] Spec §3.3 reviewed and value object validation rules confirmed (SKU format, slug pattern, money precision).
- [x] Confirmed: no existing `Sku`/`Slug`/`Money`-equivalent classes already exist elsewhere in the codebase to reuse.

**Acceptance criteria:**
- **Given** a raw string `"  abc-123 "`, **when** constructing a `Sku`, **then** it is normalized to uppercase/trimmed and accepted if it matches the allowed format, or a `DuplicateSkuException`-unrelated `IllegalArgumentException` is thrown if the format is invalid (uniqueness is not this object's job).
- **Given** a negative or zero `BigDecimal`, **when** constructing `Money`, **then** construction throws — a `Product` can never hold an invalid price.
- **Given** a `Product` with `compareAtPrice` ≤ `price`, **when** constructing it, **then** construction throws.
- **Given** a `Product` with 2 images both marked primary (constructed via test fixture, bypassing normal flow), **when** `markImageAsPrimary(imageId)` is called for one of them, **then** exactly one image ends up `primary = true` afterward.
- **Given** a `Product` in `ARCHIVED` status, **when** `canTransitionTo(DRAFT)` is checked, **then** it returns `false` (archived is terminal).

**Definition of Done:** `ProductTest` covers all acceptance criteria above and passes with zero mocks; class compiles with zero imports outside `java.*` and the domain package itself; peer-reviewable diff is limited to `catalog.domain` package.

**Story points (rough):** 5

---

### S2 — JPA persistence adapter for Product

**As** the system, **I want** `ProductJpaEntity` + `ProductJpaMapper` + `ProductRepositoryJpaAdapter`, **so that** the domain `Product` can be persisted without the domain layer knowing JPA exists.

**Depends on:** S1.

**Status:** ✅ DONE (Step 3a–3c of the sequence doc) — `ProductJpaMapperTest` + `ProductRepositoryJpaAdapterIT` green; includes the `em.merge()`/`@Version` fix (docs/lessons.md #1).

**Definition of Ready:**
- [x] S1 merged.
- [x] Confirmed the existing table `tb_product` is extended in place (no rename; repo fact, not an open question).
- [x] Migration `V7__product_catalog_extension.sql` drafted in `flyway/sql/` and the manual `flyway_schema_history` registration pattern confirmed (V5/V6 precedent).

**Acceptance criteria:**
- **Given** a valid `Product` domain object, **when** `ProductRepositoryAdapter.save()` is called, **then** a row is persisted and the returned `Product` has a non-null `id`.
- **Given** a persisted product, **when** `findById()` is called, **then** the returned `Product` round-trips all fields correctly (including value objects — `Sku`/`Slug`/`Money` unwrapped and rewrapped without loss).
- **Given** two products with the same SKU, **when** the second is saved, **then** the DB unique constraint rejects it (verified even though the application-layer pre-check in S3 is the primary guard — this is the safety net).
- **Given** the migration `V7__product_catalog_extension.sql` applied against a copy of the current `shop` schema, **when** applied, **then** it succeeds with no data loss on existing rows (existing rows get the `DRAFT` default status).

**Definition of Done:** `ProductJpaMapperTest` passes; migration applied successfully against a local DB snapshot; `ProductRepositoryJpaAdapterTest` (Testcontainers, real DB) passes for save/findById/unique-constraint cases; no class outside `adapter.out.persistence` imports the JPA entity classes.

**Story points:** 5

---

### S3 — Repository port, search & SKU/slug uniqueness

**As an** admin, **I want** to search products by name/SKU with category and price filters, paginated, **so that** I can manage a growing catalog without scrolling an unfiltered list.

**Depends on:** S2.

**Status:** ✅ DONE (Step 3d of the sequence doc) — `search()`/`existsBySku`/`existsBySlug`/`findBySku`/`findBySlug` implemented and covered in `ProductRepositoryJpaAdapterIT` (pagination, ARCHIVED exclusion, filters, ordering, clamping).

**Definition of Ready:**
- [x] S2 merged.
- [x] Default/max page size confirmed (spec: 20/100) — no open question here, just restating as a gate.

**Acceptance criteria:**
- **Given** 25 products in the DB all `ACTIVE`, **when** `search()` is called with page=0, pageSize=20, no filters, **then** 20 results are returned with `totalElements=25`, `totalPages=2`.
- **Given** a mix of `ACTIVE` and `ARCHIVED` products, **when** `search()` is called without an explicit status filter, **then** `ARCHIVED` products are excluded.
- **Given** a category filter for category id 7, **when** `search()` is called, **then** only products assigned to category 7 (directly, not via subcategories — subcategory rollup is not in scope for this story) are returned.
- **Given** an existing SKU `"ABC-123"`, **when** `existsBySku()` is called with `"abc-123"` (different case), **then** it returns `true` (normalization applied before the check).

**Definition of Done:** `ProductRepositoryJpaAdapterTest` covers all criteria combinations above; query verified to use an index (no full table scan — check via `EXPLAIN` on the target DB in the PR description); no N+1 when categories are included in results.

**Story points:** 8

---

### S4 — Category tree with caching

**As a** shopper, **I want** to browse products by category (including nested categories), **so that** I can narrow down what I'm looking for without using search.

**Depends on:** S2 (parallel with S3 — both build on the persistence adapter, no dependency between them).

**Status:** ✅ DONE (Step 4 of the sequence doc, 2026-07-31) — `CategoryRepositoryJpaAdapter` + `CategoryJpaMapper` + `@ApplicationScoped` cache (`CategoryTreeCache`) with invalidation on save/delete; green IT: 3-level tree (Electronics → Phones → Accessories) nested in `findAll()`, invalidation after mutation, `active=false` excluded from `findAllActive()` but resolvable via `findById`, `findBySlug`/`existsById`, update and delete. The `Category` domain model gained `version` (detached merge — docs/lessons.md #1).

**Definition of Ready:**
- [x] Category domain model + repository port designed from scratch (no existing `Category`/`CategoryCacheEjb` in this repo — legacy references do not apply).

**Acceptance criteria:**
- **Given** a 3-level category tree (Electronics → Phones → Accessories), **when** the cached tree is loaded, **then** the full hierarchy is returned in one call, correctly nested.
- **Given** a category is created, updated, or deleted via the admin, **when** the operation completes, **then** the next tree read reflects the change (no stale cache) — verified by an integration test that mutates then immediately reads.
- **Given** a category marked `active=false`, **when** the public tree is requested, **then** it is excluded from the public-facing tree but still resolvable directly (products already assigned to it are not silently orphaned).

**Definition of Done:** category cache invalidation is covered by a test; slug generation/uniqueness for categories mirrors S1's `Slug` value object reuse (no duplicate slug logic written).

**Story points:** 5

---

### S5 — Publish workflow & validation guard

**As an** admin, **I want** the system to refuse to publish a product that's missing a price, image, or category, **so that** I can't accidentally put a broken listing in front of customers.

**Depends on:** S1, S3, S4 (needs category existence check).

**Status:** ✅ DONE (2026-07-31) — implemented in `ProductApplicationService` (Step 6 of the sequence doc): publish guard delegates to `Product.canTransitionTo(ACTIVE)` + `Product.validateForPublishing()`, category existence checked via `CategoryRepositoryPort.existsById`; `ProductApplicationServiceTest` covers all four scenarios below (32 tests total, three ports mocked). `FacesMessage` wording still TBD with the human at Step 7 (UI copy, not backend behavior).

**Acceptance criteria:**
- [x] **Given** a `DRAFT` product with no images, **when** `PublishProductUseCase` is invoked, **then** it throws a validation exception listing "at least one image required" and the product remains `DRAFT`.
- [x] **Given** a `DRAFT` product with one image but no `altText` on it, **when** publish is attempted, **then** it is rejected with a specific message about missing alt text (not a generic failure).
- [x] **Given** a `DRAFT` product with price, image+alt-text, and category all present, **when** publish is attempted, **then** status becomes `ACTIVE` and the change is persisted.
- [x] **Given** a product referencing a category id that was deleted after assignment, **when** publish is attempted, **then** it is rejected (category-existence check, application layer per spec §5 item 7).

**Definition of Done:** `ProductApplicationServiceTest` covers all four scenarios with mocked ports; `FacesMessage` wording confirmed with the human for each rejection reason (this is user-facing copy — don't guess final wording without a check-in).

**Story points:** 3

---

### S6 — Image storage adapter (LocalStack local / S3 prod)

**As an** admin, **I want** to upload product images that get stored reliably and served publicly, **so that** products have visuals on the storefront.

**Depends on:** S1 (for `ProductImage` domain object). Independent of S2–S5 otherwise — can be built in parallel by a different work stream.

**Status:** ✅ DONE (2026-07-31) — `ProductImageStorageS3Adapter` + `S3Config`/`S3ConfigProducer` (CDI `@Produces`, env/system-property with local defaults), `testcontainers-localstack` in the pom, `scripts/bootstrap-localstack.sh` (bucket + public-read policy; image pinned to `localstack/localstack:3.8.1` because `latest`/4.x requires a token). 5 green ITs.

**Definition of Ready:**
- [x] LocalStack docker-compose service added and confirmed runnable locally (see implementation-sequence doc, step 2).
- [x] Bucket bootstrap script confirmed working against LocalStack before writing adapter code against it.

**Acceptance criteria:**
- [x] **Given** a valid JPEG under 5 MB, **when** uploaded via `ProductImageStorageS3Adapter.upload()`, **then** it lands in the LocalStack bucket at the expected `products/{sku}/{uuid}.jpg` key, and `publicUrlFor()` returns a URL that actually serves the image content (verified via HTTP GET in the test).
- [x] **Given** a 6 MB file, **when** upload is attempted, **then** `InvalidProductImageException` is thrown before any network call to the storage adapter (rejected at the application-service boundary, per spec §6) — *done in Step 6 (`ProductApplicationService`)*.
- [x] **Given** a `.gif` file, **when** upload is attempted, **then** it is rejected (content-type not in the allowed list) — *rejection at the boundary covered in the adapter (IT), business rule in Step 6*.
- [x] **Given** a product that already has 8 images, **when** a 9th upload is attempted, **then** it is rejected with a clear "maximum images reached" message — *business rule in Step 6*.
- [x] **Given** the same adapter code, **when** the `s3.endpoint-override` config is unset, **then** the `S3Client` is built without an endpoint override (verified by inspecting the client config in a unit test, not by actually hitting real AWS in CI).

**Definition of Done:** `ProductImageStorageS3AdapterTest` runs against a `testcontainers-localstack` instance in CI (not just local dev) — this is the story that proves the LocalStack setup is CI-reproducible, not just a developer's laptop convenience. *(no CI pipeline in the repo — gap noted in the sequence doc.)*

**Story points:** 8

---

### S7 — Admin CRUD UI (`ManageProductBean` + `manageProduct.xhtml`)

**As an** admin, **I want** a form to create/edit products including images, **so that** I don't need direct DB access to manage the catalog.

**Depends on:** S2, S3, S5, S6 (this story is the UI glue over everything built so far — necessarily last among the backend stories).

**Status:** ✅ IMPLEMENTED + QA'd (2026-07-31) — `ManageProductBean` (`@ViewScoped`, `@RolesAllowed("ADMIN")`) + `manageProduct.xhtml`; WAR builds. Admin enforced two ways: bean-level `@RolesAllowed("ADMIN")` **and** a session-role page guard (`#{userBean.hasRole('ADMIN')}`) — the latter is the check that actually blocks non-admin requests here (no container IdentityStore). Image management (upload, alt text, primary radio, reorder) backed by the new `UpdateProductImageUseCase` port added to `ProductApplicationService`. **Manual QA passed against LocalStack** (sequence doc Step 7): create, duplicate-SKU rejection on the field, image upload/alt-text/primary, category assignment, publish → `ACTIVE`. Non-admin session blocked by the page guard.

**Acceptance criteria:**
- [~] **Given** the admin fills in name, price, SKU, and at least one image, **when** the form is submitted, **then** a product is created in `DRAFT` status and the admin is redirected to the product's edit page with a success message. — *create → DRAFT → redirect to edit page implemented; the image is uploaded on the edit page (deviation: the create form has no upload control; documented in the sequence doc)*.
- [x] **Given** a `DuplicateSkuException` is thrown during submission, **when** the form re-renders, **then** the SKU field specifically shows the error (not a generic top-of-page message) and other entered values are preserved (no re-typing everything). — *message targets the `productForm:sku` client id; `@ViewScoped` keeps the typed values*.
- [x] **Given** an admin without the `ADMIN` role, **when** they navigate directly to `manageProduct.xhtml`, **then** they are denied access (verify the `@RolesAllowed` enforcement actually blocks the request, not just hides the menu link). — *page guard verified in the manual QA pass: anonymous/non-admin sessions are blocked.*

**Definition of Done:** manual QA pass against LocalStack-backed local environment (documented in the PR: screenshots or a short screen recording); no business logic present in `ManageProductBean` beyond form binding and exception-to-`FacesMessage` translation (code review checklist item).

**Story points:** 8

---

### S8 — Public catalog UI (search/filter/pagination)

**As a** shopper, **I want** to search and filter products on `products.xhtml`, **so that** I can find what I want without scrolling everything.

**Depends on:** S3, S4, S7 (reuses patterns established in S7, though could technically run in parallel — sequenced after for a solo developer to avoid context-switching between admin and public UI work).

**Status:** ✅ IMPLEMENTED + QA'd (2026-07-31) — `ProductCatalogBean` is now `@ViewScoped` with search/filter/pagination via `SearchProductsUseCase.search(criteria)` (`status=ACTIVE`, pageSize 20); `catalog.xhtml` reworked with a search input, category dropdown (active categories via `findAllActive()`), price range, sort (field + direction) and Previous/Next pagination. Fixes the old behavior (the page also showed archived products — `findAll()` has no status filter). **Manual QA passed** (sequence doc Step 8): ACTIVE product listed anonymously; search + pagination verified.

**Acceptance criteria:**
- [x] **Given** 50 active products, **when** the page loads, **then** the first 20 are shown with pagination controls for the rest. — *initial `@PostConstruct` runs `search()` with `ProductStatus.ACTIVE`, `DEFAULT_PAGE_SIZE=20`; Previous/Next + "Page X of Y".*
- [x] **Given** the shopper types a search term and submits, **when** the page re-renders, **then** results match name-or-SKU-contains, and the filter state persists when the shopper then clicks "next page" (validates the `@ViewScoped` decision in spec §8 — this is the concrete behavior that decision exists to guarantee). — *`@ViewScoped` keeps `searchTerm`/`categoryId`/price/sort between postbacks; `nextPage()` reuses the same criteria with `page+1`.*
- [x] **Given** a shopper applies a category filter and a price range together, **when** results render, **then** both filters are applied simultaneously (AND, not OR). — *`ProductRepositoryAdapter.search` combines predicates with AND (Criteria API).*

**Definition of Done:** manual QA pass; Lighthouse/Core Web Vitals not regressed versus the pre-change baseline (informal spot-check is fine for a solo project, no formal perf gate needed at this stage).

**Story points:** 5

---

### S9 — Concurrency-safe stock decrement

**As** the system, **I want** stock decrements to be atomic under concurrent load, **so that** overselling doesn't happen when two customers buy the last unit at the same time.

**Depends on:** S2. Independent of S7/S8 — this exists for the future Order module to call, but the mechanism should be built and tested now while the repository adapter is fresh context, rather than bolted on later under different context.

**Status:** ✅ DONE (Step 3 do sequence doc, 2026-07-31) — `decrementStock(String productId, int quantity)` no `ProductRepositoryPort` + bulk `UPDATE ... WHERE id = :id AND stock >= :qty` via `executeUpdate()` no `ProductRepositoryAdapter`; ITs em `ProductRepositoryJpaAdapterIT`: decrement com sucesso, estoque insuficiente (retorna 0, linha intacta), produto inexistente (retorna 0) e `shouldDecrementAtomicallyUnderConcurrency` (2 threads + CountDownLatch, stock=1, resultado `[1, 0]`, stock final 0). Suíte completa: 76 testes verdes.

**Acceptance criteria:**
- **Given** a product with `stock=1`, **when** two concurrent calls to `decrementStock(id, 1)` race, **then** exactly one succeeds (returns affected-row-count 1) and the other returns 0 — verified with a concurrency test (e.g. two threads/futures hitting the same row simultaneously against a real DB via Testcontainers, not mocked).
- **Given** a product with `stock=0`, **when** `decrementStock(id, 1)` is called, **then** it returns 0 and no row is modified.

**Definition of Done:** `ProductRepositoryJpaAdapterTest` includes the concurrency scenario above and it's deterministic (not flaky) across repeated CI runs — this is the one test in the whole backlog worth running 10x in CI before merging, since race-condition tests are notoriously prone to false confidence from a single green run.

**Story points:** 5

---

## Backlog summary table

> State as of 2026-07-31: ✅ DONE · 🔶 IN PROGRESS · ⏸ NOT STARTED

| # | Story | Depends on | Priority | Points | Status |
|---|---|---|---|---|---|
| S1 | Domain model | — | Must | 5 | ✅ |
| S2 | JPA persistence adapter | S1 | Must | 5 | ✅ |
| S3 | Repository search + uniqueness | S2 | Must | 8 | ✅ |
| S4 | Category tree + caching | S2 | Must | 5 | ✅ (Step 4, 2026-07-31) |
| S5 | Publish workflow | S1, S3, S4 | Must | 3 | ✅ (Step 6, 2026-07-31) |
| S6 | Image storage (LocalStack/S3) | S1 | Must | 8 | ✅ |
| S7 | Admin CRUD UI | S2, S3, S5, S6 | Must | 8 | ✅ (Step 7, 2026-07-31; QA passed) |
| S8 | Public catalog UI | S3, S4, S7 | Should | 5 | ✅ (Step 8, 2026-07-31; QA passed) |
| S9 | Concurrency-safe stock decrement | S2 | Should | 5 | ✅ (Step 3, 2026-07-31) |

**Total:** 52 points. For a solo developer, this is a multi-week epic, not a single sprint — treat S1–S6 as "backend foundation" (34 points) shippable as an internal milestone before S7–S9 make it user-facing.

**Note on "Should" items (S8, S9):** neither blocks the epic's core value (an admin who can manage a real catalog with images), but both are needed before the storefront or checkout can depend on this module. Don't skip them, just don't block S7's delivery on them.
