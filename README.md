# Ecommerce Monolith — Boilerplate

Modular monolith built with **Jakarta EE 11 + Jakarta Faces**, organized as a
**multi-module Maven** project. Each business module follows
**Hexagonal Architecture** and **SOLID / Clean Code** principles.

## Modules

| Module            | Responsibility                                                                                  |
| ----------------- | ----------------------------------------------------------------------------------------------- |
| `shared-kernel`   | Shared value objects and contracts (`Money`, `DomainEvent`, base exceptions)                    |
| `user-account`    | Registration, authentication, profile, addresses and roles (reference module)                   |
| `product-catalog` | Product catalog, search (incl. Postgres FTS ranking), stock management and images (S3)          |
| `order-checkout`  | Cart, checkout, order lifecycle, inventory reservation and refunds                              |
| `promotions`      | Discount coupons (admin CRUD, quote at checkout, order snapshot)                                |
| `product-reviews` | Product reviews & ratings (submit, summary, verified purchase, moderation)                      |
| `wishlist`        | Personal wishlist (toggle, list page, catalog heart icon)                                       |
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

**Latest tag: [v0.17.1](docs/releases/v0.17.1.md)** (2026-08-10)

- **Persistent cart** (S1–S12): add from product detail or catalog card; manage quantities on
  `/web/order-checkout/cart.xhtml`; checkout from a durable cart cleared only after a confirmed
  order. **Guest cart** uses a session UUID in `user_id`; on login, lines merge into the user cart.
  Checkout still requires an account.
- **Postgres FTS ranking**: catalog text search ranks by `ts_rank` over name/SKU/short description
  (GIN index, V25); prefix `tsquery` with ILIKE fallback; default sort `RELEVANCE`.
- **Order notifications** (Phase A+B): best-effort email for confirmed/shipped + refund events via
  `OrderNotificationEmailAdapter` (Jakarta Mail). Never blocks checkout: SMTP failures are logged,
  not thrown; respects `UserProfile.notificationsEnabled`. Phase B adds a **delivery log**
  (`tb_notification_delivery_log`, V26): every event is claimed with a unique idempotency key
  (`EVENT:{orderId}`) and recorded SENT/FAILED with attempt count and error.
- **Coupons** (`promotions`): admin create/list, checkout discount quote, snapshot on the order (V22/V23).
- **Wishlist** (S1–S10): detail toggle, list page, catalog ♥/♡.
- **Reviews & Ratings** (`product-reviews`): submit, summary, verified purchase, admin moderation.
- Admin: reporting (CSV/PDF), refund filters, audit log (actor name + entity type/id), order timeline,
  cost price / margin, RBAC, design-system tokens, ArchUnit on every module.

Full release history: **[CHANGELOG.md](CHANGELOG.md)** or [`docs/releases/`](docs/releases/).

## Local Development

### LocalStack (S3 for product images)

```bash
docker compose -f docker/docker-compose.yaml up -d localstack
./scripts/bootstrap-localstack.sh
```

### Fast feedback (skip Testcontainers)

```bash
# Compile only
mvn -q -pl product-catalog,order-checkout,product-reviews,promotions,wishlist,admin-dashboard -am test-compile

# Unit tests + ArchUnit only (no containers)
mvn test -Dtest='*Test' -DfailIfNoTests=false
```

## Roadmap / Pending

- Real payment, shipping and notification providers (currently mocked — order email is real,
  payment/shipping still mocked)
- Notification async decoupling (Phase C: outbox / JMS) only if latency or volume ever forces it
- PDF reports embed charts as images (currently data tables)
- Optional coupon depth: category/product scope, per-user redemption limits
- Hardening: guest-cart edge cases, cart↔coupon regression smoke

## Documentation

| Document                                             | Purpose                                                |
| ---------------------------------------------------- | ------------------------------------------------------ |
| [AGENTS.md](AGENTS.md)                               | Rules for AI agents and human contributors             |
| [docs/testing-playbook.md](docs/testing-playbook.md) | Test pyramid, regression checklist, failure triage     |
| [docs/design-system.md](docs/design-system.md)       | Design tokens, components and the “rule of two”        |
| [docs/lessons.md](docs/lessons.md)                   | Hard-won lessons and golden rules                      |
| [docs/releases/](docs/releases/)                     | Detailed release notes                                 |
| [tasks/](tasks/)                                     | Backlogs, technical specs and implementation sequences |
| [CHANGELOG.md](CHANGELOG.md)                         | High-level release index                               |

```

*(Opcional no mesmo commit: no `docs/testing-playbook.md`, na seção **Do not**, troque a linha do guest cart por algo como: “multi-coupon stack or new libs without a story + human OK”.)*

---

### Próximo passo

O storefront core (catálogo, reviews, wishlist, cupom, cart + guest, checkout) está **maduro**. Eu priorizaria nesta ordem:

| Prioridade | O quê | Por quê |
|------------|--------|---------|
| **1. Hygiene de release** | Tag **v0.17.0** (guest cart S12 + FTS V25) + notas + CHANGELOG | `main` já passou do tag; doc e histórico ficam honestos |
| **2. Smoke de regressão** | Guest → add → login (merge) → cupom → pedido → cart vazio; busca FTS no catálogo | Fecha o que acabou de entrar sem épico novo |
| **3. Próximo épico de produto** | **Notificações** (pedido confirmado / status) *ou* **provedor real de pagamento** (ainda mock) | Único gap grande de “loja de verdade”; há `docs/notification-system-guide.md` no repo |
| **4. Evolução menor** | Cupom por categoria/produto (S11 do épico de cupons) | Só se quiser profundidade de promo antes de infra |

**Recomendação objetiva:** faça **(1) + (2)** ainda hoje/amanhã. Depois escolha **notificações** se quiser valor de UX com mock de e-mail/outbox, ou **pagamento real** se o objetivo for fechar o caminho feliz comercial.

```
