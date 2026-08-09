### 1. `tasks/ai-software-engineer-prompt-coupons.md`

```markdown
# AI Software Engineer Prompt — Coupons / Promotions

**Status:** Not implemented — green-field epic.
**Target module:** `promotions` (package `com.loja.promotions`)
**Touches:** `order-checkout` (apply at place-order), `admin-dashboard` (CRUD composition), `web` (checkout field + admin pages)

You implement discount coupons for this Jakarta EE 11 + Faces hexagonal monolith.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/lessons.md` · `docs/java-jakarta-ee-coding-standards.md` · `docs/design-system.md`
3. `tasks/coupons-module-spec.md` — what to build
4. `tasks/coupons-backlog.md` — stories
5. `tasks/coupons-implementation-sequence.md` — build order
6. Reference modules: `wishlist`, `product-reviews`, `order-checkout` (`CreateOrderFromCartUseCase`, `Order`, `CheckoutBean`)

---

## Goal

Admins define **coupon codes** (percent or fixed amount). Customers optionally enter a code at checkout. Valid coupons reduce the **merchandise subtotal** (not shipping) and the **discount is snapshotted on the Order**.

MVP is **entire-order** scope only. Category/product-scoped coupons are optional later stories.

---

## Non-negotiable rules

- New Maven module `promotions` with the same hexagonal layout as `wishlist`
- Domain free of frameworks; DTOs in `application/dto` (never nested in ports)
- Cross-module access **only** via ports
- `admin-dashboard` **composes** use cases — no coupon business rules there
- Order total formula becomes: `lines subtotal − discount + shipping` (discount ≥ 0, total never negative)
- Discount applied **only** on merchandise subtotal; shipping is never discounted in MVP
- Snapshot on `Order`: `couponCode` + `discountAmount` (historical truth)
- English only; design-system tokens only
- No new Maven dependency without human approval
- ArchUnit + unit tests + adapter ITs (Testcontainers)
- New JPA entities → register in WAR `persistence.xml`
- Next Flyway version after current head (today: after `V21`)
- Do not push unless the human asks

---

## Definition of Done (epic)

- [ ] Admin can create / list / activate-deactivate coupons
- [ ] Checkout accepts optional coupon code; invalid codes fail with a clear FacesMessage
- [ ] Valid coupon reduces order merchandise total; shipping unchanged
- [ ] Order stores coupon code + discount amount snapshot
- [ ] Usage counted (global and optional per-user limits when in scope)
- [ ] ArchUnit green; module tests green; `mvn clean package -pl web -am` succeeds
- [ ] Browser smoke on Open Liberty

Start at **Step 0** of `coupons-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
