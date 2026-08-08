# Order & Checkout — Implementation Sequence (As-Built)

**Companion docs:** `order-checkout-module-spec.md` · `order-checkout-backlog.md`

This document records the **actual delivery order** of the order-checkout work.
The original pre-build step list (Stripe/PagSeguro SDKs, separate `order-domain` module, etc.) is preserved in git history of this file.

---

## Guiding principles used

- Single Maven module: **`order-checkout`** (not a split `order-domain` / adapters layout)
- Hexagonal layout under `com.loja.ordercheckout`
- Payment, shipping and notification = **ports + mock adapters only**
- Inventory reservation owned by **`product-catalog`**, consumed via ports
- DTOs in `application/dto` (never nested in ports — ArchUnit)
- Vertical slices preferred over big-bang foundation

---

## Actual delivery sequence

### Phase 1 — Core epic (S1–S12)

**Release: v0.2.0**

| Step | What landed                                                                                                                  |
| ---- | ---------------------------------------------------------------------------------------------------------------------------- |
| 1    | Domain: `Order` aggregate, lines, status state machine, value objects, pure domain tests                                     |
| 2    | Persistence: `OrderJpaEntity` + mapper + `OrderRepositoryPort` adapter; Flyway order tables                                  |
| 3    | Search/pagination by customer and status                                                                                     |
| 4    | `PaymentGatewayPort` + mock (authorize / capture / refund)                                                                   |
| 5    | `ShippingRatePort` + mock (PAC / SEDEX quotes + label)                                                                       |
| 6    | `NotificationPort` + mock                                                                                                    |
| 7    | `CreateOrderFromCartUseCase` — reserve stock → pay → confirm → notify; **idempotent by `requestId`**                         |
| 8    | 4-step JSF checkout wizard → PRG to `order-confirmed.xhtml`                                                                  |
| 9    | Order history + detail; cancel (releases reservation); customer refund path where applicable                                 |
| 10   | Inventory reservation in **product-catalog** (`InventoryReservationPort`, TTL, atomic stock); wired into checkout and cancel |
| 11   | Optimistic locking (`@Version` round-trip domain ↔ JPA); concurrent tests                                                    |
| 12   | `OrderHexagonalArchitectureTest` (ArchUnit)                                                                                  |

Supporting Flyway (among others): order schema, `customer_email`, `inventory_reservation`, order `version` (V11–V15 era).

**Outcome:** Customer can complete checkout end-to-end with mocks; history/cancel work; architecture guards are green.

---

### Phase 2 — Refund request workflow

**Release: v0.6.0**

1. Domain: `RefundRequest` aggregate + `RefundStatus` (PENDING → APPROVED → PROCESSED / REJECTED)
2. `RefundRequestRepositoryPort` + JPA entity/adapter
3. `RefundManagementUseCase` / `RefundApplicationService`
4. Payment mock extended for `processRefund` where needed
5. Flyway **`V16__refund_request_workflow.sql`**
6. Admin list/approve/reject UI delivered in **admin-dashboard** (composition only)

---

### Phase 3 — Reporting support (later releases)

Outbound query methods on order ports/services for admin reports:

- Revenue aggregates (S20)
- Product sales aggregates (S21)
- Customer repeat / LTV helpers (S22)

No report UI inside this module — only data/ports for composition.

---

## What was deliberately _not_ done (vs original plan)

| Original plan                                           | Reality                                                       |
| ------------------------------------------------------- | ------------------------------------------------------------- |
| Separate `order-domain` Maven module                    | Single `order-checkout` module                                |
| Stripe / PagSeguro / Correios / SendGrid SDKs in step 0 | Mock adapters only; real providers deferred                   |
| Real provider credentials before coding                 | Not required for MVP                                          |
| Heavy payment transaction audit tables up front         | Right-sized; refund workflow added later as its own aggregate |

---

## Recommended order for any _new_ work on this module

1. Prefer extending existing ports/use cases over new cross-module imports
2. Keep domain free of `jakarta.*`; put framework code in adapters
3. Place command/result DTOs in `application/dto`
4. Update domain transition-matrix tests in the **same** change when the state machine changes
5. Run unit + ArchUnit fast; use `*IT` + Testcontainers for persistence
6. After WAR changes, smoke checkout + order history on Open Liberty
7. Real payment/shipping/email = **new dependency → ask human first**

---

## Useful commands

```bash
# Fast unit + ArchUnit
mvn -pl order-checkout test -Dtest='*Test' -DfailIfNoTests=false

# Persistence ITs
mvn -pl order-checkout test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false

# Full WAR + run
mvn clean package -pl web -am
./scripts/run-liberty.sh
```

Smoke path (core flow):

1. Login as customer
2. Catalog → product → 4-step checkout → order CONFIRMED
3. “My orders” → cancel a CONFIRMED order (stock released)
4. Refresh confirmation page — same order (idempotent `requestId`)

---

## Definition of Done (sequence)

- [x] Domain + persistence + mocks + checkout UI
- [x] Order history / cancel / concurrency / ArchUnit
- [x] Inventory reservation integration
- [x] Refund request workflow + admin moderation path
- [ ] Real payment, shipping and notification providers

---

_This is the as-built execution record. For the original step-by-step pre-implementation plan, see the git history of this file._

```

```
