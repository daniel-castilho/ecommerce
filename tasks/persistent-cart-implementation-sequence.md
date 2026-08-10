### 4. `tasks/persistent-cart-implementation-sequence.md`

```markdown
# Persistent Cart — Implementation Sequence

**Companions:** `persistent-cart-module-spec.md` · `persistent-cart-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. No guest cart in MVP.

---

## Step 0 — Locate & plan

1. Confirm Flyway head (expect ≥ V23 with coupons); next script e.g. `V24__cart.sql`
2. Read `CheckoutBean`, `CheckoutCommand`, `CreateOrderFromCartUseCase`, coupon integration
3. Decide package layout under `com.loja.ordercheckout` (`domain.model.Cart`, `port.in`, …)

**Done when:** written note of touch points; compile baseline green.

---

## Step 1 — Domain

`Cart`, `CartLine`, factory methods, merge-qty behaviour, domain exceptions.
Unit tests (no framework).

**Done when:** domain tests green.

---

## Step 2 — Ports + DTOs

Inbound cart use cases; `CartRepositoryPort`; display DTOs (`CartLineView` with live price fields filled by application, not persisted).

**Done when:** interfaces compile.

---

## Step 3 — Application service

Add / update / remove / get / clear. ACTIVE product guard. Unit tests with mocks.

**Done when:** service tests green.

---

## Step 4 — Persistence

Entities, mapper (`@Version` round-trip), adapter, Flyway, ITs (unique user, upsert line, delete).

**Done when:** ITs green.

---

## Step 5 — Web: cart page + add to cart

- `CartBean` + `cart.xhtml`
- Product-detail “Add to cart” (auth / login prompt)
- Register entities in `persistence.xml`
- Nav link when logged in

**Done when:** manual add/list/remove works on Liberty.

---

## Step 6 — Checkout integration

- `CheckoutBean` loads `GetCart` for logged-in user into review (remove reliance on ephemeral only lines)
- `placeOrder` builds `ItemCheckoutRequest` list from **persisted cart** (or re-read cart inside use case — prefer single source: use case loads cart by userId)
- On successful order → `ClearCart`
- Keep `couponCode` behaviour

**Preferred design:** `CreateOrderFromCart` loads cart by `userId` from command (items optional/ignored when cart is source of truth). If you keep items on the command, they must match the cart — simpler is **server loads cart**.

**Done when:** order unit/IT coverage; empty cart cannot place order.

---

## Step 7 — Hardening

- Optimistic lock message path
- Unavailable product display rule
- ArchUnit still green
- Full `mvn test` + `mvn clean package -pl web -am`

**Done when:** gate green.

---

## Step 8 — Release prep

README / CHANGELOG / optional `docs/releases/v0.X.0.md` when human requests tag.

---

## Smoke path

1. Login as customer
2. Product detail → Add to cart (qty 1)
3. Open cart → line visible with **current** catalog price
4. Increase qty → save
5. Restart Liberty → cart still has the line
6. Admin changes product price → reload cart → **new price** shown
7. Proceed to checkout → review matches cart; optional coupon still works
8. Place order → success → cart empty
9. Guest sees login prompt on add-to-cart (no silent guest cart)

---
```
