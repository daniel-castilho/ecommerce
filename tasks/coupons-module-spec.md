### 2. `tasks/coupons-module-spec.md`

```markdown
# Coupons / Promotions — Technical Specification

**Status:** Draft for implementation (not yet built)
**Module:** `promotions` · **Package:** `com.loja.promotions`
**Companions:** `coupons-backlog.md` · `coupons-implementation-sequence.md`

---

## 1. Purpose & scope

Promotional codes that reduce the merchandise subtotal at checkout.

**In scope (MVP):**

- Coupon aggregate: code, type (`PERCENT` | `FIXED`), value, active flag, optional validity window, optional max total uses
- Admin CRUD: create, list, filter, activate / deactivate (no hard-delete in MVP)
- Checkout: optional coupon code on `CheckoutCommand`
- Validation + application inside place-order flow
- Order snapshot: `coupon_code`, `discount_amount`
- Entire-order scope only (discount on sum of line totals)

**Out of scope (explicit debt):**

- Category- or product-scoped eligibility (optional S-later)
- Stacking multiple coupons
- Free-shipping coupons
- Auto-apply / silent promotions without a code
- Guest-only or first-order-only rules (can add later via flags)
- Refund recalculation complexity beyond storing the original discount snapshot

---

## 2. Architecture
```

promotions/
├── domain/
│ ├── model/ Coupon, CouponType, Money rules via shared Money where useful
│ ├── port/in/ CreateCoupon, ListCoupons, SetCouponActive, …
│ ├── port/out/ CouponRepositoryPort
│ └── exception/
├── application/service + dto
└── adapter/
├── in/web/ (optional thin beans; admin may live under admin-dashboard composing ports)
└── out/persistence/

order-checkout/
→ depends on promotions **inbound validation port** (or a narrow ApplyCouponPort)
→ extends Order + CheckoutCommand + checkout UI

admin-dashboard/
→ composes promotions use cases only

````

**Module choice:** own `promotions` JAR (like `wishlist`), not buried only inside `order-checkout`, so admin and checkout share one domain without circular rules.

`order-checkout` must **not** import promotions adapters — only ports.

---

## 3. Domain model

### Coupon

| Field | Notes |
|-------|--------|
| id | UUID string |
| code | Normalized uppercase; unique; e.g. `SAVE10` |
| type | `PERCENT` or `FIXED` |
| value | Percent 1–100 or fixed amount > 0 (`Money` / BigDecimal per project convention) |
| active | boolean |
| validFrom / validTo | Optional `Instant`; null = open-ended |
| maxTotalUses | Optional int ≥ 1; null = unlimited |
| usedCount | Incremented on successful order placement |
| createdAt | Instant |

**Invariants:**

- Code non-blank, unique (DB unique index)
- `PERCENT`: value in `(0, 100]`; `FIXED`: value > 0
- `validFrom` ≤ `validTo` when both set
- Inactive or outside window → not applicable
- When `maxTotalUses` set: `usedCount < maxTotalUses`

### Discount calculation (pure)

```text
merchandiseSubtotal = sum(line totals)
if PERCENT:  raw = subtotal * (value / 100)
if FIXED:    raw = min(value, subtotal)
discount = max(0, raw)
payableMerchandise = subtotal − discount
orderTotal = payableMerchandise + shipping
````

Never discount shipping in MVP. Never let total go negative.

### Order snapshot (order-checkout change)

Add to `Order` (and table `tb_order`):

- `couponCode` — nullable String
- `discountAmount` — `Money` / amount, default zero

`getTotal()` becomes: line subtotal − discount + shipping.

---

## 4. Ports

### promotions (inbound examples)

- `CreateCouponUseCase`
- `ListCouponsUseCase` (filter: code fragment, active)
- `SetCouponActiveUseCase`
- `GetCouponByCodeUseCase` (admin detail optional)

### promotions (outbound)

- `CouponRepositoryPort` — save, findByCode, findById, search, incrementUsedCount (atomic)

### Bridge used by order-checkout

Prefer a dedicated application API on promotions, e.g.:

```text
ValidateAndQuoteDiscountUseCase
  quote(code, merchandiseSubtotal) → DiscountQuote(code, discountAmount)
  // does not increment usage

RecordCouponRedemptionUseCase
  redeem(code)  // increment usedCount; call only after order successfully persisted
```

Or a single transactional port invoked only from `CreateOrderFromCart` inside the same JTA transaction.

**Do not** increment usage if payment/order creation fails.

---

## 5. Persistence

Suggested Flyway **V22** (adjust if head moved):

```sql
tb_coupon (
  id, code UNIQUE, type, value, active,
  valid_from, valid_to, max_total_uses, used_count,
  created_at
)

-- order-checkout migration (same or V23):
ALTER TABLE tb_order ADD coupon_code VARCHAR(...);
ALTER TABLE tb_order ADD discount_amount NUMERIC(...);  -- align with Money storage
```

No FK from coupon to product/category in MVP.

Register entities in WAR `persistence.xml`.

---

## 6. Checkout integration

1. Extend `CheckoutCommand` with `String couponCode` (nullable/blank = none).
2. In `CreateOrderFromCart` / order application service, after line prices resolved:
   - if code present → validate + quote discount
   - build `Order` with discount snapshot
   - on success → redeem (increment uses)
3. `CheckoutBean`: input field on payment or review step; show discount line in totals; map errors to FacesMessage.

Invalid code → fail checkout with message (do not place order).

---

## 7. Admin UI

Under `/admin-dashboard/` (RBAC already gates admin):

- List coupons (code, type, value, active, used/max, validity)
- Create form
- Activate / deactivate (confirm modal pattern)

Composition only: beans call promotions use cases.

---

## 8. Security

- Admin mutations: `@RolesAllowed("ADMIN")` + existing web.xml patterns
- Redeem path: only via server-side checkout with authenticated user (existing login requirement on place order)
- Code is not a secret capability beyond “who knows the code”; rate-limiting is out of scope for the lab

---

## 9. Testing

- Domain: percent/fixed caps, validity window, inactive, max uses
- Application: create/list/activate; quote; redeem
- Adapter ITs: unique code, increment uses
- Order-checkout unit tests: total with discount; reject invalid code
- ArchUnit on `promotions`
- Smoke: admin creates `SAVE10` → customer checkout with code → order total and snapshot correct

---

## 10. Definition of Done

- [ ] MVP flows work on Open Liberty
- [ ] Order total formula + snapshot persisted
- [ ] Usage increment only on successful order
- [ ] ArchUnit + tests green; full WAR package succeeds

```

```
