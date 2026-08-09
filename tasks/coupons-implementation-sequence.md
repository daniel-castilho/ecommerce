### 4. `tasks/coupons-implementation-sequence.md`

```markdown
# Coupons / Promotions — Implementation Sequence

**Companions:** `coupons-module-spec.md` · `coupons-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not invent scope (no category coupons until S11).

---

## Step 0 — Module skeleton

1. Create Maven module `promotions` (mirror `wishlist` POM shape)
2. Wire root `pom.xml` + `web` + `order-checkout` (port dependency only) + `admin-dashboard`
3. Package tree `com.loja.promotions`
4. Confirm next Flyway version (after current head; e.g. V22+)

**Done when:** `mvn -pl promotions test-compile` succeeds.

---

## Step 1 — Domain

`Coupon`, `CouponType`, factory/invariants, discount quote helper (pure), domain exceptions
(`CouponNotFoundException`, `CouponNotApplicableException`, …).

Unit tests for percent/fixed/cap-at-subtotal/inactive/window.

**Done when:** domain tests green; zero framework imports.

---

## Step 2 — Ports + DTOs

Inbound admin + quote/redeem ports; `CouponRepositoryPort`; DTOs in `application/dto`.

**Done when:** interfaces compile.

---

## Step 3 — Application services

Create/list/setActive; quote discount; redeem (increment).
Unit tests with mocked repository.

**Done when:** service tests green.

---

## Step 4 — Persistence

Entity, mapper, adapter, Flyway `tb_coupon`, unique on `code`, ITs (unique, increment).

**Done when:** ITs green.

---

## Step 5 — Order model + migration

- Add `couponCode` + `discountAmount` to domain `Order` and JPA mapping
- Update `getTotal()` = subtotal − discount + shipping
- Flyway alter `tb_order`
- Fix/extend order unit tests and any report queries that assume old total shape

**Done when:** order module tests green; total math covered.

---

## Step 6 — CreateOrderFromCart integration

- Extend `CheckoutCommand` with optional `couponCode`
- Validate/quote before persisting order
- Snapshot on order; redeem only after successful persist (same JTA tx preferred)
- Invalid code → domain/application exception → bean message

**Done when:** order-checkout unit tests cover with/without coupon and invalid code.

---

## Step 7 — Checkout UI

- Field on review or payment step
- Display discount line and updated total
- FacesMessage on failure

**Done when:** manual smoke path ready (with Step 8).

---

## Step 8 — Admin UI

- Pages under admin-dashboard (list + create + activate/deactivate)
- Beans compose promotions ports only
- `@RolesAllowed("ADMIN")`; covered by existing admin URL patterns or extend `web.xml` if needed
- `AdminAccessControlCoverageTest` still green

**Done when:** admin can create `SAVE10` and deactivate it.

---

## Step 9 — ArchUnit + WAR wiring

- `PromotionsHexagonalArchitectureTest`
- Register entities in `persistence.xml`
- Full `mvn test` relevant modules + `mvn clean package -pl web -am`

**Done when:** CI-equivalent gate green locally.

---

## Step 10 — Release prep

Update README current state / CHANGELOG / optional `docs/releases/v0.X.0.md` when human requests tag.

---

## Smoke path

1. Login as ADMIN → create coupon `SAVE10`, PERCENT 10, active
2. Login as customer → checkout with items → enter `SAVE10`
3. Totals show discount; place order succeeds
4. Order history/admin detail shows code + discount amount
5. Deactivate coupon → checkout with same code fails cleanly
6. Checkout with blank code still works as before

---

_Pre-implementation sequence. After delivery, replace with an as-built note and keep this plan in git history if desired._
```
