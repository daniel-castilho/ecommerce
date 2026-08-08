# Order & Checkout Module — Technical Specification (As-Built)

**Status:** Living document reflecting the implemented module (core epic v0.2.0 + refund workflow v0.6.0).
**Companion docs:** `order-checkout-backlog.md` · `order-checkout-implementation-sequence.md`

The original long-form pre-implementation specification is preserved in git history of this file.

---

## 1. Purpose & Architectural Role

`order-checkout` owns the **order lifecycle**: checkout, payment/shipping/notification ports (mocked), inventory reservation integration, customer order history, and the refund-request workflow.

It depends on:

- `product-catalog` ports (stock reservation / product data)
- `shared-kernel` (`Money`, events, etc.)
- Optionally `user-account` session data via web layer

It does **not** own admin UI (that is composition in `admin-dashboard`).

---

## 2. Package Layout (actual)

```
com.loja.ordercheckout/
├── domain/
│   ├── model/       → Order, OrderLine, OrderStatus, ShippingAddress, PaymentInfo,
│   │                  RefundRequest, RefundStatus, …
│   ├── port/in/     → CreateOrderFromCart, Cancel, Refund, List/Get, report queries, …
│   ├── port/out/    → OrderRepositoryPort, PaymentGatewayPort, ShippingRatePort,
│   │                  NotificationPort, (+ inventory via product-catalog ports)
│   └── exception/
├── application/
│   ├── service/     → OrderApplicationService, RefundApplicationService, report services, …
│   └── dto/         → commands, page results, report rows (never nested in ports)
└── adapter/
    ├── in/web/      → CheckoutBean, OrderHistoryBean, …
    └── out/         → JPA adapters + Payment/Shipping/Notification **mock** adapters
```

Single Maven module: **`order-checkout`** (not a split `order-domain` / `catalog-adapters` layout).

---

## 3. Domain model (as implemented)

### Order aggregate

- Lines are snapshots (product id, name, unit price, quantity, position)
- Status machine (typical path):

```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
         ↘ CANCELLED
CONFIRMED / PROCESSING / SHIPPED → REFUNDED (where allowed)
```

- Terminal states: DELIVERED, CANCELLED, REFUNDED (exact matrix lives in code + domain tests)
- Optimistic locking: `@Version` **round-tripped** domain ↔ JPA

### RefundRequest aggregate (v0.6.0)

```
PENDING → APPROVED → PROCESSED
       ↘ REJECTED
```

Flyway: **`V16__refund_request_workflow.sql`**

### Value objects

- `ShippingAddress`, `PaymentInfo`, `Money` (from shared-kernel)
- Payment/shipping/notification result types used at port boundaries

---

## 4. Application layer

### Core use cases

| Use case                | Responsibility                                                                                            |
| ----------------------- | --------------------------------------------------------------------------------------------------------- |
| Create order from cart  | Validate → reserve inventory → authorize/capture (mock) → persist → notify; **idempotent by `requestId`** |
| Cancel order            | Ownership check; release reservation when applicable                                                      |
| Request / manage refund | Customer path + admin `RefundRequest` workflow                                                            |
| List / get order        | Customer history and detail                                                                               |

### External ports (all mocked in production path today)

| Port                 | Mock behaviour                             |
| -------------------- | ------------------------------------------ |
| `PaymentGatewayPort` | authorize / capture / refund success paths |
| `ShippingRatePort`   | PAC / SEDEX quotes + fake label            |
| `NotificationPort`   | Log / no-op style mock                     |

Real providers are **explicit debt** (need human-approved dependencies).

### Inventory

- Owned by **`product-catalog`** (`InventoryReservationPort`: reserve / confirm / release, TTL)
- Checkout reserves before payment; confirms after success; releases on cancel

### Reporting helpers

Ports/services expose aggregates used by admin reports (revenue, product sales, customer LTV/repeat).
Calculations stay in this module (or user-account for user growth); admin-dashboard only composes.

---

## 5. Persistence

- `OrderJpaEntity` + lines; mapper with full round-trip including **version**
- `RefundRequest` JPA entity (V16)
- Pagination: prefer `getResultList()` over streams
- IT rules: explicit `inTx(...)`, close `EntityManager` in `@AfterEach`

---

## 6. Web / UI

- 4-step checkout wizard → PRG confirmation page
- Customer order history + detail (cancel / refund actions ownership-guarded)
- Admin order/refund screens live under **admin-dashboard** pages

Design-system tokens only; no hard-coded colors.

---

## 7. Security

- Customer actions: authenticated user; ownership checks on order history
- Admin status updates / refund moderation: `@RolesAllowed("ADMIN")` in admin beans + `web.xml`

---

## 8. Testing

- Domain: pure unit tests, no mocks (`OrderTest`, transition matrix, `RefundRequestTest`)
- Application: service tests with mocked ports
- Adapters: `*IT` + Testcontainers
- ArchUnit: `OrderHexagonalArchitectureTest`

---

## 9. What differs from the original spec

| Original aspiration                             | As built                                                  |
| ----------------------------------------------- | --------------------------------------------------------- |
| Separate `order-domain` module                  | Single `order-checkout` module                            |
| Stripe / PagSeguro / Correios / SendGrid        | Mock adapters only                                        |
| Heavy payment-transaction audit entity up front | Right-sized; refund workflow as dedicated aggregate later |
| SMS notifications                               | Not implemented                                           |

---

## 10. Explicit debt

- Real payment, shipping and notification providers
- Richer refund list filters (date range, customer name) in admin UI
- Full audit-log coverage for every refund action (partially evolved over releases)

---

## 11. Definition of Done (module)

- [x] Full checkout with mocks + idempotency
- [x] Order history, cancel, concurrency control
- [x] Inventory reservation integration
- [x] ArchUnit boundaries
- [x] Refund request workflow
- [ ] Real external providers

---

_This document describes the module as implemented. For the original pre-build design (provider SDKs, split modules, full code sketches), see the git history of this file._

```

```
