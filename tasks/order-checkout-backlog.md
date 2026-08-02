# Order & Checkout Module — Agile Backlog Refinement

**Companion to:** `order-checkout-module-spec.md` (technical design) and `order-checkout-implementation-sequence.md` (build order).

**Purpose of this document:** Break down the Order & Checkout module into independently valuable, shippable stories with Given/When/Then acceptance criteria, Definition of Ready, and Definition of Done — enabling incremental delivery and concurrent work if needed.

---

## Epic: Order Management & Checkout Workflow

**Epic goal:** Transform the e-commerce site from "catalog browser" to "fully functional online store" by adding complete order creation, payment processing, shipping integration, and customer notifications.

**Epic-level Definition of Done:** All stories below are done; customers can complete checkout end-to-end (add to cart → shipping → payment → confirmation); admins can view/cancel orders; payment audit trail is complete; no legacy DAO patterns remain in Order module; ArchUnit hexagonal-boundary test passes.

---

## Story Map (Dependency Order)

```
S1 Domain model ──▶ S2 JPA persistence adapter ──▶ S3 Repository port & search
                                                          │
S4 Payment gateway port + mocks ───────────────────────────┤
                                                          │
S5 Shipping rate port + Correios adapter ─────────────────┤
                                                          │
S6 Notification port + email adapter ──────────────────────┤
                                                          ▼
S7 Create order use case (orchestrates all above) ────── S8 Checkout UI (4-step form)
                                                          │
                                                          ▼
S9 Order history UI (view orders, cancel, track)

S10 Inventory integration (reserve/decrement stock)
S11 Idempotency & concurrency handling
S12 Architecture tests (ArchUnit)
```

Each story is sized to be completable and demoable independently, but dependencies (arrows) are hard constraints.

---

### S1 — Domain Model for Order

**As** the system, **I want** a framework-free `Order` domain object with a state machine and business invariants, **so that** all higher stories build on a domain that cannot represent invalid states.

**Priority:** Must (MoSCoW)

**Definition of Ready:**
- [ ] Spec §3 (domain model) reviewed and state transitions confirmed (PENDING → CONFIRMED → SHIPPED → DELIVERED, plus cancellation/refund paths)
- [ ] Payment provider decision made (Stripe or PagSeguro?) — affects PaymentInfo structure
- [ ] Confirmed: Money, ShippingAddress, PaymentInfo value objects either reuse from Catalog or are new domain classes

**Acceptance Criteria:**

- **Given** an Order in PENDING status with a valid authorization id, **when** `capture(paymentCapture)` is called, **then** status transitions to CONFIRMED and paymentInfo is updated with capture details.
- **Given** an Order in CONFIRMED status, **when** `canTransitionTo(SHIPPED)` is checked, **then** it returns true.
- **Given** an Order in DELIVERED status, **when** `canTransitionTo(CANCELLED)` is checked, **then** it returns false (terminal state).
- **Given** an Order in PENDING status, **when** `validateForCheckout()` is called with empty lines, **then** it throws InvalidOrderStateException with message "Cart is empty".
- **Given** an Order in CONFIRMED status, **when** `requestRefund(amount)` is called, **then** status transitions to REFUNDED and refund metadata is stored.
- **Given** an OrderLine with quantity=2 and unitPrice=$10, **when** `lineTotal()` is called, **then** it returns $20.

**Definition of Done:** 
- `OrderTest` covers all acceptance criteria above and passes with zero mocks
- State machine transitions verified via a transition matrix test
- All domain classes compile with zero framework imports
- Peer-reviewable diff limited to `order-domain` package

**Story Points:** 8

---

### S2 — JPA Persistence Adapter for Order

**As** the system, **I want** `OrderJpaEntity` + `OrderJpaMapper` + `OrderRepositoryJpaAdapter`, **so that** the domain `Order` can be persisted without the domain layer knowing JPA exists.

**Depends on:** S1

**Definition of Ready:**
- [ ] S1 merged
- [ ] Table schema finalized (§11 of spec) — confirm column names, types, nullability
- [ ] Flyway migration tool decision confirmed (assume it's already wired from Catalog module)

**Acceptance Criteria:**

- **Given** a valid `Order` domain object, **when** `OrderRepositoryJpaAdapter.save()` is called, **then** a row is persisted in `ORDER_ENTITY` table and the returned `Order` has a non-null id.
- **Given** a persisted order, **when** `findById()` is called, **then** all fields round-trip correctly (OrderLine list preserved, ShippingAddress embedded fields preserved, PaymentInfo details preserved).
- **Given** two orders with different status values (PENDING, CONFIRMED, SHIPPED), **when** `findByStatus(CONFIRMED)` is called, **then** only the CONFIRMED order is returned.
- **Given** an order with 3 line items with positions 0, 1, 2, **when** the order is loaded, **then** lines are sorted by position (not random order from DB).
- **Given** an order that already exists, **when** an update is made to the order (e.g., status change), **then** the DB row is updated (not inserted as duplicate).

**Definition of Done:** 
- `OrderJpaMapperTest` passes (domain ↔ JPA round-trip)
- `OrderRepositoryJpaAdapterTest` (Testcontainers, real DB) passes for CRUD + search cases
- Migration `V4__order_and_checkout_schema.sql` applied successfully against local DB snapshot
- No class outside `adapter.persistence` package imports `OrderJpaEntity`
- Column indexes verified (customer_id, status) are present

**Story Points:** 5

---

### S3 — Repository Port & Search for Order

**As an** admin, **I want** to query orders by customer, status, and date range with pagination, **so that** I can manage fulfillment efficiently.

**Depends on:** S2

**Definition of Ready:**
- [ ] S2 merged
- [ ] Default/max page size confirmed (spec: 20/100, no open question here)

**Acceptance Criteria:**

- **Given** 50 orders in the DB, **when** `findByCustomerId(id, page=0, pageSize=20)` is called, **then** 20 results are returned with totalPages=3.
- **Given** a mix of orders in PENDING, CONFIRMED, SHIPPED, DELIVERED status, **when** `findByStatus(SHIPPED)` is called, **then** only SHIPPED orders are returned (not others).
- **Given** 25 PENDING orders, **when** `findByStatus(PENDING)` is called with default sort, **then** results are sorted by created_at DESC (newest first).
- **Given** an order created 6 months ago and another created today, **when** both are queried, **then** the search correctly filters by date range if a date-range parameter is provided (future enhancement hint).

**Definition of Done:** 
- `OrderRepositoryJpaAdapterTest` covers all search/filter combinations
- Query verified to use indexes (check `EXPLAIN PLAN` in PR description)
- No N+1 on line items (JOIN FETCH or @EntityGraph used)

**Story Points:** 3

---

### S4 — Payment Gateway Port + Mock Adapter

**As** the system, **I want** a PaymentGatewayPort interface and a mock adapter for local development, **so that** checkout can be tested without hitting a real payment processor.

**Depends on:** S1 (domain must define PaymentAuthorization, PaymentCapture, PaymentRefund records)

**Definition of Ready:**
- [ ] Payment provider confirmed (Stripe or PagSeguro) — implementation will be adapter-specific
- [ ] Tokenization strategy confirmed (Stripe Elements client-side token, or PagSeguro form)

**Acceptance Criteria:**

- **Given** an Order and a PaymentMethod (tokenized card), **when** `authorize(order, method)` is called via mock adapter, **then** it returns a PaymentAuthorization with a non-empty authorizationId.
- **Given** a PaymentAuthorization from authorize(), **when** `capture(authId)` is called, **then** it returns a PaymentCapture with capturedAmount = Order.total.
- **Given** a PaymentCapture from capture(), **when** `refund(captureId, amount)` is called, **then** it returns a PaymentRefund with refundedAmount = requested amount.
- **Given** the mock adapter configured with a "fail_mode" flag, **when** authorize() is called, **then** it throws PaymentFailedException with a user-friendly message (not a stack trace).
- **Given** a successful authorize/capture/refund sequence, **when** the calls are logged, **then** each appears in an audit trail (for dev debugging).

**Definition of Done:** 
- `PaymentGatewayMockAdapterTest` passes all scenarios (success, failure modes, edge cases)
- Mock adapter is marked clearly as "FOR LOCAL DEV ONLY" in class-level comment
- Port interface has comprehensive Javadoc on each method

**Story Points:** 3

---

### S5 — Shipping Rate Port + Correios Adapter

**As** the system, **I want** a ShippingRatePort interface and a Correios adapter (plus mock for local dev), **so that** checkout can quote shipping costs and generate labels.

**Depends on:** S1 (domain must define ShippingAddress, ShippingOption)

**Definition of Ready:**
- [ ] Correios API access confirmed (SIGEPWEB credentials, if using real API, or decided to mock entirely for now)
- [ ] Shipping methods decided (PAC, SEDEX, or others)

**Acceptance Criteria:**

- **Given** a ShippingAddress in São Paulo, **when** `getQuotes(address)` is called via mock adapter, **then** it returns a list with at least 2 ShippingOption objects (PAC and SEDEX).
- **Given** a ShippingOption for PAC, **when** `createLabel(order, option)` is called, **then** it returns a ShippingLabel with a non-empty trackingNumber (format matches carrier, e.g., starts with "AA" for Correios).
- **Given** mock adapter, **when** multiple calls to getQuotes() are made for the same address, **then** the cost is consistent (not random).
- **Given** an invalid CEP in ShippingAddress, **when** getQuotes() is called via mock, **then** it still returns results (mock doesn't validate; real Correios adapter will).

**Definition of Done:** 
- `ShippingRateMockAdapterTest` passes
- If real Correios adapter is implemented: `ShippingRateCorreiosAdapterTest` with mocked HTTP (don't hit real API in CI)
- ShippingOption records are immutable and properly compared via equals()

**Story Points:** 3

---

### S6 — Notification Port + Email Adapter

**As** the system, **I want** a NotificationPort interface and an email adapter (plus mock), **so that** customers receive order confirmations, shipping updates, and refund notifications.

**Depends on:** S1 (domain must define Order, so notification can reference order details)

**Definition of Ready:**
- [ ] Email provider confirmed (SendGrid, internal SMTP, or mock-only for now)
- [ ] Email templates finalized (Order Confirmed, Order Shipped, Refund Processed templates)

**Acceptance Criteria:**

- **Given** an Order, **when** `notifyOrderConfirmed(order)` is called via mock adapter, **then** no exception is thrown and a confirmation entry is logged (for test verification).
- **Given** an Order with shippingTrackingNumber set, **when** `notifyOrderShipped(order, trackingNumber)` is called, **then** the notification includes the tracking number and carrier name.
- **Given** mock adapter, **when** `notifyRefundRequested(order, reason)` is called, **then** the notification is recorded with the reason included.
- **Given** real email adapter (if implemented), **when** `notifyOrderConfirmed(order)` is called, **then** an email is sent to customer email address (mocked SendGrid API call in test).

**Definition of Done:** 
- `NotificationMockAdapterTest` passes
- If real email adapter implemented: templates are externalized (not hardcoded Java strings), Javadoc confirms template file location
- All notifications include customer email address and order ID

**Story Points:** 3

---

### S7 — Create Order Use Case (Orchestration)

**As** a customer at checkout, **I want** to create an order from my cart with a single "Place Order" click, **so that** the order is reserved, payment is processed, and inventory is updated atomically.

**Depends on:** S2, S4, S5, S6 (all adapters must exist for orchestration to call them)

**Definition of Ready:**
- [ ] All adapters (payment, shipping, notification) are implemented and testable via mock
- [ ] Inventory integration decided: does Catalog module expose an InventoryReservationPort? Confirm how to call it.

**Acceptance Criteria:**

- **Given** a cart with valid items, shipping address, and shipping method, **when** checkout form is submitted with a valid (mock) payment token, **then** an Order is created with status CONFIRMED and persisted.
- **Given** a successful order creation, **when** the use case completes, **then** the customer receives an email notification (via mock).
- **Given** payment authorization succeeds but capture fails, **when** the use case completes, **then** the order is still created but status remains PENDING (not CONFIRMED), and no notification is sent.
- **Given** inventory reservation fails (insufficient stock), **when** the use case is called, **then** it throws InsufficientInventoryException before payment is attempted.
- **Given** a successful order, **when** inventory is checked, **then** stock for each line item has been decremented by the order quantity.
- **Given** an identical cart + payment submitted twice (duplicate submit), **when** both requests hit the use case, **then** only one order is created (idempotency via request ID or session token).

**Definition of Done:** 
- `OrderApplicationServiceTest` passes all scenarios (success path, each failure point, idempotency)
- E2E integration test (with real Order persistence + mocked adapters) confirms full workflow
- Transaction boundaries verified (atomicity: all-or-nothing on order creation + inventory decrement)

**Story Points:** 8

---

### S8 — Checkout UI (Multi-Step Form)

**As a** customer, **I want** a guided 4-step checkout form (review → shipping → payment → confirm), **so that** the process is clear and I can review before finalizing.

**Depends on:** S7 (use case must be implemented for bean to call)

**Definition of Ready:**
- [ ] Stripe/PagSeguro choice confirmed (affects payment form implementation: Elements iframe vs. PagSeguro form)
- [ ] Multi-step conversation pattern familiar (JSF @ViewScoped bean state management)

**Acceptance Criteria:**

- **Given** the checkout page loads, **when** the customer reviews their cart, **then** the page shows all items, quantities, prices, and total.
- **Given** the customer enters a CEP on the shipping step, **when** an autocomplete lookup is triggered, **then** address fields are populated (or shipping options are shown based on CEP).
- **Given** the customer selects a shipping method, **when** they click "Next", **then** shipping cost is calculated and added to order total.
- **Given** the payment step, **when** the customer enters card details in the Stripe Elements iframe, **then** a Stripe token is generated client-side (raw card data never touches our server).
- **Given** all information is correct on the confirm step, **when** the customer clicks "Place Order", **then** the order is created (calls S7 use case) and they are redirected to a success page.
- **Given** payment fails, **when** an error message is displayed, **then** the customer is returned to the payment step with the error message (not stuck or redirected away).

**Definition of Done:** 
- Manual QA pass: complete checkout flow with mock payment (in browser)
- Form validation: all required fields show error messages if left blank
- Error handling: no unhandled exceptions; all domain/application exceptions translated to user-friendly messages
- Accessibility: form inputs have labels, buttons are keyboard-accessible

**Story Points:** 8

---

### S9 — Order History UI

**As a** customer, **I want** to see my past orders, their status, and tracking information, **so that** I can track shipments and request refunds if needed.

**Depends on:** S2, S3 (repository must support queries by customer)

**Definition of Ready:**
- [ ] Refund workflow confirmed: is it auto-approved or manual approval by admin?

**Acceptance Criteria:**

- **Given** a customer with 5 past orders, **when** they view their order history, **then** all 5 orders are displayed in a table (sorted newest first).
- **Given** an order with status SHIPPED, **when** the order row is clicked, **then** order details and carrier tracking number are shown (hyperlinked to carrier's tracking page).
- **Given** an order with status PENDING or CONFIRMED (not yet shipped), **when** the customer views it, **then** an "Cancel Order" button is visible.
- **Given** an order with status CONFIRMED, PROCESSING, or SHIPPED, **when** the customer clicks "Request Refund", **then** a dialog appears asking for a reason, and the refund request is submitted.
- **Given** a refund request is submitted, **when** the order status is re-loaded, **then** it shows "Refund Requested" (or similar indicator).

**Definition of Done:** 
- Manual QA pass: customer logs in, sees their orders, can click through to details and request refund
- Pagination tested: > 20 orders show as multiple pages

**Story Points:** 5

---

### S10 — Inventory Integration (Stock Decrement)

**As** the system, **I want** to coordinate with the Catalog module to reserve inventory at checkout and decrement it on order confirmation, **so that** overselling is prevented.

**Depends on:** S7 (orchestration must call InventoryReservationPort)

**Definition of Ready:**
- [ ] Catalog module's InventoryReservationPort reviewed — confirm its interface and error handling
- [ ] Reservation TTL decided (if checkout is abandoned, when does reservation expire? 15 min? 1 hour?)

**Acceptance Criteria:**

- **Given** a product with 10 units in stock, **when** a checkout reserves 5 units, **then** the reservation succeeds and stock-available becomes 5 (for other browsers).
- **Given** an active reservation, **when** another customer tries to checkout with the same product, **then** they see only the remaining 5 units available (reserved units hidden).
- **Given** a checkout is abandoned (user closes browser), **when** the reservation TTL expires, **then** the reserved units are released back to available stock. — *delivered two ways: lazily on the next `reserve` of the same product, and proactively by the scheduled sweep `InventoryReservationExpiryService`/`ReservationExpiryScheduler` (60s interval) added 2026-08-01.*
- **Given** a successful order confirmation, **when** the inventory decrement is applied, **then** reserved units become permanently decremented (not just reserved).
- **Given** two simultaneous checkouts for the last 2 units of a product, **when** both try to confirm, **then** one succeeds and one fails with "Insufficient inventory" (no overselling).

**Definition of Done:** 
- Concurrent checkout test (2 simultaneous threads/futures) ensures no race condition
- Integration test confirms Catalog InventoryReservationPort is called correctly
- Stock levels audited after checkout (SQL query verifies counts)

**Story Points:** 5

---

### S11 — Idempotency & Concurrency Handling

**As** the system, **I want** to guarantee that duplicate checkout submissions create only one order and that concurrent checkouts don't corrupt state, **so that** customers don't accidentally create multiple orders or see inconsistent stock levels.

**Depends on:** S7, S10

**Definition of Ready:**
- [ ] Idempotency strategy confirmed: use request ID (client-generated UUID) or session-based deduplication?

**Acceptance Criteria:**

- **Given** a checkout form is submitted and the order is created successfully, **when** the form is submitted again with the same data (e.g., F5 browser refresh), **then** the same order is returned (not a duplicate order created).
- **Given** two concurrent checkout requests for the same cart, **when** both hit the service simultaneously, **then** one succeeds and one fails with a clear error message (no race condition, no data corruption).
- **Given** an order in PENDING state and a concurrent attempt to cancel it, **when** both operations race, **then** either the order is created with CONFIRMED status (cancel loses race), or no order is created (cancel wins race) — but not both.
- **Given** a successful order confirmation, **when** a concurrent inventory decrement attempt is made on the same order, **then** the operation fails with "Order already confirmed" (not a duplicate decrement).

**Definition of Done:** 
- Idempotency test: submit checkout form twice, verify only one Order.id is created
- Concurrency test (Testcontainers + multiple threads): concurrent checkouts don't corrupt state
- Database constraints (unique order number, if used) prevent duplicates at DB level as well

**Story Points:** 5

---

### S12 — Architecture Tests (ArchUnit for Order Module)

**As** the team, **I want** automated boundary enforcement on the Order module (like Catalog has), **so that** architectural rules don't erode over time as new code is added.

**Depends on:** All other stories (tests need actual classes to verify against)

**Definition of Ready:**
- [ ] ArchUnit dependency already in pom.xml (from Catalog module setup)

**Acceptance Criteria:**

- **Given** the HexagonalArchitectureTest suite runs, **when** all tests pass, **then** the Order module conforms to hexagonal rules (domain doesn't import framework, application doesn't import adapters).
- **Given** new code is added to the domain layer with a `jakarta.persistence.*` import, **when** the test suite runs, **then** it fails with a clear message pointing to the violation.
- **Given** all six tests in the suite (domain purity, application isolation, adapter independence, port location, JPA entity location, state machine validation), **when** they all pass, **then** the Order module is production-ready from an architecture perspective.

**Definition of Done:** 
- 6 ArchUnit tests written (domain/application/adapter boundary rules)
- Tests integrated into CI/CD pipeline (run on every build)
- All tests pass before PR merge

**Story Points:** 3

---

## Backlog Summary Table

| # | Story | Depends on | Priority | Points |
|---|---|---|---|---|
| S1 | Domain model | — | Must | 8 |
| S2 | JPA persistence adapter | S1 | Must | 5 |
| S3 | Repository search | S2 | Must | 3 |
| S4 | Payment gateway port + mock | S1 | Must | 3 |
| S5 | Shipping rate port + Correios | S1 | Must | 3 |
| S6 | Notification port + email | S1 | Must | 3 |
| S7 | Create order use case | S2, S4, S5, S6 | Must | 8 |
| S8 | Checkout UI | S7 | Must | 8 |
| S9 | Order history UI | S2, S3 | Should | 5 |
| S10 | Inventory integration | S7 | Must | 5 |
| S11 | Idempotency & concurrency | S7, S10 | Must | 5 |
| S12 | Architecture tests (ArchUnit) | All | Should | 3 |

**Total:** 61 points. For a solo developer, this is a 4–5 week epic (assuming ~12–15 points per week velocity).

**Sequencing:** S1–S6 as "foundation" (34 points, 2–3 weeks) can ship as an internal milestone. S7–S8 make it user-facing (16 points, 1–2 weeks). S9–S12 are "completion" (11 points, 1 week).

**Note on "Should" items (S9, S12):** Neither blocks the core epic value (customers can checkout), but both are strongly recommended before shipping: S9 for post-purchase tracking, S12 for long-term architecture maintainability.
