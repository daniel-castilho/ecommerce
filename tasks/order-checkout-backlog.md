Order & Checkout — Backlog Status

**Companion documents:**
`order-checkout-module-spec.md` · `order-checkout-implementation-sequence.md`

**Epic goal:** Enable customers to complete checkout end-to-end and manage their orders, with inventory reservation, payment/shipping/notification ports (mock for now), and a refund-request workflow for admins.

---

## Current Status Summary

| Area                                            | Status                | Notes                                                                       |
| ----------------------------------------------- | --------------------- | --------------------------------------------------------------------------- |
| Domain model + state machine (S1)               | ✅ Done               | PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED + CANCELLED/REFUNDED |
| JPA persistence + search (S2–S3)                | ✅ Done               | Aggregate mapping, pagination, filters                                      |
| Payment / Shipping / Notification ports (S4–S6) | ✅ Done               | **Mock adapters only**                                                      |
| Create order + 4-step checkout UI (S7–S8)       | ✅ Done               | Idempotent by `requestId`, PRG to confirmation                              |
| Order history + cancel/refund (S9)              | ✅ Done               | Customer-facing; ownership-guarded                                          |
| Inventory reservation (S10)                     | ✅ Done               | Lives in `product-catalog`; wired into checkout                             |
| Optimistic locking / concurrency (S11)          | ✅ Done               | `@Version` round-trip + friendly reload message                             |
| ArchUnit boundaries (S12)                       | ✅ Done               | `OrderHexagonalArchitectureTest`                                            |
| Refund request workflow                         | ✅ Done               | `RefundRequest` + `V16` (v0.6.0)                                            |
| Real payment / shipping / email providers       | ❌ Pending            | Explicit debt — mocks only                                                  |
| Customer notification on refund approve/reject  | ⚠️ Partial / deferred | Depends on notification module maturity                                     |

**Core epic (S1–S12):** completed in **v0.2.0**.
**Refund workflow:** completed in **v0.6.0**.

---

## Implemented Stories

### Foundation (v0.2.0)

- **S1** — `Order` aggregate, lines, money/address/payment value objects, state machine, domain tests (no mocks)
- **S2** — `OrderJpaEntity` + mapper + repository adapter; Flyway order schema
- **S3** — Paginated queries by customer, status, etc.

### External ports (v0.2.0) — mocks only

- **S4** — `PaymentGatewayPort` + mock (authorize / capture / refund)
- **S5** — `ShippingRatePort` + mock (PAC/SEDEX quotes, label)
- **S6** — `NotificationPort` + mock (order confirmed / shipped / refund)

### Checkout & post-purchase (v0.2.0)

- **S7** — `CreateOrderFromCartUseCase` — reserve inventory → payment → confirm → notify; idempotent by `requestId`
- **S8** — 4-step JSF checkout wizard → `order-confirmed.xhtml` (PRG)
- **S9** — Order history + detail; cancel (releases reservation); customer-initiated refund path where applicable

### Cross-cutting (v0.2.0)

- **S10** — Inventory reservation in `product-catalog` (TTL, atomic stock update, wired before/after payment and on cancel)
- **S11** — Optimistic locking (`@Version` in domain ↔ JPA); concurrent checkout / status races covered
- **S12** — ArchUnit: domain free of frameworks; ports are interfaces; DTOs in `application/dto`

### Refund request workflow (v0.6.0)

- `RefundRequest` aggregate: PENDING → APPROVED → PROCESSED / REJECTED
- `RefundRequestRepositoryPort` + JPA + Flyway **`V16__refund_request_workflow.sql`**
- `RefundManagementUseCase` / `RefundApplicationService`
- Admin UI for list/approve/reject lives in **admin-dashboard** (composition only)

### Reporting support (later releases)

- Aggregation queries used by admin reports (revenue, product sales, customer LTV/repeat) are exposed via `OrderRepositoryPort` and related services — still no business rules in admin-dashboard.

---

## Still Pending / Explicit Debt

| Item                               | Notes                                                                     |
| ---------------------------------- | ------------------------------------------------------------------------- |
| Real payment provider              | Stripe / PagSeguro / etc. — needs human-approved dependency + credentials |
| Real shipping provider             | Correios / carrier API — mock only today                                  |
| Real email / notification          | See `docs/notification-system-guide.md`                                   |
| Richer refund filters              | Date range, customer name, sort — optional UX                             |
| Blocking vs badge-only for reviews | Owned by `product-reviews`; order-checkout only exposes verification data |

---

## How the module is structured today

```
com.loja.ordercheckout/
├── domain/model + port/in + port/out + exception
├── application/service + application/dto
└── adapter/
    ├── in/web/          → checkout & order-history beans
    └── out/             → JPA + payment/shipping/notification mocks
```

- Inventory reservation: **product-catalog** ports
- Admin order/refund screens: **admin-dashboard** composition
- Security: customer ownership checks on history; admin actions behind `@RolesAllowed("ADMIN")` in the web/admin layer

---

## Definition of Done (Epic)

- [x] End-to-end checkout with mocks
- [x] Order history + cancel/refund paths
- [x] Inventory reservation integrated
- [x] Optimistic locking + ArchUnit
- [x] Refund request workflow for admin moderation
- [ ] Real payment / shipping / notification providers

---

_This backlog is a living status document. For the original INVEST story catalogue (Given/When/Then, story points, DoR/DoD), see the git history of this file._

```

```
