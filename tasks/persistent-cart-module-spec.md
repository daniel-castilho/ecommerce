### 2. `tasks/persistent-cart-module-spec.md`

````markdown
# Persistent Cart — Technical Specification

**Status:** Draft for implementation (not yet built)
**Module:** `order-checkout` · **Package:** `com.loja.ordercheckout`
**Companions:** `persistent-cart-backlog.md` · `persistent-cart-implementation-sequence.md`

---

## 1. Purpose & scope

Durable shopping cart for authenticated customers.

**In scope (MVP):**

- Aggregate `Cart` + `CartLine` (productId, quantity)
- Use cases: add, update quantity, remove line, get cart, clear cart
- Persistence: one cart per `userId`
- UI: `cart.xhtml`; “Add to cart” on product-detail
- Checkout loads lines from cart for the current user
- Clear cart after successful order placement
- Live price/name from `product-catalog` ports (no frozen unit price on the line)

**Out of scope:**

- Guest / anonymous cart (session or cookie token)
- Merge guest → login
- Stock reservation at add-to-cart
- “Save for later” / move between cart and wishlist
- Multi-cart / named carts
- Admin cart inspection UI

---

## 2. Current baseline (as-is)

`CheckoutBean` (`@ViewScoped`) holds `List<CartLine>` in memory:

- Seeded from `?productId=` or an empty row
- `placeOrder` maps lines → `ItemCheckoutRequest` → `CheckoutCommand`
- Lost on view end / session expiry / Liberty restart

Coupons already extend `CheckoutCommand` with optional `couponCode` and quote discount — **preserve that**.

---

## 3. Domain model

### Cart

| Field     | Notes                                             |
| --------- | ------------------------------------------------- |
| id        | UUID string                                       |
| userId    | Owner; **unique** one active cart per user in MVP |
| lines     | Collection of `CartLine`                          |
| version   | long — optimistic lock                            |
| updatedAt | Instant                                           |

### CartLine

| Field     | Notes          |
| --------- | -------------- |
| productId | Target product |
| quantity  | int ≥ 1        |

**Invariants:**

- At most one line per `productId` in a cart (add merges quantities or sets qty — **document the chosen rule**: prefer **add increments quantity**)
- Quantity ≥ 1; setting qty to 0 removes the line
- Only **ACTIVE** products may be added (via catalog port)
- Price is **not** part of the line

**Not** the same aggregate as `Order`. Order remains the committed purchase snapshot (including coupon discount fields already added in V23).

---

## 4. Ports

**Inbound (examples):**

- `AddToCartUseCase` — `(userId, productId, qty)`
- `UpdateCartLineUseCase` — `(userId, productId, qty)`
- `RemoveFromCartUseCase` — `(userId, productId)` idempotent
- `GetCartUseCase` — `(userId)` → cart + display DTOs
- `ClearCartUseCase` — `(userId)`

**Outbound:**

- `CartRepositoryPort` — findByUserId, save (with version), delete/clear lines
- Reuse `product-catalog` `ProductRepositoryPort` (or a thin lookup) for ACTIVE + name/price/image for UI

---

## 5. Persistence

Suggested Flyway **V24** (adjust if head moved):

```sql
tb_cart (
  id            VARCHAR(36) PK,
  user_id       VARCHAR(36) NOT NULL UNIQUE,
  version       BIGINT NOT NULL,
  updated_at    TIMESTAMP NOT NULL
)

tb_cart_line (
  cart_id       VARCHAR(36) NOT NULL REFERENCES tb_cart(id) ON DELETE CASCADE,
  product_id    VARCHAR(36) NOT NULL,
  quantity      INT NOT NULL CHECK (quantity >= 1),
  PRIMARY KEY (cart_id, product_id)
)
```
````

- Map `@Version` on cart entity; round-trip in mapper (see `docs/lessons.md`)
- Register entities in WAR `persistence.xml`

---

## 6. Application flow

```text
AddToCart:
  require userId
  product must be ACTIVE
  load or create cart for user
  upsert line (increment qty on same productId)
  save with version

GetCart:
  load cart
  for each line resolve catalog snapshot (name, price, slug, image)
  missing/inactive product → show fallback or skip with message (choose one; prefer show row as unavailable)

Checkout:
  GetCart(userId) → items for CheckoutCommand
  existing CreateOrderFromCart + coupon path unchanged
  on success → ClearCart(userId)

CheckoutBean:
  stop owning the source of truth for lines
  init review from GetCart when logged in
  optional: keep wizard-only fields (address, payment, coupon) in view scope
```

---

## 7. Web / UI

| Surface         | Behaviour                                                                     |
| --------------- | ----------------------------------------------------------------------------- |
| Product detail  | “Add to cart” (auth); guest → login link                                      |
| `cart.xhtml`    | List lines, change qty, remove, subtotal (live prices), “Proceed to checkout” |
| Checkout review | Lines from cart; coupon field unchanged                                       |
| Nav             | Link to cart for logged-in users                                              |

Design-system tokens only. Thin beans.

---

## 8. Concurrency & stock

- Optimistic lock on `Cart.version` — conflict → message “Cart was updated, try again”
- **No** inventory reservation on add
- At checkout, existing stock/reservation behaviour remains

---

## 9. Testing

- Domain: merge qty, remove, invariants
- Application: mocked ports; clear after “order success” path unit-tested
- Adapter ITs: unique user cart, line upsert, version conflict if feasible
- Checkout integration: place order reads cart and clears it
- Regression: coupon quote still applied when code present

---

## 10. Definition of Done

- [ ] MVP flows on Open Liberty
- [ ] Cart survives server restart
- [ ] Checkout without cart items fails cleanly (empty cart)
- [ ] ArchUnit + tests green; full package succeeds

```

```
