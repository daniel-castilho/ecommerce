### 3. `tasks/persistent-cart-backlog.md`

```markdown
# Persistent Cart — Backlog

**Companions:** `persistent-cart-module-spec.md` · `persistent-cart-implementation-sequence.md`
**Epic goal:** Replace view-scoped checkout lines with a durable per-user cart integrated into place-order.

**MVP:** S1–S10 · **Optional:** S11–S12

---

## Story map
```

FOUNDATION
S1 Domain Cart + CartLine + exceptions
S2 Ports + DTOs
S3 CartApplicationService
S4 Persistence + Flyway (tb_cart / tb_cart_line)

CUSTOMER FLOW
S5 Add / update / remove
S6 GetCart with live catalog snapshots
S7 cart.xhtml + product-detail Add to cart

CHECKOUT
S8 Checkout loads cart; clear after successful order
S9 Preserve coupon field + totals

QUALITY
S10 ArchUnit + tests + smoke

OPTIONAL
S11 Catalog-card “Add to cart”
S12 Guest cart + merge on login

```

---

## Stories

| ID | Story | Priority | Notes |
|----|--------|----------|--------|
| S1 | Cart domain model | Must | One line per product; qty ≥ 1; @Version on cart |
| S2 | Ports + DTOs | Must | No price on line DTO persistence |
| S3 | Cart application service | Must | ACTIVE check; upsert qty |
| S4 | JPA + Flyway | Must | user_id unique; cascade lines |
| S5 | Mutating use cases | Must | add / update / remove idempotent where specified |
| S6 | GetCart + display DTO | Must | Live price from catalog |
| S7 | Cart UI + add on detail | Must | Auth required for mutations |
| S8 | Checkout integration | Must | Command items from cart; clear on success |
| S9 | Coupon regression | Must | Existing promotions path still works |
| S10 | Tests + ArchUnit + WAR | Must | |
| S11 | Add to cart on catalog card | Should | |
| S12 | Guest cart + merge | Won’t for MVP | Explicit debt |

---

## Definition of Done (epic)

- [x] S1–S10 done
- [x] Smoke: login → add from detail → see cart → change qty → checkout → order OK → cart empty → restart Liberty → still empty (and prior to order, restart kept items)
- [x] `mvn clean package -pl web -am` succeeds

---

## Status (v0.16.0)

- S1–S10 shipped (2026-08-09). S11 (catalog-card "Add to cart") and S12 (guest cart +
  merge on login) remain explicit debt.

*Planning backlog. Update status as stories ship; full history in git after implementation.*
```
