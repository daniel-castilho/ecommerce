# Ecommerce Monolith — Boilerplate

Modular monolith in **Jakarta EE 11 + Jakarta Faces**, organized as a
**multi-module Maven** project, with each business module following
**Hexagonal Architecture** and **SOLID / Clean Code** principles.

## Modules

| Module             | Responsibility                                                                                       |
| ------------------ | ---------------------------------------------------------------------------------------------------- |
| `shared-kernel`    | Shared value objects and contracts (`Money`, `DomainEvent`, base exceptions)                        |
| `user-account`     | User registration/query (reference module, fully implemented)                                       |
| `product-catalog`  | Product catalog and search                                                                          |
| `order-checkout`   | Checkout, order calculation, stock reservation                                                      |
| `admin-dashboard`  | Compose metrics from the other modules (no business rules of its own)                               |
| `web`              | Final WAR: aggregates the modules, contains `web.xml`, `faces-config.xml`, `persistence.xml` and the `.xhtml` pages |

## Hexagonal pattern used in each module

```
<module>/src/main/java/com/loja/<module>/
├── domain/
│   ├── model/       -> Entities and Value Objects. Zero framework dependency.
│   └── port/
│       ├── in/      -> UseCases (what the module offers)
│       └── out/      -> Repositories/external services (what the module needs)
├── application/
│   └── service/     -> UseCase implementations. Depends only on ports (DIP).
└── adapter/
    ├── in/web/       -> JSF managed beans (thin, no business rules)
    └── out/persistence/ -> JPA entities + port implementations
```

## Cross-module dependency rules

- A module **never** depends on another module's `adapter` — only on `domain.port` (interfaces).
  Example: `order-checkout` uses `ProductRepositoryPort` from `product-catalog`, never
  `ProductRepositoryAdapter` directly.
- `admin-dashboard` only composes `UseCases` from the other modules — it must not contain business rules.
- `shared-kernel` depends on no other module (it is the base).

## How to build

```bash
mvn clean package -pl web -am
```

The final `.war` ends up at `web/target/web.war`.

## How to run (Open Liberty)

```bash
./scripts/run-liberty.sh   # builds the WAR and brings the app up at https://localhost:9443/web/
```

`mvn clean package` wipes `web/target/liberty` (the installed runtime, the features and the
auto-generated keystore), so run `scripts/run-liberty.sh` after any clean build: it recreates
the server config, installs the features from `server.xml` (first time only) and deploys the
WAR into `dropins`. The dev keystore password lives in `web/src/main/liberty/config/server.xml`.

## Current state (2026-07-31)

- `user-account` — reference module, complete.
- `product-catalog` — epic in progress (see `tasks/product-catalog-implementation-sequence.md`
  and `tasks/product-catalog-backlog.md`). **Done:** Steps 1–3 (domain model, ports, persistence
  adapter, `search()`, `decrementStock` + S9 concurrency test, `V7` migration, unit and integration
  tests green — includes the IT harness fix, docs/lessons.md #3).
  `order-checkout` (`CheckoutService`) migrated to `decrementStock` with
  `InsufficientStockException` when stock is insufficient. Step 4 done
  (`CategoryRepositoryJpaAdapter` + `@ApplicationScoped` cache with invalidation).
  Steps 0 and 5 done (`scripts/bootstrap-localstack.sh` + `ProductImageStorageS3Adapter`
  with `S3Config` via CDI `@Produces`; 5 green ITs against LocalStack Testcontainer).
  Step 6 done (`ProductApplicationService` implementing the six use cases —
  SKU/slug rules, publish guard, image upload validation, description sanitization via
  the OWASP HTML sanitizer; 41 green unit tests, incl. the added `UpdateProductImageUseCase`
  for image-meta/reorder).
  Step 7 done (admin CRUD flow manually QA'd against Open Liberty + LocalStack: create
  `SKU-QA-001`, duplicate-SKU rejection, image upload with alt-text/primary → object in
  `product-images` bucket + `tb_product_image` row + public GET 200, category assignment,
  publish → `ACTIVE`; `@RolesAllowed("ADMIN")` + session-role page guard).
  Step 8 done (public catalog manually QA'd: ACTIVE product listed anonymously with
  name/SKU/price/description and working search/pagination; cards link to checkout — no
  dedicated product-detail page yet).
  Step 9 done (`ProductHexagonalArchitectureTest` — 8/8 ArchUnit rules green: domain
  free of `jakarta.*`/`javax.*`, domain/application isolated from adapters, allowed-dependency
  whitelists, ports are interfaces, `*Adapter` implements interfaces, JPA entities used only in
  `adapter.out.persistence`; `archunit-junit5` 1.3.0 added; full unit suite 103 green tests).
  **Immediate pending items:** none for Steps 7–9.

Completed (2026-07-31): **Bean Validation** on the JSF adapter beans (`adapter/in/web`) —
constraints mirror the domain rules, keeping `domain/` and `application/` free of
`jakarta.validation` (ArchUnit still green). **No new runtime dependency**: the API ships in
`jakarta.jakartaee-api` (provided) and `webProfile-11.0` bundles `validation-3.1` (Hibernate
Validator 9.0.0.Final). Annotated `RegisterBean`, `LoginBean`, `PasswordResetBean`,
`AddressBookBean`, `ProfileBean` (user-account), `ManageProductBean` (product-catalog) and
`CheckoutBean.CartLine` (order-checkout) with `@NotBlank`/`@Email`/`@Size`/`@Pattern`/
`@DecimalMin`/`@Min`/`@NotNull`. `web.xml` gains `jakarta.faces.VALIDATE_EMPTY_FIELDS=true`
(so `@NotBlank`/`@NotNull` fire on blank fields) and the forms that use Bean Validation
now render `h:messages` with `showSummary="false"` (field message duplication removed;
`manageProduct.xhtml` per-field `h:message` dropped). Regression coverage:
`BeanValidationConstraintsTest` (10 tests) + `hibernate-validator`/`tomcat-embed-el`
test-only deps in `user-account`. QA against the server: register (invalid e-mail/short
password/blank name), login (bad e-mail → field error; wrong password → global error),
address book (CEP + 2-char state), manage product (SKU pattern, blank name, price ≤ 0),
checkout (qty 0 → "Quantity must be at least 1"); valid submits still pass (no false
positives). Note: submitting the manage-product form with an **empty category selection**
hits a pre-existing MyFaces NPE (`SelectItemsUtil.matchValue`, `UISelectMany`) unrelated
to Bean Validation. See `docs/lessons.md` #6 for the Liberty-install gotcha.

## LocalStack (local S3)

Image storage uses S3-compatible storage; in dev it runs via LocalStack
(`docker/docker-compose.yaml`, `localstack` service, port 4566, image
`localstack/localstack:3.8.1` — `latest`/4.x versions require an authentication token).

```bash
docker compose -f docker/docker-compose.yaml up -d localstack
./scripts/bootstrap-localstack.sh   # creates the product-images bucket + public-read policy (idempotent)
```

The adapter config reads env/system-property (`s3.endpoint-override`, `s3.bucket`,
`s3.public-base-url`, `s3.region`, `s3.access-key`, `s3.secret-key`) with local
defaults (`http://localhost:4566`, `product-images`, `test`/`test`).

## How to validate fast (avoid Testcontainers)

The `mvn test` for `product-catalog` is slow because `ProductRepositoryJpaAdapterIT` boots a
Postgres via Testcontainers on every run. For fast feedback:

```bash
# Module compilation
mvn -q -pl product-catalog test-compile

# Unit tests (no container, ~2s)
mvn -pl product-catalog test -Dtest='ProductTest,SkuTest,SlugTest,ProductJpaMapperTest,ProductApplicationServiceTest,CategoryTreeCacheTest,ProductHexagonalArchitectureTest' -DfailIfNoTests=false

# Full project compilation (includes order-checkout depending on the ports)
mvn -q -pl order-checkout -am compile

# IT with a real DB (slow): also runs the *IT.java
mvn -pl product-catalog test
```

## Next steps (product-catalog)

1. **Steps 7 + 8 QA:** ~~manual pass against LocalStack~~ **done** (2026-07-31) — admin
   create/upload/publish flow (incl. duplicate-SKU rejection) and public search/pagination.
   QA fixes: `forcePathStyle(true)` on the S3 client (virtual-host broke LocalStack with
   hostname endpoints), `@Cacheable(false)` on `UserJpaEntity` (stale roles), UUID in
   `ProductApplicationService.create()`, FacesServlet `<multipart-config>`. Notes: external
   SQL writes to `tb_category`/roles need a server restart to clear the app caches; the
   public catalog card does not render images yet.

## General pending items (outside the product-catalog epic)

- Set up a real `DataSource` (`java:/EcommerceDS`) in the application server.

Applied (2026-07-31): `V8__order_checkout_schema.sql` on the running `shop_db`
(Postgres 15) and registered in `flyway_schema_history` (rank 7, checksum NULL,
following the V5/V6/V7 manual convention). `tb_order`/`tb_order_item` now exist with
the exact columns of `OrderJpaEntity` (`id` PK, `user_id`, `status`; items with
`product_id`, `quantity`, `unit_price NUMERIC(19,2)`, FK `ON DELETE CASCADE`).

Completed (2026-07-31): unit tests in the `application` layer of the remaining modules
(mocking the `out` ports) — `order-checkout` (`CheckoutServiceTest` 4 tests, `OrderTest` 9
tests), `admin-dashboard` (`DashboardMetricsServiceTest` 2 tests).
Checkout UI built (`CheckoutBean` rewritten with a bindable `CartLine` list + session
user via `SessionPort`; `order-checkout/checkout.xhtml` + `order-confirmed.xhtml`; "Buy"
link per row on `catalog.xhtml` pre-fills the cart). Confirmation page reloads the
persisted order by id (PRG): `OrderRepositoryPort` gained `findById`, implemented in
`OrderRepositoryAdapter`; `OrderJpaMapperTest` covers the round-trip (OPEN/CONFIRMED/
CANCELLED).

Completed (2026-07-31): `OrderJpaEntity` `fromDomain`/`toDomain` mappers — `OrderRepositoryAdapter.save()` now persists (merge + flush + `toDomain`), `Order.cancel()` added to restore `CANCELLED`, `CheckoutService.checkout()` is `@Transactional` (stock decrement + order save are atomic). Backing DDL `V8__order_checkout_schema.sql` (`tb_order`/`tb_order_item`) + `docs/migrations/V8_rollback.sql` — **already applied and registered on `shop_db` (see "Applied" below)**.

Completed (2026-07-31): checkout manually QA'd against Open Liberty — "Buy" pre-fills the cart, order placed (qty 1 and 2) → CONFIRMED with correct user/items/unit price, stock decremented atomically (10→9→7), confirmation page survives refresh (PRG). Failure modes verified: anonymous blocked ("You need to log in before placing an order."), insufficient stock (no order, stock unchanged), product not found, empty cart. **QA fix:** `h:messages` defaulted to summary-only, hiding the failure reason → `showDetail="true"` added on `checkout.xhtml`, `manageProduct.xhtml` and `login.xhtml`.

Completed (2026-07-31): `OrderRepositoryJpaAdapterIT` (4 tests) green via Testcontainers + Postgres —
`mvn -pl order-checkout -am test` → 21 tests green (9 `OrderTest`, 4 `CheckoutServiceTest`, 4
`OrderJpaMapperTest`, 4 IT). Debugging note: the IT initially failed with `Could not find a valid
Docker environment` (HTTP 400 from Docker Desktop 29.2.1+), fixed by pinning the docker-java API
version in surefire (`-Dapi.version=1.44`), see `docs/lessons.md` #4.

Completed (2026-07-31): **user-account flows manually QA'd** against Open Liberty —
register, password reset (request → token in `user_account` → confirm → login with new password;
consumed token rejected), address book (add 2, set default, remove, last-address guard), profile
(name update, password change with wrong-current rejection), admin role assignment (qa user granted
`ADMIN` via `admin/users.xhtml` and immediately reached `manageProduct.xhtml`). **QA bugs found &
fixed:** (1) `password-reset-confirm.xhtml` lost the `?token=` on form POST (`f:viewParam
required`) → replaced with `h:inputHidden` + `@PostConstruct` token seeding; (2) `address-book.xhtml`
used `#{address.default}` — `default` is a reserved EL keyword → 500 → bracket notation
`#{address['default']}`; (3) **root-cause bug:** `addAddress` never assigned an id
(`new Address(null, ...)`), so `Objects.equals(null, null)` matched every address —
"Set as Default" flipped all rows, "Remove" deleted all → `User.addAddress` now assigns a
per-user unique id (max+1) + `UserTest.shouldAssignUniqueAddressIdsWhenNoneProvided`;
(4) `showDetail="true"` also added to `address-book.xhtml` and `profile.xhtml`. See
`docs/lessons.md` #5.
# ecommerce
