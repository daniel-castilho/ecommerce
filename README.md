# Ecommerce Monolith — Boilerplate

Modular monolith built with **Jakarta EE 11 + Jakarta Faces**, organized as a
**multi-module Maven** project. Each business module follows
**Hexagonal Architecture** and **SOLID / Clean Code** principles.

## Modules

| Module            | Responsibility                                                                                  |
| ----------------- | ----------------------------------------------------------------------------------------------- |
| `shared-kernel`   | Shared value objects and contracts (`Money`, `DomainEvent`, base exceptions)                    |
| `user-account`    | Registration, authentication, profile, addresses and roles (reference module)                   |
| `product-catalog` | Product catalog, search, stock management and images (S3)                                       |
| `order-checkout`  | Checkout, order lifecycle, inventory reservation and refunds                                    |
| `product-reviews` | Product reviews & ratings (submit, summary, verified purchase, moderation)                      |
| `admin-dashboard` | Back-office composition (metrics, management screens, reports) — no own rules                   |
| `web`             | Final WAR: aggregates all modules, contains `web.xml`, Faces config, persistence unit and pages |

## Hexagonal Pattern

```
<module>/src/main/java/com/loja/<module>/
├── domain/
│   ├── model/          → Entities & Value Objects (zero framework dependencies)
│   └── port/
│       ├── in/         → Use cases the module offers
│       └── out/        → Ports the module needs
├── application/
│   └── service/        → Use-case implementations (depend only on ports)
└── adapter/
    ├── in/web/         → Thin JSF managed beans
    └── out/persistence/→ JPA entities + port implementations
```

### Cross-module rules

- A module **never** depends on another module’s `adapter` — only on `domain.port`.
- `admin-dashboard` only **composes** use cases from other modules.
- `shared-kernel` has no dependencies on other modules.

## Build

```bash
mvn clean package -pl web -am
```

The final WAR is produced at `web/target/web.war`.

## Run (Open Liberty)

```bash
./scripts/run-liberty.sh
```

The application will be available at:
https://localhost:9443/web/

> Note: `mvn clean package` removes the Liberty runtime under `web/target/liberty`.
> Always prefer `./scripts/run-liberty.sh` after a clean build.

## Current State

**Latest release: [v0.15.0](docs/releases/v0.15.0.md)** (2026-08-08)

- Audit log viewer complete: filters, full-text tooltip, **resolved admin-name actor column**,
  and **entity type/ID columns** (USER / PRODUCT / REFUND / ADDRESS)
- Wishlist module complete (S1–S10): add/remove toggle on the product detail, a
  `/web/wishlist/wishlist.xhtml` list page (login-guarded, per-item remove, empty state),
  and a ♥/♡ heart toggle on catalog cards
- Order status timeline + payment snapshot on admin order detail
- Blocked / suspended accounts cannot place orders
- Product cost price with gross profit margin on the admin product list and product performance report
- Audit log filters (actor, event type, details, date range) with full-text tooltip on long details
- Full **Reviews & Ratings** module (`product-reviews`)
- Admin reporting lane completed: Revenue, Product Performance, Customer Insights reports with **CSV/PDF export**
- Real Jakarta Security RBAC
- Design-system tokens and shared components
- ArchUnit architectural guards on every module
- Complete storefront and back-office flows

Full release history: see **[CHANGELOG.md](CHANGELOG.md)** or the individual notes under [`docs/releases/`](docs/releases/).

## Local Development

### LocalStack (S3 for product images)

```bash
docker compose -f docker/docker-compose.yaml up -d localstack
./scripts/bootstrap-localstack.sh
```

### Fast feedback (skip Testcontainers)

```bash
# Compile only
mvn -q -pl product-catalog,order-checkout,product-reviews,admin-dashboard -am test-compile

# Unit tests + ArchUnit only (no containers)
mvn test -Dtest='*Test' -DfailIfNoTests=false
```

## Roadmap / Pending

- Real payment, shipping and notification providers (currently mocked)
- PDF reports embed charts as images (currently exported as data tables)
- Further search improvements (PostgreSQL full-text search / ranking)

## Documentation

| Document                                       | Purpose                                                |
| ---------------------------------------------- | ------------------------------------------------------ |
| [AGENTS.md](AGENTS.md)                         | Rules for AI agents and human contributors             |
| [docs/design-system.md](docs/design-system.md) | Design tokens, components and the “rule of two”        |
| [docs/lessons.md](docs/lessons.md)             | Hard-won lessons and golden rules                      |
| [docs/releases/](docs/releases/)               | Detailed release notes                                 |
| [tasks/](tasks/)                               | Backlogs, technical specs and implementation sequences |
| [CHANGELOG.md](CHANGELOG.md)                   | High-level release index                               |
