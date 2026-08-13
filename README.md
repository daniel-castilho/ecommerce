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

**Latest tag: [v0.19.0](docs/releases/v0.19.0.md)** (2026-08-11)

- **v0.19.0 coupon scope**: coupons target specific products/categories (`CouponScope`:
  ALL/PRODUCT/CATEGORY) and can cap redemptions per user; the checkout discounts only the
  eligible cart lines (V29 adds scope columns + the `tb_coupon_redemption` per-user ledger).
- **Persistent cart** (S1–S12): add from product detail or catalog card; manage quantities on
  `/web/order-checkout/cart.xhtml`; checkout from a durable cart cleared only after a confirmed
  order. **Guest cart** uses a session UUID in `user_id`; on login, lines merge into the user cart.
  Checkout still requires an account.
- **Postgres FTS ranking**: catalog text search ranks by `ts_rank` over name/SKU/short description
  (GIN index, V25); prefix `tsquery` with ILIKE fallback; default sort `RELEVANCE`.
- **Admin reporting PDFs embed charts (v0.19.1)**: the Revenue, Product Performance and
  Customer Insights PDF exports now draw vector bar/line charts (pure OpenPDF, token colors)
  reflecting the on-screen series — CSV and tables unchanged.
- **Order notifications** (Phase A+B+C): best-effort email for confirmed/shipped + refund events.
  Since Phase C the email is never sent on the request thread: checkout claims a **transactional
  outbox** row on `tb_notification_delivery_log` (V26+V27) with an idempotency key
  (`EVENT:{orderId}`) and a rendered snapshot (`recipient_email`/`subject`/`body`); a fixed-delay
  poller (`NotificationOutboxDispatcher`, 5 s) dispatches due rows via
  `NotificationOutboxProcessor`, recording SENT/FAILED with attempt count and error. Since v0.18.1
  (V28) retries are gated behind an exponential-ish backoff (`next_attempt_at`: 30 s → 2 min →
  5 min) and the final of 3 failures escalates to **EXHAUSTED** (never polled again); admins see
  every delivery on `/web/admin-dashboard/notifications/list.xhtml` and can Re-queue a stuck row.
  Best-effort: SMTP failures never block checkout; respects `UserProfile.notificationsEnabled`.
- **Order notification emails are multipart (v0.19.2)**: since V30 the outbox snapshots an
  inline-styled **HTML variant** (`body_html`) alongside the text body at claim time and the poller
  sends `multipart/alternative` (`text/plain` + `text/html`, design-token colors, HTML-escaped
  user/catalog strings). Pre-V30 rows stay text-only; resend re-sends the same stored snapshots.
- **v0.18.2 QA fixes**: admin delivery-log "Resend" verifies (tag-rewrite of the confirm
  modal + `&&` escaping) and the outbox poller shuts down cleanly on app redeploy/stop.
- **v0.18.3 fixes**: the confirm modal's no-JSF fallback now submits via
  `form.requestSubmit(el)` (the old `el.click()` hit the guarded button's `return false`
  and silently cancelled the submit), and the profile logout lives in a proper `<h:form>`.
- **v0.18.4 hardening**: checkout rejects lines whose product is missing or no longer
  ACTIVE; the guest-cart merge can no longer fail a login (retry + isolation in the
  observer); `Coupon.create` enforces the validity window, `Order.applyCoupon` refuses a
  double apply, and redemption serializes `used_count` under a pessimistic write lock.
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

> Coupon depth (category/product scope, per-user redemption limits) is **done (v0.19.0)**:
> coupons restrict discounts to specific products/categories, cap redemptions per user via
> the `tb_coupon_redemption` ledger, and the checkout quotes against eligible lines only.

> Hardening item 4 (guest-cart edge cases, cart↔coupon regression smoke) is **done
> (v0.18.4)**: checkout rejects non-ACTIVE products, guest merge never fails login, coupon
> window/double-apply invariants, and atomic `used_count` redemption, covered by unit tests
> and a guest→merge→coupon→checkout IT.

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
