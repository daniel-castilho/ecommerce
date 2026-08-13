# Testing Playbook

**Role:** Write and interpret tests for this Jakarta EE 11 hexagonal monolith (Open Liberty, Testcontainers, ArchUnit).
**Stack constraints:** JUnit 5 + AssertJ + Mockito only — no new test deps without human approval.

Sources: `AGENTS.md` · `docs/lessons.md` · module `*IT` / ArchUnit · epic docs under `tasks/`

---

## Pyramid

1. **Domain unit** — invariants, Money, coupon math, cart rules, state machines
2. **Application unit** — mocked ports; happy path + rejection + idempotency
3. **Adapter IT** — real DB; constraints; `@Version`; Flyway
4. **ArchUnit** — package boundaries
5. **Smoke** — login → flow → check outcome on Liberty

This is not a public JSON API project. Exercise **ports / use cases**; Faces beans only at the edge. Never adapter → adapter across modules.

---

## Mandatory patterns

- IT setup: `TRUNCATE … CASCADE` · writes inside **`inTx`** · `em.clear()` after commit
- Queries: **`getResultList()`**, not `getResultStream()`
- Round-trip `@Version` in JPA mappers
- Instant comparisons: allow small precision tolerance
- English names: `add_whenProductInactive_rejects`, not `test1`

---

## Regression checklist

| Area | Must verify |
|------|-------------|
| **Auth** | Server-side userId; customer blocked from admin |
| **Catalog** | Only ACTIVE in customer flows; safe fallback if unavailable |
| **Cart** | Persist across restart; one line/product; empty cart cannot order; clear only after successful order; checkout rejects lines whose product is no longer ACTIVE (cart survives) |
| **Guest → login** | Merge folds lines (quantities summed); guest cart deleted; merge never fails login (retry on optimistic-lock conflict, swallow+reset otherwise) |
| **Coupons** | Blank = no discount; invalid fails place-order; discount on merchandise only; snapshot on Order; `usedCount` after success only; **window `validFrom ≤ validTo` enforced at create**; **one coupon per order (double-apply rejected)**; **concurrent redemptions never exceed `maxTotalUses`** |
| **Checkout** | Totals = lines − discount + shipping ≥ 0; idempotency preserved |
| **Wishlist / reviews** | Ownership by userId; idempotent add where specified |
| **Admin** | Composition only; ADMIN RBAC |

**Guest → coupon end-to-end regression** (`OrderApplicationServiceIT`): guest adds item → login merge folds it into the user cart → checkout with coupon → confirmed order carries the coupon snapshot, `redeem` is invoked, and the cart is cleared. The "product deactivated after add" variant must fail cleanly at checkout without touching payment or clearing the cart.

---

## Release regression smoke (browser)

Run on Liberty against the **freshly built WAR** (`mvn clean package -pl web -am`, then `./scripts/run-liberty.sh`). Needs the dev DB migrated to the latest Flyway version and `docker compose up -d localstack` for product images.

Known QA values in a stocked dev DB: login `qa.admin@loja.com` / `SmokeTest@123` (ADMIN + CUSTOMER); products `SKU-QA-001` (QA Test Widget, $29.90); coupons `SAVE10` (10% off, `ALL` scope) and `EXHAUST1` (5% off, exhausted). If a fresh DB shows fewer values, create a product and coupon from the admin dashboards first — the checklist does not depend on their exact ids.

**Required — run before every release tag. Stop and fix on first failure.**

| # | Step | Expected → verify |
|---|------|-------------------|
| 1 | Log in (`/web/user-account/login.xhtml`) | Land on catalog, no Faces error |
| 2 | Catalog FTS: search `widget` | Only `QA Test Widget` returned |
| 3 | Log out. As **guest**, add widget to cart, go to cart, click **Log in to check out** | Quantities merge with the user's existing cart (fold, not overwrite); single line per product |
| 4 | `Cart` → `Proceed to checkout` | Step 1 review lists cart lines + subtotal |
| 5 | Step 2: fill shipping address, **Check shipping rates** | PAC + SEDEX options shown with costs |
| 6 | Step 3: email, payment token, coupon `SAVE10` → Continue | No validation error |
| 7 | Step 4 Confirm: coupon + discount shown; subtotal − discount + shipping = total | Total ≥ 0; discount **not** applied to shipping |
| 8 | **Place Order** | `/order-confirmed.xhtml?orderId=…`, status **CONFIRMED** |
| 9 | Admin `/web/admin-dashboard/notifications/list.xhtml` | Row for that order's `ORDER_CONFIRMED`; Recipient = `qa.admin@loja.com` |

**SQL proof (Postgres, `docker exec -it shop_db psql -U user -d shop`):**

```sql
-- order confirmed (substitute the orderId from the URL)
SELECT id, user_id, status FROM tb_order WHERE id = '<orderId>';

-- notification delivery recorded (best-effort; SENT only if a local SMTP sink is up)
SELECT event_type, channel, status, attempt_count, recipient_email
FROM tb_notification_delivery_log WHERE aggregate_id = '<orderId>';

-- coupon redeemed exactly once per order, used_count incremented
SELECT r.user_id, r.redeemed_at, c.used_count
FROM tb_coupon_redemption r JOIN tb_coupon c ON c.id = r.coupon_id
WHERE c.code = 'SAVE10' ORDER BY r.redeemed_at DESC LIMIT 1;

-- guest cart folded (run right after step 3, before ordering): target user's cart has
-- exactly one line per product with summed quantities (empty after step 8 clears the cart)
SELECT u.email, l.product_id, SUM(l.quantity) AS qty
FROM tb_cart_line l
JOIN tb_cart ct ON ct.id = l.cart_id JOIN user_account u ON u.id = ct.user_id
WHERE u.email = 'qa.admin@loja.com'
GROUP BY u.email, l.product_id ORDER BY u.email;
```

**Optional — spot-check on the same session (do not block the tag):**

| Area | Step | Expected |
|------|------|----------|
| Scoped coupon | Admin `Coupons` → **New coupon** (`PRODUCT` scope, list `SKU-QA-001`'s UUID, max 2 uses/user) → save → list | Renders with `Scope = PRODUCT`; `Valid` window formatted, page does **not** 500 |
| Coupon guardrails | Admin coupon list shows `SAVE10` as Active, `EXHAUST1` as `1 / 1` | `Used / Max` reflects the redemption ledger |
| Admin reports | `/web/admin-dashboard/reports/revenue.xhtml` → dates `dd/MM/yyyy` → **Generate** | Renders totals + chart, no Faces error; CSV/PDF export buttons present |
| Empty cart guard | Checkout with an **empty** cart, click **Continue to shipping** | Stays on review (Step 1) with a "Add products to your cart before checking out" message; cannot reach shipping |
| Resend | `notifications/list` → **Resend** on a `PENDING`/`FAILED` row | No exception; `attempt_count` increments |

> Regression note (v0.19.x): `Coupons` list broke with `Cannot format given Object as a Date` because `Instant` was fed to `<f:convertDateTime>`; fixed via `CouponManagementBean.formatUtc(...)`. If it 500s again, that helper or its EL usage is the culprit.

---

## Reading failures

| Class | Signal | First move |
|-------|--------|------------|
| **Logic** | `AssertionError`, wrong total/status | Fix domain/app or wrong expectation |
| **Persistence** | constraint, empty after save | Flyway, `inTx`, `em.clear`, `@Version` |
| **ArchUnit** | rule failed | Restore boundary — do not weaken |
| **CDI / WAR** | unsatisfied bean, missing entity | Producer/qualifier · `persistence.xml` |
| **Faces unit** | NPE session/context | Mock `SessionPort` / Faces as existing tests |
| **Flaky / env** | Docker, port, timeout | Re-run; fix setup — don’t `@Disabled` |
| **Compile** | symbol / module | POM dependency or move DTO |

**Priority when many fail:** compile/ArchUnit → shared-kernel/Money → touched module ITs → app units → checkout+coupon+cart regressions → unrelated modules.

---

## Analyzer reply format

```text
## Summary
Module / test / class (Logic|Persistence|ArchUnit|CDI|Faces|Flaky|Compile)
Cause (one line)

## Fix plan
1. …
2. …

## Verify
mvn -pl <module> test
# then broader package if needed
```

---

## Do not

- Skip or delete tests to green the build
- Broaden ArchUnit allow-lists
- Call another module’s `adapter` package from tests or production code
- Add multi-coupon stack or new libs without a story + human OK

---

## Done when

- [ ] Happy path + one rejection automated for the change
- [ ] IT covers persistence constraints/version when relevant
- [ ] Failure analysis names root cause and smallest fix
- [ ] Smoke steps clear for Liberty when UI is involved
```
