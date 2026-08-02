# Product Catalog Module — Implementation Specification

**Audience:** this document was written for an AI coding agent that implemented the product-catalog module in the `ecommerce` monolith. It assumes the stack and conventions established in the project's `java-jakarta-ee-coding-standards.md` (Jakarta EE 11, multi-module Maven layout, CDI/hexagonal architecture, JPA, JSF/Facelets, `java.util.logging`) **and the project's adopted target architecture: hexagonal (ports & adapters).**

**Status:** implemented and verified (epic delivered 2026-07-31 — see the banner at the top). Section 14 lists assumptions the implementer should flag back to the human if they turn out to be wrong. See the companion documents for backlog breakdown (`product-catalog-backlog.md`) and step-by-step build order (`product-catalog-implementation-sequence.md`).

> ## ✅ EPIC DELIVERED (2026-07-31)
>
> All sections of this spec are implemented and verified in the `product-catalog` module. The architecture (§0) and scope remain accurate; module/class names in the body resolve to the **`ecommerce` monolith** layout below. Where the body conflicts with the facts in this box, **the box wins**.
>
> **Real repo (`/home/castilho/projects/ecommerce`):** single Maven module `product-catalog` (package root `com.loja.productcatalog`) with hex packages `domain/model`, `domain/port/in`, `domain/port/out`, `application/service`, `application/dto`, `adapter/in/web`, `adapter/out/persistence`, `adapter/out/storage`. The `user-account` module is the reference for every convention (ports in `domain/port`, `@ApplicationScoped` + `@Transactional`, only `domain` framework-free, thin JSF beans, Strategy for conditional logic, CDI observers for events).
>
> **As-built state:** `Product` (String UUID id, name, description, `Money` price, stock, `ProductStatus`, `reserveStock()`/`decrementStock`), `Sku`/`Slug` value objects, `ProductJpaEntity` (table `tb_product`, migration `V7__product_catalog_extension.sql`), `ProductJpaMapper`, `ProductRepositoryAdapter` (Criteria `search()` + atomic `decrementStock`), `Category` + `CategoryTreeCache` (`@ApplicationScoped`, invalidation on mutation), `ProductImageStorageS3Adapter` (LocalStack; `scripts/bootstrap-localstack.sh`), `ProductApplicationService` (six use-case ports), `ManageProductBean` + `manageProduct.xhtml`, `ProductCatalogBean` (`@ViewScoped`) + `catalog.xhtml`, `ProductHexagonalArchitectureTest` (ArchUnit). Module suite green incl. Testcontainers/LocalStack ITs; `order-checkout` consumes `ProductRepositoryPort` + `Product.reserveStock` (`CheckoutService`).
>
> **Resolved repo decisions (override §14 open questions):**
> 1. Currency — single-currency; **reuse `com.loja.shared.domain.Money` (shared-kernel)**, do not create a new Money.
> 2. HTML sanitizer — **still open**: no sanitizer dependency exists in the repo yet.
> 3. Config mechanism — **no MicroProfile Config** anywhere; S3/other config must follow env/system-property lookup (confirm the exact pattern at implementation time).
> 4. Migration tooling — `flyway/sql/` already exists (`V5__user_account_schema.sql`, `V6__add_actor_to_audit_log.sql`) with **manual `flyway_schema_history` registration and no Flyway runner**. New migration = `V7__product_catalog_extension.sql`, registered by hand, same pattern.
> 5. Admin file upload — no JSF component library; plain `<h:inputFile>`.
> 6. Testcontainers — already the repo standard (user-account: Testcontainers 1.21.3, Surefire `-Dapi.version=1.44`, JPA XSD 3.1, yasson); `testcontainers-localstack` is a new but consistent dependency.
> 7. Module-per-layer split — **NO**: the repo is module-per-bounded-context; hex layers are enforced by package + ArchUnit (`UserHexagonalArchitectureTest` is the template to mirror).
> 8. Reference pattern — `product-catalog` is already the hex home for Product; follow `user-account` conventions, not the legacy EJB/cache classes named in the body.
>
> **Other deltas:** IDs are `String` UUID (`VARCHAR(36)`), not `Long`; use-case/outbound ports live in `domain/port/in` and `domain/port/out` (not `application/port`); DTOs in `application/dto`; `docker/docker-compose.yaml` **already defines** `db` (postgres:15, `shop`) and `localstack` (s3, port 4566) — only the bucket bootstrap script is missing; `AdminUsersBean` + `@RolesAllowed("ADMIN")` in `adapter/in/web` is the admin-UI precedent (pages in `web/.../webapp/`); admin pages follow `web/.../webapp/user-account/admin/users.xhtml` as a template.

---

## 0. Architecture: hexagonal (ports & adapters)

The project has committed to hexagonal architecture as part of its ongoing modernization. This spec follows that decision. If anything below reads as more layers than a "simple CRUD module" would need, that's deliberate — consistency with the project's chosen architecture matters more than local simplicity for one module.

**Dependency rule (the one rule that must never be violated):** dependencies point inward only. `domain` depends on nothing. `application` depends only on `domain`. `adapters` depend on `application` + `domain`. Nothing in `domain` or `application` may import a JPA, CDI, JSF, or AWS SDK class. If a domain or application class needs `@Entity`, `@Inject`, or an S3 client type, that is a sign the code is in the wrong layer.

**Layers used in this spec:**

| Layer | Contains | May depend on |
|---|---|---|
| **Domain** | `Product`, `ProductImage`, `Category` as plain Java objects (no JPA annotations), value objects (`Sku`, `Slug`, `Money`), domain exceptions, and the business rules that belong to the object itself (e.g. "can this product be published?") | nothing (zero framework imports, not even `jakarta.*`) |
| **Application** | Use-case interfaces (inbound ports), use-case implementations (application services), and outbound port interfaces (`ProductRepositoryPort`, `ProductImageStoragePort`, `CategoryRepositoryPort`) | `domain` only |
| **Adapters — inbound** | JSF managed beans that call use cases | `application`, `domain` |
| **Adapters — outbound** | JPA entities + repository implementations (`persistence-jpa`), S3/LocalStack client wrapper (`storage-s3`) | `application`, `domain` |

**Why application services keep the `@Transactional`/CDI-scoped boundary, not the adapters:** a stricter hexagonal reading pushes even transaction demarcation out to adapters. In practice, a use case that touches the product repository and (on failure) needs to roll back an image already uploaded to S3 needs to coordinate both — that coordination is an application-layer responsibility. So application services are `@ApplicationScoped`/`@Transactional` CDI beans that depend on port *interfaces* (injected via CDI, resolved to the adapter implementation at runtime) — the beans themselves stay framework-aware for lifecycle/transaction purposes, but they never import a JPA entity type or the AWS SDK. This is a deliberate, documented pragmatic deviation from academic hexagonal purity — flag it in code review if the team prefers a stricter split later.

---

## 1. Purpose & scope

Extend the current minimal `ProductEntity`/`CategoryEntity` pair into a production-viable Product Catalog module: richer product data, product images backed by S3-compatible object storage, category tree support, search/filter/pagination, soft delete, audit fields, optimistic concurrency, and admin-only mutation security.

**In scope:** single-SKU products (no variant/option matrix), category tree, image management via object storage, search/listing, CRUD with validation, stock tracking primitives (decrement/increment, not full inventory reservation workflow).

**Out of scope (explicitly deferred):**
- Product variants (size/color/etc.) — schema should not actively block adding this later, but no variant tables are created now.
- Multi-currency / multi-locale content.
- Inventory reservation / hold-on-checkout workflow (belongs to the future Order module).
- Full-text search engine (Elasticsearch/OpenSearch) — this spec uses database-level search; note where the abstraction should live so it can be swapped later without touching callers.

---

## 2. Module / package layout

Existing Maven modules are repurposed to hexagonal layers rather than replaced — this avoids a disruptive module rename/split in the same change that adds a whole new feature. If the project's hexagonal migration later wants full module-per-adapter separation (recommended long-term — see §14), that's a separate, mechanical refactor.

```
catalog-core/                          → DOMAIN + APPLICATION (ports & use cases)
  src/main/java/.../catalog/domain/
  ├── Product.java                  (new — plain domain object, NOT @Entity)
  ├── ProductImage.java             (new — plain domain object)
  ├── Category.java                 (new — plain domain object)
  ├── Sku.java, Slug.java, Money.java   (new — value objects)
  ├── ProductStatus.java            (new enum)
  └── exception/
      ├── DuplicateSkuException.java
      ├── ProductNotFoundException.java
      ├── InsufficientStockException.java
      └── InvalidProductImageException.java

  src/main/java/.../catalog/application/
  ├── port/in/
  │   ├── CreateProductUseCase.java     (new — interface)
  │   ├── UpdateProductUseCase.java     (new — interface)
  │   ├── PublishProductUseCase.java    (new — interface)
  │   ├── ArchiveProductUseCase.java    (new — interface)
  │   ├── SearchProductsUseCase.java    (new — interface)
  │   └── UploadProductImageUseCase.java (new — interface)
  ├── port/out/
  │   ├── ProductRepositoryPort.java    (new — interface)
  │   ├── CategoryRepositoryPort.java   (new — interface)
  │   └── ProductImageStoragePort.java  (new — interface)
  └── service/
      ├── ProductApplicationService.java   (new — implements the use-case interfaces above)
      └── ProductSearchCriteria.java, PageResult.java  (new — application-layer DTOs)

catalog-adapters/                                    → OUTBOUND ADAPTERS
  src/main/java/.../catalog/adapter/persistence/
  ├── entity/
  │   ├── ProductJpaEntity.java          (renamed/replaces old ProductEntity — see migration note below)
  │   ├── ProductImageJpaEntity.java     (new)
  │   ├── CategoryJpaEntity.java         (renamed/replaces old CategoryEntity)
  │   └── AuditableJpaEntity.java        (new @MappedSuperclass)
  ├── ProductJpaMapper.java              (new — domain Product <-> ProductJpaEntity)
  ├── ProductRepositoryJpaAdapter.java   (new — implements ProductRepositoryPort, wraps the old ProductDao query logic)
  └── CategoryRepositoryJpaAdapter.java  (new — implements CategoryRepositoryPort)

  src/main/java/.../catalog/adapter/storage/
  └── ProductImageStorageS3Adapter.java  (new — implements ProductImageStoragePort, AWS SDK v2 S3 client; endpoint overridden for LocalStack locally, real S3 in prod — see §6)

catalog-web/servlet/                        → INBOUND ADAPTER
  src/main/java/.../web/jsf/beans/
  ├── ProductBean.java               (extend — public catalog browsing, calls SearchProductsUseCase)
  └── ManageProductBean.java         (new — admin CRUD, calls Create/Update/Publish/Archive/UploadImage use cases)

  src/main/webapp/
  ├── products.xhtml                 (extend)
  └── manageProduct.xhtml            (extend)
```

**Migration note on renaming `ProductEntity` → `ProductJpaEntity`:** the existing `ProductEntity`/`CategoryEntity` classes are JPA entities living in `catalog-core`, which under hexagonal rules is domain territory and must not contain `@Entity`. This spec renames and *moves* them into `catalog-adapters/.../adapter/persistence/entity/` as `ProductJpaEntity`/`CategoryJpaEntity`, and introduces new framework-free `Product`/`Category` domain classes in `catalog-core`. This is a real breaking change to existing imports (`ProductBean`, `ProductDao`, `ProductEjb`, the mappers, and their tests all currently reference `ProductEntity` directly) — see the implementation sequence document for the exact order this must happen in to avoid a half-migrated, non-compiling intermediate state.

**Rationale for splitting `ManageProductBean` out of `ProductBean`:** the current `ProductBean` mixes public catalog browsing and admin editing in one class. A public listing bean and an admin CRUD bean have different security postures and different scopes (`@ViewScoped` browsing vs. `@RequestScoped`-per-form-submit editing) and should not share a class — independent of hexagonal architecture, this was already true under the old layering.

---

## 3. Domain model

**Every type in this section that is *not* explicitly labeled "JPA entity" is a plain Java object with no framework annotations.** The `Product`/`Category`/`ProductImage` domain classes hold state and the behavior described below (validation, invariants); the `*JpaEntity` classes described afterward are pure persistence records with no business logic, mapped to/from the domain objects by `ProductJpaMapper`.

### 3.1 `AuditableJpaEntity` (new `@MappedSuperclass`, persistence adapter only)

Shared audit + optimistic-locking fields for `ProductEntity` and `CategoryEntity`, applied via lifecycle callbacks (not a separate `EntityListener` class, to keep it simple and colocated):

```java
@MappedSuperclass
public abstract class AuditableEntity {

    @Version
    private Long version;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // getters only (no setters — these fields are framework-managed)
}
```

`@Version` gives optimistic locking on concurrent edits (e.g., two admin sessions editing the same product) — the container throws `OptimisticLockException`, which the service layer must translate into an application-level `ConcurrentModificationException` with a user-facing "this product was changed by someone else, please reload" message.

### 3.2 `ProductStatus` (new enum)

```java
public enum ProductStatus {
    DRAFT,      // not visible in public catalog, editable
    ACTIVE,     // visible in public catalog
    INACTIVE,   // temporarily hidden (e.g. out of season), not visible
    ARCHIVED    // soft-deleted, never visible, excluded from all default queries
}
```

### 3.3 `Product` domain object (new, `catalog.domain` — no JPA)

The domain object holds the fields below and the business methods that used to be scattered across the service layer (§5 shows how the application service now delegates to these methods instead of re-implementing the checks):

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` (nullable until persisted) | |
| `sku` | `Sku` (value object wrapping `String`) | Immutable after construction. |
| `slug` | `Slug` (value object wrapping `String`) | Validates URL-safe pattern in its constructor — an invalid `Slug` cannot be constructed at all. |
| `name` | `String` | |
| `shortDescription`, `description` | `String`, nullable | |
| `price` | `Money` (value object wrapping `BigDecimal`) | Rejects negative/zero in its constructor. |
| `compareAtPrice` | `Money`, nullable | |
| `stock` | `int` | |
| `status` | `ProductStatus` | |
| `weightGrams` | `Integer`, nullable | |
| `metaTitle`, `metaDescription` | `String`, nullable | |
| `categoryIds` | `Set<Long>` | Domain object references categories by id, not by object graph — keeps `Product` independent of `Category`'s own persistence concerns. |
| `images` | `List<ProductImage>` | |

Business methods that live **on the domain object itself** (this is what makes it a domain object rather than a data bag):

- `void validateForPublishing()` — throws a domain exception listing every unmet condition if price/image/category/name checks (§9) fail. Called by `PublishProductUseCase` before flipping status to `ACTIVE`.
- `void markImageAsPrimary(Long imageId)` — enforces the "exactly one primary image" invariant internally (unmarks the previous primary).
- `void removeImage(Long imageId)` — if the removed image was primary, promotes the next-lowest-position image automatically.
- `boolean canTransitionTo(ProductStatus target)` — encodes the allowed status transitions (e.g. `ARCHIVED` is terminal, nothing transitions out of it) so illegal transitions are rejected before they ever reach persistence.

Value objects (`Sku`, `Slug`, `Money`) are simple immutable wrappers with validation in their constructors — this is what "make illegal states unrepresentable" looks like in practice: a `Product` can never hold an invalid SKU format or a negative price, because the value object wrapping it would have failed to construct.

### 3.4 `ProductJpaEntity` (persistence adapter, extends the existing `ProductEntity`)

This is the renamed/relocated version of today's `ProductEntity` (see the migration note in §2), living in `catalog-adapters/.../adapter/persistence/entity/`. It is a plain persistence record — **no business logic**, only JPA mapping metadata. Add the following columns (on top of the existing `id`, `name`, `price`, `stock`, `categories`):

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `sku` | `String` | `unique`, `nullable=false`, `length=64` | Maps to/from the domain `Sku` value object in `ProductJpaMapper`. |
| `slug` | `String` | `unique`, `nullable=false`, `length=160` | Maps to/from `Slug`. |
| `shortDescription` | `String` | `length=500`, nullable | |
| `description` | `String` | `@Lob`, nullable | Sanitized HTML — sanitization happens in the application service, before it ever reaches the domain object or this entity. |
| `compareAtPrice` | `BigDecimal` | nullable, `precision=19, scale=2` | |
| `status` | `ProductStatus` | `nullable=false`, `@Enumerated(STRING)`, default `DRAFT` | Store as `STRING`, never `ORDINAL` — reordering the enum must not corrupt existing data. `ProductStatus` itself is a domain enum with zero framework dependency, safe to reference from the JPA entity directly. |
| `weightGrams` | `Integer` | nullable | |
| `metaTitle` | `String` | `length=160`, nullable | |
| `metaDescription` | `String` | `length=300`, nullable | |
| `images` | `List<ProductImageJpaEntity>` | `@OneToMany(mappedBy="product", cascade=ALL, orphanRemoval=true)` | Ordered by `position`. |

Also:
- `price` keeps `nullable=false` but add `precision=19, scale=2` explicitly (currently implicit — be explicit to avoid silent truncation on some DB drivers).
- Remove the two commented-out `@ManyToOne`/`@OneToOne` category blocks entirely — dead code (see coding-standards.md §11 finding #6).
- Extend `AuditableJpaEntity`.
- `ProductJpaMapper` is the *only* class allowed to construct a `Product` domain object from a `ProductJpaEntity` or vice versa. Nothing else should reach into a `ProductJpaEntity` directly outside the persistence adapter package.

### 3.5 `ProductImageJpaEntity` (new)

```java
@Entity
@Table(name = "PRODUCT_IMAGE_ENTITY")
public class ProductImageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private ProductJpaEntity product;

    @Column(name = "OBJECT_KEY", nullable = false, length = 512)
    private String objectKey;      // S3 object key, e.g. "products/{sku}/{uuid}.webp"

    @Column(name = "ALT_TEXT", length = 200)
    private String altText;        // accessibility + SEO, required before status=ACTIVE

    @Column(name = "POSITION", nullable = false)
    private Integer position;      // display order, 0-based

    @Column(name = "IS_PRIMARY", nullable = false)
    private boolean primary;       // exactly one true per product — enforced by Product.markImageAsPrimary()

    // constructors, getters/setters
}
```

**Deliberate design choice:** store only the `objectKey`, never a full URL, in the database. Public base URL / CDN domain is environment configuration (bucket can move between environments — dev MinIO vs. prod S3+CDN — without a data migration). URL construction happens in the mapper (`ProductImageModel`), reading the base URL from a CDI-injected config bean.

---

## 4. Data access layer (`ProductDao`)

Extend the already-fixed `ProductDao` (typed named query, `@Stateless`) with these methods. Use the **Criteria API**, not string-concatenated JPQL, since filters are optional and combinable:

```java
public interface ProductSearchCriteria {
    // all fields optional/nullable — null means "no filter on this field"
    String nameOrSkuContains;
    Long categoryId;
    BigDecimal minPrice;
    BigDecimal maxPrice;
    ProductStatus status;          // defaults to ACTIVE for public-facing calls
    int page;                      // 0-based
    int pageSize;                  // default 20, hard cap 100
    ProductSortField sortField;    // NAME, PRICE, CREATED_AT
    SortDirection sortDirection;   // ASC, DESC
}
```

Required methods on `ProductRepositoryPort` (interface, `application/port/out`), implemented by `ProductRepositoryJpaAdapter` (`catalog-adapters/.../adapter/persistence`):

- `PageResult<Product> search(ProductSearchCriteria criteria)` — the adapter builds a JPA `CriteriaQuery` against `ProductJpaEntity` dynamically from non-null fields, maps results back to domain `Product` objects via `ProductJpaMapper`, and runs a matching `count()` query for total pages. **Always** apply `status != ARCHIVED` unless the caller explicitly requests archived (admin-only "trash" view).
- `Optional<Product> findBySku(Sku sku)` — for uniqueness checks and lookups.
- `Optional<Product> findBySlug(Slug slug)` — for public PDP routing.
- `boolean existsBySku(Sku sku)` — cheap existence check (`SELECT COUNT` capped at 1, don't fetch the entity).
- `boolean existsBySlug(Slug slug)`.
- `int decrementStock(Long productId, int quantity)` — **bulk `UPDATE` query**, not entity load + save:
  ```
  UPDATE ProductJpaEntity p SET p.stock = p.stock - :qty
  WHERE p.id = :id AND p.stock >= :qty
  ```
  Return the affected-row count from `executeUpdate()`. The calling application service treats `0` as "insufficient stock" and throws `InsufficientStockException`. This avoids the lost-update race condition that a read-modify-write would have under concurrent checkouts, and is independent of the `@Version` optimistic lock (which still protects other field edits made through the entity).
- `Product save(Product product)`, `Optional<Product> findById(Long id)` — standard create/update/read, mapping through `ProductJpaMapper` both directions.

Note the signature change from the pre-hexagonal draft: the port speaks in domain types (`Product`, `Sku`, `Slug`) everywhere, never `ProductJpaEntity`. The adapter is the only place that ever imports `ProductJpaEntity`.

The old `ProductDao`'s existing query logic (already fixed to use a typed named query — see coding-standards.md §11) is not thrown away; it becomes the starting point for `ProductRepositoryJpaAdapter`'s implementation, just relocated and wrapped behind the port interface with domain-typed inputs/outputs.

---

## 5. Application layer (use cases)

Business rules that do not belong in the persistence adapter, the storage adapter, or the JSF bean. **Prefer putting a rule on the `Product` domain object itself (§3.3) when the rule only needs the object's own state; put it in the application service when it needs to consult a port** (e.g. checking uniqueness against the repository, or coordinating repository + storage together).

`ProductApplicationService` implements `CreateProductUseCase`, `UpdateProductUseCase`, `PublishProductUseCase`, `ArchiveProductUseCase`, `SearchProductsUseCase`, `UploadProductImageUseCase` — one class implementing several narrow interfaces is fine here (it's still one cohesive unit of behavior); split it only if it grows unwieldy.

1. **SKU generation/validation** (`CreateProductUseCase`). SKU is provided by the admin at creation (not auto-generated, this is a manual catalog for now). Before persisting: normalize to uppercase, trim, construct a `Sku` value object (which itself validates format), then call `productRepositoryPort.existsBySku()`. If true, throw `DuplicateSkuException` — do not rely solely on the DB unique constraint for the user-facing error path, but the DB constraint stays as the safety net for race conditions (the adapter catches `PersistenceException`/`ConstraintViolationException` and re-throws as the same domain exception, in case two concurrent requests both pass the pre-check).
2. **Slug generation** (`CreateProductUseCase`/`UpdateProductUseCase`). On create, if the admin didn't supply a slug, derive one from `name` (lowercase, ASCII-fold accents, replace non-alphanumerics with `-`, collapse repeats, trim `-`). Check `existsBySlug()`; if taken, append `-2`, `-3`, etc. until unique. Slug is editable afterward by admin but must re-run the uniqueness check on update, excluding the product's own id.
3. **Publish guard** (`PublishProductUseCase`). Calls `product.validateForPublishing()` (§3.3 — the rule lives on the domain object since it only needs the product's own state) before calling `productRepositoryPort.save()` with the new status. If the domain method throws, the use case does not touch the repository at all — don't let a product go live with a $0 price or no image.
4. **Primary image invariant.** Enforced by `Product.markImageAsPrimary()`/`removeImage()` on the domain object (§3.3); `UploadProductImageUseCase` calls these methods, then persists the resulting state via the repository port.
5. **Compare-at price validation.** Enforced by the `Money`/domain-object construction itself — an invalid `Product` (compareAtPrice ≤ price) cannot be constructed, so this rule is unrepresentable rather than checked.
6. **Soft delete** (`ArchiveProductUseCase`). Never calls a delete/remove method on any port. Loads the product, calls `product.canTransitionTo(ARCHIVED)` to confirm the transition is legal, sets status, saves. Physical deletion, if ever needed, is a separate manual/admin-only operation not exposed through the normal use-case API.
7. **Category assignment.** A product must belong to at least one category before it can be `ACTIVE` — this check needs `CategoryRepositoryPort` to confirm the referenced category ids actually exist, so it lives in `PublishProductUseCase` (application layer), not on the domain object (which only has `Set<Long> categoryIds`, no way to verify existence on its own).

---

## 6. Image storage integration (S3-compatible: LocalStack local, AWS S3 prod)

### 6.1 Architecture decision

Given the app is server-rendered JSF (not an SPA), use the **server-proxied upload** pattern rather than presigned direct-to-bucket upload: the admin's browser submits the file to the JSF managed bean via a file-upload component, the bean calls `UploadProductImageUseCase`, which calls `ProductImageStoragePort` (implemented by `ProductImageStorageS3Adapter`, using the AWS SDK v2 S3 client — the same SDK and client code work against LocalStack and real AWS S3, only the endpoint configuration differs). This keeps the upload synchronous within the existing request-scoped bean pattern and avoids introducing client-side JavaScript/fetch just for this one flow.

> Future enhancement (not in this phase): switch to presigned-URL direct upload if/when the admin panel gets a JS-heavy rewrite. Note this so it isn't forgotten, but don't build it speculatively now.

### 6.2 `ProductImageStorageS3Adapter` (new, `catalog-adapters/.../adapter/storage`, implements `ProductImageStoragePort`)

```java
@ApplicationScoped
public class ProductImageStorageS3Adapter implements ProductImageStoragePort {

    // Config via CDI + MicroProfile Config (or equivalent env-var lookup
    // already used elsewhere in the project — confirm existing pattern
    // before introducing a new config mechanism). The same S3Client code
    // path serves both environments; only these values change:
    //
    //   s3.endpoint-override   LOCAL:  http://localhost:4566  (LocalStack)
    //                          PROD:   unset — SDK resolves the real AWS
    //                                  regional endpoint automatically
    //   s3.bucket              e.g. "product-images"
    //   s3.public-base-url     LOCAL:  http://localhost:4566/product-images
    //                          PROD:   https://cdn.example.com (CloudFront
    //                                  in front of the bucket, or the
    //                                  bucket's own public endpoint)
    //   s3.region              e.g. "us-east-1" (LocalStack needs a value
    //                                  even though it doesn't enforce it)
    //   s3.access-key / s3.secret-key
    //                          LOCAL:  LocalStack accepts any non-empty
    //                                  dummy value ("test"/"test" is the
    //                                  LocalStack convention)
    //                          PROD:   real IAM credentials, injected via
    //                                  environment/secret store — never
    //                                  committed to the repo

    String upload(byte[] content, String contentType, String suggestedKeyPrefix);
    void delete(String objectKey);
    String publicUrlFor(String objectKey);
}
```

**Client construction detail:** build the `S3Client` with `.endpointOverride(...)` **only when `s3.endpoint-override` is configured** (i.e. only in local/LocalStack profiles). Leaving it unset in prod lets the SDK resolve the real regional AWS endpoint — don't hardcode a prod endpoint string, and don't make the prod path go through the override branch at all, to avoid a misconfigured local value silently leaking into a prod deploy.

**Validation before upload (in the application service, not just the UI):**
- Allowed content types: `image/jpeg`, `image/png`, `image/webp`. Reject anything else with `InvalidProductImageException`.
- Max file size: 5 MB. Reject larger files before attempting upload.
- Max images per product: 8. Enforce in `ProductApplicationService`, not the storage adapter (the adapter is dumb I/O — it has no business rules).

**Object key convention:** `products/{sku}/{uuid}.{ext}` — grouping by SKU makes bulk cleanup/debugging easier; the UUID avoids filename collisions and avoids leaking the original filename.

**Bucket policy:** public-read for this bucket in both environments (product images are meant to be public; LocalStack supports bucket policies the same way real S3 does). Do not use presigned GET URLs for serving — that adds needless latency and complexity for content that has no confidentiality requirement. `publicUrlFor()` simply concatenates the configured base URL with the object key.

**Local dev (WSL2/Docker):** run LocalStack via `docker-compose` alongside the existing Liberty/GlassFish service. Add (or create, if no `docker-compose.yml` exists yet — confirm first, the earlier project dump referenced Docker) a service block equivalent to:

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

The bucket itself is not created by LocalStack automatically — add a one-time bootstrap step (either a small shell script run after `docker-compose up`, or a `docker-compose` `depends_on` init container) that runs `aws --endpoint-url=http://localhost:4566 s3 mb s3://product-images` and applies the public-read bucket policy. Document this in the project's local setup instructions (README or `AGENTS.md`) so it isn't a one-time tribal-knowledge step.

---

## 7. Category tree

Extend `Category` (domain) / `CategoryJpaEntity` (persistence adapter):

| Field | Type | Notes |
|---|---|---|
| `parent` | `Category`/`CategoryJpaEntity` (`@ManyToOne`, nullable) | Self-referencing; `null` = top-level category. |
| `slug` | `String` (unique) | Same generation rule as product slugs. |
| `position` | `Integer` | Manual ordering within siblings, admin-controlled. |
| `active` | `boolean` | Hide a category (and implicitly its products from category-browse, but not from direct search) without deleting it. |

Remove the commented-out `@OneToMany(mappedBy="category")` dead code — same rationale as `ProductJpaEntity`.

**Caching:** the project already has a `CategoryCacheEjb` pattern — extend it rather than introducing a second caching mechanism. The full category tree changes infrequently and is read on every catalog page load, so it's a good caching candidate; invalidate the cache on any category create/update/delete through the application service (not by TTL alone, to avoid stale-tree admin confusion right after an edit).

---

## 8. Web layer (inbound adapter, JSF)

- `products.xhtml`: add search input, category filter (populated from the cached tree), price range filter, sort dropdown, and pagination controls (prev/next + page numbers). Bind to `ProductSearchCriteria` on `ProductBean` (`@ViewScoped` — this page has multi-step interaction: filter, then paginate, within one conversation, so `@RequestScoped` would lose filter state between clicks). `ProductBean` depends only on `SearchProductsUseCase` (injected as the interface — CDI resolves it to `ProductApplicationService` at runtime), never on the repository port or the JPA entity directly.
- `manageProduct.xhtml`: add image upload control (`<h:inputFile>` or the project's existing form component convention — confirm what's already used elsewhere in the admin views before introducing a new one), image reorder (simple up/down buttons are sufficient, no drag-and-drop needed for v1), alt-text field per image, primary-image radio selector, status dropdown gated by the publish guard (§5.3) — surface validation failures as `FacesMessage`s, not silent rejection.
- New `ManageProductBean` (`@RequestScoped`, per the split described in §2): depends on `CreateProductUseCase`, `UpdateProductUseCase`, `PublishProductUseCase`, `ArchiveProductUseCase`, `UploadProductImageUseCase` — each injected as its own interface. Keep it thin — no business logic, just form binding and translating domain/application exceptions into `FacesMessage`s.

---

## 9. Validation rules (summary table)

| Field | Rule | Enforced where |
|---|---|---|
| `sku` | required, unique, max 64 chars, uppercase-normalized | Application service (pre-check) + DB unique constraint (safety net) |
| `name` | required, max 200 chars | Bean Validation `@NotBlank @Size` on the inbound DTO |
| `slug` | required, unique, URL-safe pattern `^[a-z0-9]+(-[a-z0-9]+)*$` | `Slug` value object constructor (generation + format) + application service (uniqueness) |
| `price` | required, `> 0`, precision 19/2 | `Money` value object constructor |
| `compareAtPrice` | optional, must be `> price` if set | `Product` domain object constructor (cross-field — unrepresentable otherwise) |
| `stock` | required, `>= 0` | Bean Validation `@NotNull @PositiveOrZero` on the inbound DTO |
| `description` | optional, HTML sanitized on input (strip `<script>`, event handlers, disallowed tags) | Application service, using a whitelist sanitizer — do not hand-roll regex stripping |
| image `contentType` | one of jpeg/png/webp | `ProductApplicationService` (before calling the storage port) |
| image size | `<= 5 MB` | `ProductApplicationService` |
| images per product | `<= 8` | `ProductApplicationService` |
| publish (`status=ACTIVE`) | price set, ≥1 image with alt text, ≥1 category, name/slug non-blank | `Product.validateForPublishing()` (domain) + category-existence check (`PublishProductUseCase`) |

---

## 10. Security & authorization

Per the project's existing `jakarta.security.enterprise` usage (already ahead of the rest of the codebase per the coding-standards review — use it as the reference pattern):

- Read operations (`SearchProductsUseCase`, category listing) — public, no authentication required.
- Write operations (`CreateProductUseCase`, `UpdateProductUseCase`, `PublishProductUseCase`, `ArchiveProductUseCase`, `UploadProductImageUseCase`) — require an authenticated user with an `ADMIN` role. Apply `@RolesAllowed("ADMIN")` on the `ProductApplicationService` methods (the application layer is the actual enforcement boundary here, not the JSF bean) — the bean-level check, if any, is only a UX convenience.
- Do not trust `status` or `price` values submitted from the admin form without server-side re-validation — client-side JSF validation is UX, not a security boundary (already a stated principle in the project's coding standards, §10).

---

## 11. Database schema changes

New tables: `PRODUCT_IMAGE_ENTITY`. Altered tables: `PRODUCT_ENTITY` (new columns per §3.4), `CATEGORY_ENTITY` (new columns per §7). Table names stay as-is (`PRODUCT_ENTITY`, `CATEGORY_ENTITY`) even though the Java class is renamed to `ProductJpaEntity`/`CategoryJpaEntity` — no reason to force a table rename/data migration just to match a Java-side naming convention.

**Migration tooling:** confirm whether the project already uses Flyway or Liquibase (not visible in the reviewed file set — if absent, this is a good time to introduce one rather than relying on Hibernate's `hbm2ddl.auto=update` for anything beyond local dev, since schema drift on a shared/prod DB via auto-DDL is a common source of surprises). If neither is present, recommend Flyway (simpler SQL-first migrations, fits a small solo project better than Liquibase's XML/YAML abstraction) and scaffold `V2__product_catalog_extension.sql` covering:

- `ALTER TABLE PRODUCT_ENTITY ADD COLUMN ...` for all new columns, with `NOT NULL` columns given a `DEFAULT` for the migration itself (e.g. `status DEFAULT 'DRAFT'`) since existing rows need a value.
- Unique indexes: `product_entity_sku_uq`, `product_entity_slug_uq`, `category_entity_slug_uq`.
- New `PRODUCT_IMAGE_ENTITY` table with FK to `PRODUCT_ENTITY` (`ON DELETE CASCADE` — consistent with `orphanRemoval=true` on the JPA side).
- Index on `PRODUCT_ENTITY.status` (filtered on nearly every public query).
- **Rollback:** pair `V2__product_catalog_extension.sql` with a documented manual rollback (`DROP TABLE PRODUCT_IMAGE_ENTITY; ALTER TABLE PRODUCT_ENTITY DROP COLUMN ...`) kept in `docs/migrations/V2_rollback.sql` — Flyway doesn't auto-generate down-migrations, so write it by hand alongside the forward migration, not as an afterthought if something breaks in prod.

---

## 12. Testing requirements

Hexagonal architecture's biggest practical payoff is here: the domain (`Product`, `Sku`, `Slug`, `Money`) and application layer (`ProductApplicationService`) can be fully unit-tested with **zero container, zero database, zero S3** — everything they depend on is either pure Java or a port interface, trivially mockable.

- `ProductTest` (domain, no mocks needed at all): `validateForPublishing()` pass/fail combinations, `markImageAsPrimary()`/`removeImage()` invariant maintenance, `canTransitionTo()` transition rules, `Money`/`Sku`/`Slug` constructor validation (invalid input rejected at construction).
- `ProductApplicationServiceTest`: SKU-duplicate rejection, slug auto-generation and collision handling, publish-guard delegation to the domain object, soft-delete behavior (verify `status` changes, no repository `delete` method ever called) — mock `ProductRepositoryPort`, `CategoryRepositoryPort`, `ProductImageStoragePort` (all interfaces, so no framework test tooling needed to mock them).
- `ProductImageStorageS3AdapterTest`: content-type/size rejection paths, and — since this adapter is the one place real I/O happens — an integration-style test against a **LocalStack Testcontainer** (the `testcontainers-localstack` module) rather than a hand-rolled mock S3 client, so the test exercises the real AWS SDK call path.
- `ProductJpaMapperTest`: domain `Product` ↔ `ProductJpaEntity` round-trip mapping, especially the value-object unwrapping/wrapping (`Sku` ↔ `String`, `Money` ↔ `BigDecimal`).
- `ProductRepositoryJpaAdapterTest` (querying logic, bulk `decrementStock` race behavior): needs a real or embedded database — recommend Testcontainers with the project's actual DB image over H2, since JPQL bulk-update semantics and constraint behavior can differ subtly between H2 and the production DB engine. Confirm existing test infrastructure before adding a new dependency.

---

## 13. Non-functional targets

- Default page size 20, hard cap 100 (reject/clamp larger requests rather than erroring).
- Search/list query: index-backed on `status`, `sku`, `slug`; category filter joins through `PRODUCT_CATEGORY` (already indexed via the `@JoinTable` FK).
- Image uploads capped at 5 MB client-side check too (fail fast before the multi-second upload, not just after).
- No N+1 on category listing pages: when loading products with their categories for a list view, use `@EntityGraph` or an explicit `JOIN FETCH` in the Criteria query rather than relying on lazy loading per row.
- **Architecture conformance:** add an ArchUnit test (`HexagonalArchitectureTest`) asserting the dependency rule from §0 mechanically — e.g. `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAPackage("..jakarta.persistence..")`. Without an automated check, the domain/adapter boundary erodes silently over time as new code gets added under time pressure; this is cheap insurance and directly enforces the architecture the project has committed to.

---

## 14. Open questions / assumptions for review

> **Update (July 30, 2026):** item 2 is now **resolved — OWASP Java HTML Sanitizer** (whitelist-based; add `org.owasp.owasp-java-html-sanitizer` as a new dependency at S5/S7 time). Items 1 and 3–8 are already resolved by the banner at the top of this document (repo decisions). No open questions remain before implementation.

Flag these back if any assumption is wrong before implementing:

1. **Currency:** assumed single-currency (BRL implied by prior project context) — no `currency` field added to `Money`. Confirm this is correct for now.
2. **HTML sanitizer library:** assumed a whitelist-based sanitizer (e.g. OWASP Java HTML Sanitizer) will be added as a new dependency — confirm this is acceptable or if a specific library is preferred.
3. **Config mechanism:** assumed MicroProfile Config or existing env-var pattern is available for S3 credentials/endpoint (LocalStack local / AWS S3 prod) — confirm what the project already uses elsewhere so this doesn't introduce a second, inconsistent config approach.
4. **Migration tool:** assumed Flyway is not yet present and should be introduced — confirm, since retrofitting a migration tool onto an existing schema needs a baseline migration step that captures current state first.
5. **Admin file-upload component:** assumed plain `<h:inputFile>` (Jakarta Faces 4+) is acceptable — confirm no third-party JSF component library (PrimeFaces, etc.) is already the project's convention for forms, to stay consistent.
6. **Testcontainers:** recommended for both the S3 adapter test (`testcontainers-localstack`) and the JPA repository adapter test — confirm this is an acceptable new test dependency, or state the existing test strategy if one already exists that wasn't visible in the reviewed files.
7. **Module-per-layer split:** this spec keeps the existing 3 Maven modules and separates hexagonal layers by *package* within them (domain+application in `catalog-core`, both adapter types in `catalog-adapters`). A stricter setup would give each layer/adapter its own Maven module so the dependency rule is enforced by the build itself (a module simply cannot `import` a class from a module it doesn't depend on in its `pom.xml`), which is a stronger guarantee than the ArchUnit test in §13 alone. Confirm whether to do the fuller module split now or treat it as a follow-up refactor once the package-level structure has proven itself.
8. **`AGENTS.md` / `docs/action_plan.md` alignment:** confirm this hexagonal restructuring of the Product module is meant to be the reference pattern for migrating the rest of the app (User, Order, etc.) afterward, or if it's scoped to Product Catalog only for now — affects how much of §0's reasoning is worth writing back into the project's own architecture docs versus keeping local to this spec.
