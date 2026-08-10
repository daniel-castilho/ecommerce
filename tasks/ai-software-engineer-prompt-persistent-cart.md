### 1. `tasks/ai-software-engineer-prompt-persistent-cart.md`

```markdown
# AI Software Engineer Prompt — Persistent Cart

**Status:** Not implemented — green-field epic.
**Home module:** `order-checkout` (package `com.loja.ordercheckout` — cart is part of the buy bounded context)
**Touches:** `order-checkout` domain/application/adapters, `web` (cart page + product-detail “Add to cart”), optionally catalog cards

You replace the ephemeral view-scoped cart lines in `CheckoutBean` with a **persisted Cart aggregate** per authenticated user.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/lessons.md` · `docs/java-jakarta-ee-coding-standards.md` · `docs/design-system.md`
3. `tasks/persistent-cart-module-spec.md`
4. `tasks/persistent-cart-backlog.md`
5. `tasks/persistent-cart-implementation-sequence.md`
6. Reference: current `CheckoutBean` / `CreateOrderFromCartUseCase` / `CheckoutCommand`; patterns from `wishlist` (idempotent add, ports, ITs)

---

## Goal

Authenticated customers can **add / update / remove** products on a durable cart that survives Liberty restart and new browser sessions. Checkout **loads items from the cart** (not only `?productId=` / empty view lines). After a successful order, the cart is **cleared**.

MVP: **one active cart per userId**. No guest cart, no merge guest→login.

---

## Non-negotiable rules

- Cart lives in **`order-checkout`** (no new Maven module required unless the human asks)
- Hexagonal: domain model + ports; JPA only in `adapter/out`
- Domain free of frameworks; DTOs in `application/dto`
- Cross-module access only via ports (`product-catalog` for ACTIVE check + display price)
- Cart lines store **`productId` + `quantity` only** — **no price snapshot** (re-resolve price from catalog on read and at checkout)
- Do **not** reserve stock on add-to-cart (reservation stays at checkout)
- `@Version` on Cart for concurrent updates
- After successful `CreateOrderFromCart`, clear the user’s cart in the same flow
- Coupons (already shipped) keep working: checkout still passes optional `couponCode`
- English only; design-system tokens only
- No new Maven dependency without human approval
- ArchUnit remains green for order-checkout; unit + ITs for cart
- New entities → WAR `persistence.xml`
- Next Flyway after current head (today: after `V23`)
- Do not push unless the human asks

---

## Definition of Done (epic)

- [ ] Add / update qty / remove on persisted cart (authenticated)
- [ ] `cart.xhtml` lists lines with live catalog price; empty state
- [ ] Product-detail (and optionally catalog) “Add to cart”
- [ ] Checkout review loads from `GetCart` for the logged-in user
- [ ] Successful order clears cart
- [ ] Restart Liberty → cart still present for that user
- [ ] Tests + ArchUnit + `mvn clean package -pl web -am` succeed

Start at **Step 0** of `persistent-cart-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
