# Product Catalog — Implementation Sequencing Appendix

**Companion to:** `product-catalog-module-spec.md` (what to build) and `product-catalog-backlog.md` (why, sliced into stories). This document is the **execution order** — read it before writing any code. It exists so the implementing agent never has to stop and ask "what do I do first" or produce a half-migrated, non-compiling intermediate state.

> ## ⚠️ WRITTEN FOR THE LEGACY `java-ee-online-shop` REPO — ADAPT BEFORE EXECUTING (July 30, 2026)
>
> This sequence names modules/classes that do not exist in this repo (`catalog-core`, `catalog-adapters`, `catalog-web`, `ProductDao`, `ProductEjb`, `CategoryCacheEjb`, GlassFish/Liberty commands). The real repo is the `ecommerce` monolith; see the banner in `product-catalog-module-spec.md` for the authoritative mapping. Apply the following substitutions throughout:
>
> - `catalog-core` → `product-catalog` module (package root `com.loja.productcatalog`); domain in `domain/model`, ports in `domain/port/in` + `domain/port/out`, DTOs in `application/dto`, services in `application/service`.
> - `catalog-adapters` → same `product-catalog` module: `adapter/out/persistence` (JPA) and `adapter/out/storage` (S3, new). Verify commands: `mvn -pl product-catalog test` / `mvn test -pl product-catalog -Dtest=...`.
> - `catalog-web` → `web` module for pages (`web/.../webapp/product-catalog/`); JSF beans stay in `product-catalog/.../adapter/in/web` (pattern: `AdminUsersBean`).
> - **Step 3e cutover does not apply:** `ProductJpaEntity` already lives in `adapter/out/persistence` (table `tb_product`) and there is no `ProductDao`/`ProductEntity` to delete or migrate. The work is *extending* the existing entity/mapper and adding the Criteria `search()`/`decrementStock()` — `ProductJpaMapper` does not exist yet and must be created.
> - **Step 4 is from scratch:** no `Category` or `CategoryCacheEjb` exists anywhere — build the category tree/caching fresh (keep it simple; no EJB).
> - **Step 6 security:** the repo has no `jakarta.security.enterprise` IdentityStore; the established RBAC precedent is session-based (`@CurrentUser`/`UserBean`) with `@RolesAllowed("ADMIN")` on the JSF bean (`AdminUsersBean`). Enforce admin on the `ManageProductBean` and/or a session-role check, matching that pattern.
> - **Step 9 template:** copy the ArchUnit approach from `user-account/.../UserHexagonalArchitectureTest` (only `domain` framework-free; ports in `domain/port`; adapters isolated).
> - Migrations: `V7__product_catalog_extension.sql` in `flyway/sql/`, registered manually in `flyway_schema_history` (no runner; see V5/V6).
> - `docker-compose.yaml` **already contains** a `localstack` (s3) service — Step 0 only needs the bootstrap script + bucket policy, not a new compose block.

**Rule for the implementing agent:** work through the steps in order. Do not start step N+1 until step N's "Done when" checklist is fully satisfied. If a step's prerequisites (previous steps) aren't met, stop and report rather than improvising an out-of-order approach.

---

## Progress status (updated 2026-07-31)

> How to resume: start with the checklist at the end of this file. Step status:

- **Step 1 — DONE.** Domain model + `ProductTest` green (28 tests).
- **Step 2 — DONE** (completed on 2026-07-31). Ports created in `domain/port/in` and
  `domain/port/out`: `CategoryRepositoryPort`, `ProductImageStoragePort`,
  `CreateProductUseCase`, `UpdateProductUseCase`, `PublishProductUseCase`,
  `ArchiveProductUseCase`, `UploadProductImageUseCase`. Input DTOs in
  `application/dto`: `CreateProductCommand`, `UpdateProductCommand`,
  `UploadProductImageCommand`. **Pull-forward:** `domain/model/Category.java` (no JPA)
  was created here because `CategoryRepositoryPort` needs the type to compile — Step 4
  completes the adapter/cache/tests, it does **not** recreate the model.
- **Step 3 — DONE.** Entities/mapper/adapter/search/`decrementStock` (spec §4, atomic
  bulk UPDATE) + migration `V7` + green IT (23 cases, including round-trip, S9
  concurrency and the IT harness fix — see `docs/lessons.md` #1 and #3). `order-checkout`
  (`CheckoutService`) already uses `decrementStock` and throws `InsufficientStockException` when
  the return is 0.
- **Step 4 — DONE.** `CategoryRepositoryJpaAdapter` + `CategoryJpaMapper` + cache
  `@ApplicationScoped` (`CategoryTreeCache`) with invalidation on save/delete; green IT
  (3-level nested tree, invalidation after mutation, `active=false` out of the public
  tree but resolvable directly, slug/exists, update and delete). The domain `Category`
  gained `version` (detached merge — see `docs/lessons.md` #1, rule 5).
- **Step 0 — DONE.** `scripts/bootstrap-localstack.sh` created (executable, idempotent):
  creates the `product-images` bucket and applies the public-read policy; compose image
  pinned to `localstack/localstack:3.8.1` (`latest`/4.x requires an authentication token).
- **Step 5 — DONE.** `ProductImageStorageS3Adapter` + `S3Config`/`S3ConfigProducer`
  (CDI `@Produces`, env/system-property with local defaults) + `testcontainers-localstack`
  in the pom; 5 green ITs (upload/key/HTTP public GET, delete, content-type-based
  extensions, `.gif` rejection, `endpointOverride` only when configured).
- **Step 6 — DONE.** `ProductApplicationService` implementing the six use-case interfaces
  (`CreateProductUseCase`, `UpdateProductUseCase`, `PublishProductUseCase`,
  `ArchiveProductUseCase`, `SearchProductsUseCase`, `UploadProductImageUseCase`) with
  `@ApplicationScoped` + `@Transactional`; `SearchProductsService` (redundant split) deleted.
  Spec §5 rules implemented: duplicate-SKU guard, slug generation/derivation + numeric
  collision suffix, publish guard (`canTransitionTo` + `validateForPublishing` +
  category-existence check), soft-delete via `canTransitionTo(ARCHIVED)`, primary-image
  delegation, and HTML whitelist sanitization of `description` via
  `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer` (added to the pom).
  `Product` gained `changeSlug`/`replaceCategories` (and `slug` is no longer `final`).
  `ProductApplicationServiceTest` green: **41 tests** mocking the three ports
  (SKU/slug rules, sanitization, image validation 5 MB / 8 max / content-type whitelist,
  first-image-auto-primary, publish without image/alt-text rejection, image-meta and
  reorder rules).
- **Step 7 — DONE.** `ManageProductBean` (product-catalog `adapter/in/web`) + `web/.../webapp/product-catalog/manageProduct.xhtml`; WAR builds (`web/target/web.war`).
  Bean enforces admin two ways: `@RolesAllowed("ADMIN")` on the class (precedent
  `AdminUsersBean`) **and** a session-role guard on the page (`#{userBean.hasRole('ADMIN')}`
  gates the whole management UI — this is the check that actually works here, since the
  container has no IdentityStore). Product list + create/edit form (DuplicateSku surfaces
  on the SKU field, other values preserved), category `selectManyCheckbox`, image upload
  (`<h:inputFile>`), per-image alt text, primary radio, up/down reorder, and Publish/
  Archive buttons (disabled unless `canTransitionTo`). `ProductApplicationService` gained
  **`UpdateProductImageUseCase`** (new port: `updateImageMeta` for primary + alt text,
  `moveImage` for reorder — needed by the UI; the six original use cases did not expose
  post-upload image management). **Two documented deviations from spec §8:** the bean is
  `@ViewScoped`, not `@RequestScoped` (image management is multi-step within one
  conversation; `@RequestScoped` would lose the edited product id between postbacks —
  matches the `AdminUsersBean` precedent), and the create form has no image upload
  (a DRAFT product is created first, then images are added on the edit page — matches the
  manual-QA flow below). **Manual QA passed (2026-07-31)** against Open Liberty +
  LocalStack: create `SKU-QA-001`, duplicate-SKU rejection on the field, image upload
  (S3 object + `tb_product_image` row + public GET), alt-text/primary, category
  assignment, publish → `ACTIVE`; non-admin page guard blocks anonymous sessions.
  QA fixes: `forcePathStyle(true)` on the S3 client, `@Cacheable(false)` on
  `UserJpaEntity`, `@ViewScoped` state, UUID in `ProductApplicationService.create()`.
- **Step 8 — DONE.** `ProductCatalogBean` is now `@ViewScoped` (implements `Serializable`)
  with search/filter/pagination bound to `ProductSearchCriteria`: name-or-SKU search,
  category dropdown (active categories via `CategoryRepositoryPort.findAllActive()`),
  min/max price range, sort field (NAME/PRICE/CREATED_AT) + direction, page size 20
  (`ProductSearchCriteria.DEFAULT_PAGE_SIZE`). The public page now queries
  `SearchProductsUseCase.search()` with `status=ACTIVE` — **behavior fix**: the old
  `findAll()` returned archived products too (no status filter in the adapter).
  `catalog.xhtml` reworked: filter form + results table + Previous/Next pagination with
  "Page X of Y" and totals. **Documented deviation from spec §8:** the bean injects
  `CategoryRepositoryPort` (via `findAllActive()`) for the filter dropdown — there is no
  category listing use case; this mirrors the `ManageProductBean` precedent. **Manual QA
  passed (2026-07-31):** ACTIVE product listed anonymously with name/SKU/price, search +
  Previous/Next pagination render and work; cards link to checkout (no detail page yet).
- **Step 9 — DONE.** `ProductHexagonalArchitectureTest` (product-catalog `src/test`,
  mirrored from `UserHexagonalArchitectureTest`) with 8 ArchUnit rules: domain free of
  `jakarta.*`/`javax.*`, domain and application isolated from adapters, domain's allowed
  dependencies (domain + `application.dto` + shared-kernel + `java`), application's
  allowed dependencies (application + domain + shared-kernel + `org.owasp` sanitizer +
  `jakarta` CDI/`@Transactional` + `java`), ports are interfaces, `*Adapter` classes
  implement interfaces, and JPA entities referenced only inside
  `adapter.out.persistence`. **8/8 rules green** on the codebase as built through
  Steps 1–8 (no fixes required). `archunit-junit5` 1.3.0 added to the pom. Full unit
  suite: **103 green tests**.

---

## Step 0 — Environment setup (do this first, before touching any Java)

> **Repo note:** `docker/docker-compose.yaml` already defines the `localstack` (s3, port 4566) service alongside `db` (postgres:15). The compose block below is the reference shape; do **not** add a duplicate block.

1. Confirm `docker/docker-compose.yaml` has the `localstack` service running:
   ```yaml
   services:
     localstack:
       image: localstack/localstack:3.8.1
       ports:
         - "4566:4566"
       environment:
         - SERVICES=s3
         - DEFAULT_REGION=us-east-1
       volumes:
         - "./.localstack:/var/lib/localstack"
   ```
2. Start it: `docker compose -f docker/docker-compose.yaml up -d localstack` and confirm `curl http://localhost:4566/_localstack/health` responds with `s3: available`.
3. Bootstrap the bucket (run once per fresh LocalStack volume):
   ```bash
   aws --endpoint-url=http://localhost:4566 s3 mb s3://product-images
   aws --endpoint-url=http://localhost:4566 s3api put-bucket-policy \
     --bucket product-images \
     --policy '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::product-images/*"}]}'
   ```
   Save this as `scripts/bootstrap-localstack.sh` in the repo (executable, `chmod +x`) so it's not a one-off terminal command lost to history — this is exactly the kind of "tribal knowledge" step referenced in the main spec.
4. Confirm the AWS CLI and `aws` credentials are configured for LocalStack use (dummy values are fine): `export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1` — document this in the script header as a prerequisite, don't assume it's already set globally.

**Done when:** `aws --endpoint-url=http://localhost:4566 s3 ls` shows the `product-images` bucket.

---

## Step 1 — Domain model (`product-catalog`, package `com.loja.productcatalog.domain.model`)

Corresponds to backlog story **S1**.

1. Create `ProductStatus.java` (enum, §3.2 of the spec).
2. Create value objects `Sku.java`, `Slug.java`, `Money.java` — **note: `Money` already exists in `com.loja.shared.domain.Money` (shared-kernel); reuse it** — `Sku`/`Slug` are new, each with validating constructors, `equals()`/`hashCode()` based on wrapped value, and a package-private or public accessor for the raw value (needed by the mapper in Step 2). Write these before `Product` itself — `Product`'s constructor depends on them existing and validating correctly.
3. Create `ProductImage.java` (domain object — id, objectKey, altText, position, primary flag; no JPA annotations).
4. Create `Product.java` per spec §3.3, including the four business methods (`validateForPublishing()`, `markImageAsPrimary()`, `removeImage()`, `canTransitionTo()`).
5. Create domain exceptions: `DuplicateSkuException`, `ProductNotFoundException`, `InsufficientStockException`, `InvalidProductImageException` (all extend a common `AppException`/`ValidationException` base per the project's existing exception hierarchy — check `coding-standards.md` §5 before inventing a new hierarchy).
6. Write `ProductTest.java` covering every acceptance criterion in backlog story S1. **Run it and confirm it's green before moving to Step 2.**

**Done when:** `mvn -pl product-catalog test` passes, and `ProductTest` has zero mocks (a mock in this test file is a signal something is in the wrong layer).

---

## Step 2 — Ports (`product-catalog`, package `com.loja.productcatalog.domain.port`)

Interfaces only — no implementations yet, those come in Step 3.

1. `port/out/ProductRepositoryPort.java` — all methods from spec §4, typed in domain objects (`Product`, `Sku`, `Slug`), never JPA types.
2. `port/out/CategoryRepositoryPort.java`.
3. `port/out/ProductImageStoragePort.java` — `upload`/`delete`/`publicUrlFor` per spec §6.2.
4. `port/in/CreateProductUseCase.java`, `UpdateProductUseCase.java`, `PublishProductUseCase.java`, `ArchiveProductUseCase.java`, `SearchProductsUseCase.java`, `UploadProductImageUseCase.java` — one-method-or-few-methods interfaces, each named after the use case, not after a CRUD verb generically.
5. `ProductSearchCriteria.java` and `PageResult.java` (application-layer DTOs, spec §4).

**Done when:** module compiles with these interfaces present and zero implementations — this step deliberately produces uncalled/unimplemented interfaces, which is expected and fine (Java doesn't error on an unimplemented interface).

---

## Step 3 — JPA persistence adapter (`product-catalog`, package `com.loja.productcatalog.adapter.out.persistence`)

Corresponds to backlog story **S2** (steps 3a–3c) and **S3** (step 3d).

**3a. Entities.** Create `AuditableJpaEntity.java` (`@MappedSuperclass`), then **extend the existing** `ProductJpaEntity.java` (table `tb_product`, id String) and create `ProductImageJpaEntity.java` (spec §3.4–3.5), then create `CategoryJpaEntity.java` (spec §7) — **there is no existing Category entity to extend**. **Do not delete the existing `ProductJpaEntity`** — extend it in place.

**3b. Migration.** Write `V7__product_catalog_extension.sql` (spec §11) in `flyway/sql/` and its paired rollback script. Register it manually in `flyway_schema_history` (pattern: V5/V6; no Flyway runner). Apply against the running `shop` DB (docker `shop_db`, postgres:15) and confirm the schema matches what the entities expect — a mismatch here fails silently at runtime with cryptic Hibernate errors otherwise, so verify explicitly with a `DESCRIBE`/`\d` before moving on.

**3c. Mapper and adapter.** Create `ProductJpaMapper.java` (domain ↔ JPA, both directions, including value-object unwrapping — does not exist yet). **Extend** `ProductRepositoryJpaAdapter.java` (currently `ProductRepositoryAdapter`) to implement the new `ProductRepositoryPort` methods — the existing `findByName`/`findAll`/`findById`/`save` carry over; add the Criteria-API `search()`, `existsBySku`/`existsBySlug`, and bulk `decrementStock` (spec §4). Create `CategoryRepositoryJpaAdapter.java` similarly.

**3d. Search.** Add the Criteria-API-based `search()` implementation (spec §4) to the repository adapter.

**3e. Cutover — NOT APPLICABLE in this repo:** there is no old `ProductEntity`/`ProductDao` to delete; the entity already lives in `adapter/out/persistence`. Do not create one. `order-checkout` (`CheckoutService`) must keep compiling against `ProductRepositoryPort` + `Product.reserveStock` throughout.

**Done when:** `mvn -pl product-catalog test` passes, `mvn test` at the repo root compiles (including `order-checkout`), and the concurrency test from backlog story S9 (§ below) passes.

---

## Step 4 — Category tree + caching

Corresponds to backlog story **S4**. **There is no `Category` model and no `CategoryCacheEjb` in this repo — build the category tree from scratch** (domain `Category` + `CategoryJpaEntity` + `CategoryRepositoryPort` from Step 2). Caching: keep it simple — an `@ApplicationScoped` in-memory cache invalidated on mutation (via the application service) is sufficient; do not invent EJB/cache infrastructure.

**Done when:** `CategoryRepositoryJpaAdapterTest` / category tests pass, including the cache-invalidation-after-mutation scenario from backlog S4.

---

## Step 5 — Image storage adapter (`product-catalog`, package `com.loja.productcatalog.adapter.out.storage`)

Corresponds to backlog story **S6**. Can be done in parallel with Steps 3–4 by a separate work stream if applicable, since it only depends on Step 1 (the `ProductImage` domain object) and Step 2 (`ProductImageStoragePort`).

1. Add the AWS SDK v2 S3 dependency (`software.amazon.awssdk:s3`) to `product-catalog/pom.xml` if not already present.
2. Implement `ProductImageStorageS3Adapter.java` per spec §6.2, including the conditional `.endpointOverride(...)` logic (only set when the local endpoint config is present).
3. Add the config properties (`s3.endpoint-override`, `s3.bucket`, `s3.public-base-url`, `s3.region`, `s3.access-key`/`s3.secret-key`) — **resolved: no MicroProfile Config; use a CDI `@Produces` config bean reading env/system-property lookups with local defaults** (the S6 step implements this bean).
4. Add the `testcontainers-localstack` test dependency (consistent with the existing Testcontainers setup from `user-account`) and write `ProductImageStorageS3AdapterTest` against it — covering upload/delete/content-type-rejection/size-rejection/endpoint-override-unset-in-prod-profile.
5. **HTML sanitizer (resolved):** add `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer` (this is the real groupId; `org.owasp:owasp-java-html-sanitizer` does not exist on Central) when implementing the `description` sanitization rule (spec §9) in Step 6.

**Done when:** the test suite from backlog S6 passes in CI (not just locally) — confirm the CI pipeline (if one exists) can run Testcontainers (Docker-in-Docker or equivalent); if no CI pipeline exists yet, note this as a gap to raise with the human rather than silently skipping CI verification.

---

## Step 6 — Application services (`product-catalog`, package `com.loja.productcatalog.application.service`)

Corresponds to backlog story **S5** plus the "glue" parts of S3/S6 not yet wired together.

1. Implement `ProductApplicationService.java`, implementing all six use-case interfaces from Step 2, injecting `ProductRepositoryPort`, `CategoryRepositoryPort`, `ProductImageStoragePort` via `@Inject` (CDI resolves to the adapters built in Steps 3 and 5 — this is the first point in the sequence where domain, ports, and adapters are actually wired together end-to-end).
2. Implement the seven business rules from spec §5 (SKU/slug generation, publish guard delegating to `Product.validateForPublishing()`, primary-image delegation, soft delete, category-existence check).
3. Enforce the `ADMIN` role on mutating operations (spec §10) — **follow the repo precedent**: there is no container IdentityStore, so enforce at the JSF bean with `@RolesAllowed("ADMIN")` and/or a session-role check (`@CurrentUser`/`UserBean.hasRole`), like `AdminUsersBean`; do not rely on container-level security constraints.
4. Write `ProductApplicationServiceTest` covering backlog story S5's acceptance criteria, mocking the three ports.

**Done when:** `ProductApplicationServiceTest` is green and every use-case interface from Step 2 has exactly one calling path exercised in a test.

---

## Step 7 — Admin web adapter (`product-catalog` bean in `adapter/in/web` + page in `web/.../webapp/product-catalog/`)

Corresponds to backlog story **S7**.

1. Create `ManageProductBean.java` in `product-catalog/.../adapter/in/web` — do **not** split it from an existing bean: `ProductCatalogBean` stays public-only, and `ManageProductBean` is new (pattern: `AdminUsersBean`). **No stray duplicate files to delete in this repo** (that was a legacy-repo finding).
2. Wire `ManageProductBean` to the six use-case interfaces via `@Inject`.
3. Create `manageProduct.xhtml` under `web/.../webapp/product-catalog/` with the image upload (`<h:inputFile>` — no JSF component library), reorder, alt-text, primary-selector, and status controls (spec §8).
4. Manual QA pass against the LocalStack-backed local environment: create a product, upload 2 images, mark one primary, attempt to publish without alt text (expect rejection), add alt text, publish successfully.

**Done when:** backlog story S7's acceptance criteria all pass manual QA, and `@RolesAllowed` is confirmed to actually block non-admin access (test with a non-admin session, not just by reading the annotation).

---

## Step 8 — Public web adapter

Corresponds to backlog story **S8**. Extend `ProductCatalogBean` (already the public-browsing bean) and `web/.../webapp/product-catalog/catalog.xhtml` (already exists) with search/filter/pagination, calling `SearchProductsUseCase`. Consider switching the bean to `@ViewScoped` so filter state survives pagination (spec §8).

**Done when:** backlog story S8's acceptance criteria pass manual QA, including the filter-state-survives-pagination check.

---

## Step 9 — Architecture conformance test

Do this **last**, once all layers exist, so the test has something real to check (writing it earlier against an incomplete codebase gives false confidence).

1. Add ArchUnit (`com.tngtech.archunit:archunit`) as a test dependency to `product-catalog` (mirror the `user-account` test setup: Testcontainers, Surefire `-Dapi.version=1.44`, JPA XSD 3.1, yasson).
2. Write `ProductHexagonalArchitectureTest.java` per spec §13, using `UserHexagonalArchitectureTest` (in `user-account`) as the template: assert `domain` has zero dependencies on `jakarta.*`/`javax.*`; assert `application` depends only on `domain` (plus `jakarta..` for CDI/`@Transactional`, matching the user-account relaxation); assert nothing outside `adapter.out.persistence` imports the JPA entity classes.

**Done when:** the ArchUnit test passes against the codebase as built through Steps 1–8. If it fails, that's a real finding — fix the violating code, don't weaken the test to make it pass.

---

## Full-sequence completion checklist

> Status on 2026-07-31: `[x]` done · `[~]` in progress · `[ ]` not started.

- [x] Step 0 — LocalStack running, bucket bootstrapped, script committed — **completed 2026-07-31 (`scripts/bootstrap-localstack.sh`, image pinned to 3.8.1)**
- [x] Step 1 — Domain model + `ProductTest` green
- [x] Step 2 — Ports compile (no implementations yet) — **completed 2026-07-31**
- [x] Step 3 — Persistence adapter, migration applied, build green project-wide — **`decrementStock` (spec §4) + S9 concurrency test + full green IT (76 tests); `CheckoutService` migrated to `decrementStock`**
- [x] Step 4 — Category tree + caching (adapter, `@ApplicationScoped` cache with invalidation) — **`CategoryRepositoryJpaAdapter` + `CategoryTreeCache` + green IT (7 cases)**
- [x] Step 5 — S3 adapter (`ProductImageStorageS3Adapter`) — **`S3Config`/`S3ConfigProducer` (CDI `@Produces`), `testcontainers-localstack` + 5 green ITs; full suite 89 tests**
- [x] Step 6 — Application service (`ProductApplicationService`, six use cases) + `ProductApplicationServiceTest` — **completed 2026-07-31 (32 green tests; OWASP sanitizer dependency added)**
- [x] Step 7 — Admin UI (`ManageProductBean` + `manageProduct.xhtml`) — **implemented 2026-07-31; manual QA passed 2026-07-31 (create/upload/publish + page guard)**
- [x] Step 8 — Public UI (search/filter/pagination, `@ViewScoped`) — **implemented 2026-07-31; manual QA passed 2026-07-31 (list/search/pagination of ACTIVE products)**
- [x] Step 9 — ArchUnit conformance test green — **completed 2026-07-31 (`ProductHexagonalArchitectureTest`, 8/8 rules, `archunit-junit5` 1.3.0; full unit suite 103 tests)**

Only after every box above is checked is the epic (backlog document, "Epic-level Definition of Done") actually complete.

**Epic complete: 2026-07-31** — Steps 0–9 all `[x]`, including the S7/S8 manual QA passes. Follow-up QA of the adjacent checkout flow was also done the same day (see `README.md`, "Completed" notes).
