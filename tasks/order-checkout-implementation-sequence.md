# Order & Checkout Module — Implementation Sequencing Appendix

**Companion to:** `order-checkout-module-spec.md` (what to build) and `order-checkout-backlog.md` (why, sliced into stories). This document is the **execution order** — read it before writing any code. It exists so the implementing agent never has to stop and ask "what do I do first" or produce a half-migrated, non-compiling intermediate state.

**Rule for the implementing agent:** work through the steps in order. Do not start step N+1 until step N's "Done when" checklist is fully satisfied. If a step's prerequisites (previous steps) aren't met, stop and report rather than improvising an out-of-order approach.

---

## Step 0 — Environment & Configuration Setup (do this first)

1. Confirm payment provider choice is made:
   - [ ] Stripe? PagSeguro? Or mock-only for now?
   - If Stripe: obtain test API keys (sk_test_..., pk_test_...)
   - If PagSeguro: obtain email + token credentials
   - If mock-only for now: no action needed, proceed with MockAdapter

2. Confirm shipping provider decision:
   - [ ] Correios only? Or multi-carrier planned?
   - If Correios real API: confirm SIGEPWEB credentials available
   - If mock: no credentials needed, MockAdapter returns fake rates

3. Confirm notification setup:
   - [ ] Email only via SendGrid? Internal SMTP? Or mock-only?
   - If SendGrid: obtain API key
   - If SMTP: confirm host/port/credentials available
   - If mock-only: no credentials needed, MockAdapter logs to console

4. Confirm Inventory integration:
   - [ ] Does Catalog module already expose `InventoryReservationPort`?
   - If yes: get the port's package path and interface signature
   - If no: create the port in Catalog module first (separate work, or mock it in Order tests)

5. Add payment provider SDKs to `catalog-adapters/pom.xml`:
   ```xml
   <!-- If using Stripe -->
   <dependency>
     <groupId>com.stripe</groupId>
     <artifactId>stripe-java</artifactId>
     <version>24.x.x</version>
   </dependency>
   
   <!-- If using PagSeguro (example, verify current version) -->
   <dependency>
     <groupId>br.com.pagseguro</groupId>
     <artifactId>pagseguro-api</artifactId>
     <version>4.x.x</version>
   </dependency>
   ```

**Done when:**
- [ ] Payment provider keys are available (in test .env or mocked)
- [ ] Shipping provider access confirmed (or mock strategy confirmed)
- [ ] Notification provider keys available (or mock confirmed)
- [ ] SDKs added to pom.xml and `mvn dependency:resolve` succeeds

---

## Step 1 — Create Order Domain Module (story S1)

Corresponds to backlog story **S1 — Domain Model for Order**.

1. Create a new Maven module `order-domain` (parallel to `catalog-domain` if it exists, otherwise parallel to `catalog-core`):
   ```bash
   mkdir -p order-domain/src/main/java/.../order/domain
   mkdir -p order-domain/src/main/java/.../order/application
   mkdir -p order-domain/src/test/java/.../order/domain
   ```

2. Create value objects in `order-domain/src/main/java/.../order/domain/`:
   - `OrderId.java` — strongly-typed Long wrapper (immutable)
   - `ShippingAddress.java` — immutable value object with field validation
   - `PaymentInfo.java` — immutable value object (method, status, transaction IDs)
   - **Reuse from Catalog:** `Money.java` (already exists)

3. Create domain exceptions in `order-domain/src/main/java/.../order/domain/exception/`:
   - `OrderNotFoundException.java`
   - `InvalidOrderStateException.java`
   - `InsufficientInventoryException.java`
   - `PaymentFailedException.java`
   - `ShippingException.java`
   - `NotificationException.java`

4. Create `OrderStatus.java` enum with 7 states: PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED

5. Create domain classes:
   - `OrderLine.java` — immutable value object (productId, productName, unitPrice, quantity, position)
   - `Order.java` (aggregate root) with:
     - Fields: id, customerId, lines, shippingAddress, total, shippingCost, status, paymentInfo, trackingNumber, createdAt, updatedAt
     - Constructor (private, create via static factory method)
     - Business methods:
       - `static Order create(customerId, lines, address, shippingMethod)` — factory
       - `void validateForCheckout()` — throws if cart empty, address incomplete, etc.
       - `void authorize(PaymentAuthorization auth)` — store auth, don't change status
       - `void capture(PaymentCapture capture)` — transition PENDING → CONFIRMED
       - `void requestRefund(Money amount)` — transition to REFUNDED
       - `boolean canTransitionTo(OrderStatus target)` — state machine enforcement
       - `void setStatus(OrderStatus)` — private setter, called only by other methods
     - Getters for all fields (no setters except status via state machine methods)

6. Write `OrderTest.java` in `order-domain/src/test/java/` covering:
   - State machine transitions (valid + invalid paths)
   - `validateForCheckout()` success and all failure conditions
   - Line item calculations
   - Domain exceptions thrown correctly
   - No mocks used (pure unit tests)

7. **Verify no framework imports:** Run `grep -r "jakarta\|javax" order-domain/src/main/java` — should return nothing.

**Done when:**
- [ ] `mvn clean compile -pl order-domain` succeeds
- [ ] `mvn test -pl order-domain -Dtest=OrderTest` passes
- [ ] No framework imports in domain package
- [ ] All acceptance criteria from S1 backlog story are covered by tests

---

## Step 2 — Create Application Ports & DTOs (story S1 continued)

Still in `order-domain`, create the application layer interfaces and DTOs:

1. Create use-case interfaces in `order-domain/src/main/java/.../order/application/port/in/`:
   - `CreateOrderFromCartUseCase.java`
   - `CancelOrderUseCase.java`
   - `RequestRefundUseCase.java`
   - `GetOrderDetailsUseCase.java`
   - `ListOrdersForCustomerUseCase.java`

2. Create outbound port interfaces in `order-domain/src/main/java/.../order/application/port/out/`:
   - `OrderRepositoryPort.java` — persistence
   - `PaymentGatewayPort.java` — payment processing
   - `ShippingRatePort.java` — shipping rates & labels
   - `NotificationPort.java` — email/SMS
   - `InventoryReservationPort.java` — call into catalog module

3. Create DTOs in `order-domain/src/main/java/.../order/application/`:
   - `OrderSearchCriteria.java`
   - `PageResult.java`
   - `PaymentMethod.java` — input DTO (method type, token ID)
   - `PaymentAuthorization.java` — output from gateway
   - `PaymentCapture.java` — output from gateway
   - `PaymentRefund.java` — output from gateway
   - `ShippingOption.java` — output from shipping port
   - `ShippingLabel.java` — output from shipping port

**Verify:** No framework imports, no JPA, nothing that depends on adapters.

**Done when:**
- [ ] All interfaces compile and are documented with Javadoc
- [ ] All DTOs are immutable (final classes, private fields, public constructors/getters)
- [ ] Zero framework imports in application package
- [ ] `mvn clean compile -pl order-domain` succeeds

---

## Step 3 — JPA Persistence Adapter (story S2)

In `catalog-adapters/src/main/java/.../order/adapter/persistence/`:

1. Create `AuditableJpaEntity.java` if not already present (reuse from Catalog if it exists there):
   ```java
   @MappedSuperclass
   public abstract class AuditableJpaEntity {
       @Version
       private Long version;
       
       @Column(name = "CREATED_AT", nullable = false, updatable = false)
       private Instant createdAt;
       
       @Column(name = "UPDATED_AT", nullable = false)
       private Instant updatedAt;
       
       @PrePersist
       void onCreate() { createdAt = Instant.now(); updatedAt = Instant.now(); }
       
       @PreUpdate
       void onUpdate() { updatedAt = Instant.now(); }
   }
   ```

2. Create JPA entities in `catalog-adapters/.../order/adapter/persistence/entity/`:
   - `OrderJpaEntity.java` (extends AuditableJpaEntity)
     - `@Table(name = "ORDER_ENTITY")`
     - All columns from spec §3.6
     - `@OneToMany(mappedBy = "order", cascade = ALL, orphanRemoval = true)` for lines
     - `@Embedded` for ShippingAddress and PaymentInfo
   
   - `OrderLineJpaEntity.java`
     - `@Table(name = "ORDER_LINE_ENTITY")`
     - All columns from spec
   
   - `PaymentTransactionJpaEntity.java`
     - `@Table(name = "PAYMENT_TRANSACTION")`
     - Audit log for payment events

3. Create `@Embeddable` classes for complex value objects:
   - `ShippingAddressEmbeddable.java` (maps ShippingAddress value object)
   - `PaymentInfoEmbeddable.java` (maps PaymentInfo value object)

4. Create `OrderJpaMapper.java`:
   ```java
   public class OrderJpaMapper {
       public Order mapToDomain(OrderJpaEntity entity) { /* unwrap from JPA */ }
       public OrderJpaEntity mapToJpa(Order domain) { /* wrap to JPA */ }
   }
   ```
   - Maps value objects (OrderId, ShippingAddress, PaymentInfo) from JPA primitives

5. Create Flyway migration file `src/main/resources/db/migration/V4__order_and_checkout_schema.sql`:
   - CREATE TABLE ORDER_ENTITY (all columns, indexes)
   - CREATE TABLE ORDER_LINE_ENTITY (FK to ORDER_ENTITY CASCADE)
   - CREATE TABLE PAYMENT_TRANSACTION (audit log)
   - CREATE INDEX on customer_id, status (frequent queries)

6. Write `OrderJpaMapperTest.java`:
   - Domain ↔ JPA round-trip for all fields
   - Value object unwrapping/wrapping

7. Write `OrderRepositoryJpaAdapterTest.java` (Testcontainers):
   - CRUD operations
   - Search by customer, status
   - No N+1 on line items

**Done when:**
- [ ] `mvn clean compile -pl catalog-adapters` succeeds
- [ ] `mvn test -pl catalog-adapters -Dtest=OrderJpaMapperTest` passes
- [ ] `mvn test -pl catalog-adapters -Dtest=OrderRepositoryJpaAdapterTest` passes
- [ ] Migration file can be applied to a test database: `mvn flyway:migrate`
- [ ] No class outside `adapter.persistence` imports `OrderJpaEntity`

---

## Step 4 — Payment Gateway Adapter (story S4)

In `catalog-adapters/src/main/java/.../order/adapter/payment/`:

1. Move `PaymentGatewayPort.java` interface from `order-domain` to here (adapters/payment package)

2. Create `PaymentGatewayMockAdapter.java`:
   ```java
   @ApplicationScoped
   public class PaymentGatewayMockAdapter implements PaymentGatewayPort {
       @Override
       public PaymentAuthorization authorize(Order order, PaymentMethod method) {
           return new PaymentAuthorization("mock_auth_" + UUID.randomUUID(), order.total());
       }
       
       @Override
       public PaymentCapture capture(String authId) {
           return new PaymentCapture(authId, "mock_capture_" + UUID.randomUUID(), order.total());
       }
       
       @Override
       public PaymentRefund refund(String captureId, Money amount) {
           return new PaymentRefund(captureId, "mock_refund_" + UUID.randomUUID(), amount);
       }
   }
   ```

3. If using Stripe, create `PaymentGatewayStripeAdapter.java`:
   - Inject Stripe API key via config
   - Implement authorize() → call `Charge.create()` with "authorize_only"
   - Implement capture() → call `Charge.update()` to capture
   - Implement refund() → call `Refund.create()`
   - Translate Stripe exceptions to `PaymentFailedException`

4. If using PagSeguro, create `PaymentGatewayPagSeguroAdapter.java` (similar pattern)

5. Create config class `PaymentGatewayConfig.java` (CDI Producer):
   ```java
   @ApplicationScoped
   public class PaymentGatewayConfig {
       @Produces
       @ApplicationScoped
       PaymentGatewayPort paymentGateway(@ConfigProperty(name = "payment.gateway.type") String type) {
           if ("stripe".equals(type)) {
               return new PaymentGatewayStripeAdapter(...);
           } else if ("pagseguro".equals(type)) {
               return new PaymentGatewayPagSeguroAdapter(...);
           } else {
               return new PaymentGatewayMockAdapter();
           }
       }
   }
   ```

6. Write `PaymentGatewayMockAdapterTest.java`:
   - Success path (authorize → capture → refund)
   - Failure modes (optional, if mock supports it)

7. If real adapter written: write `PaymentGatewayStripeAdapterTest.java` (mocked HTTP, don't hit real Stripe in CI)

**Done when:**
- [ ] `mvn clean compile -pl catalog-adapters` succeeds
- [ ] `mvn test -pl catalog-adapters -Dtest=PaymentGatewayMockAdapterTest` passes
- [ ] Config producer selects correct adapter based on property
- [ ] `payment.gateway.type` property defaults to "mock" for local dev

---

## Step 5 — Shipping Rate Adapter (story S5)

In `catalog-adapters/src/main/java/.../order/adapter/shipping/`:

1. Move `ShippingRatePort.java` interface from `order-domain` to here

2. Create `ShippingRateMockAdapter.java`:
   ```java
   @ApplicationScoped
   public class ShippingRateMockAdapter implements ShippingRatePort {
       @Override
       public List<ShippingOption> getQuotes(ShippingAddress address) {
           return List.of(
               new ShippingOption("pac", new Money(15.00), 15, "PAC - 15 business days"),
               new ShippingOption("sedex", new Money(30.00), 3, "SEDEX - 3 business days")
           );
       }
       
       @Override
       public ShippingLabel createLabel(Order order, ShippingOption selected) {
           return new ShippingLabel("AA" + System.nanoTime(), selected.method());
       }
   }
   ```

3. If integrating Correios, create `ShippingRateCorreiosAdapter.java`:
   - Call Correios SIGEPWEB API (XML-based) for rates
   - Call SIGEPWEB for shipping label generation
   - Translate Correios exceptions to `ShippingException`
   - Cache rates by CEP (they don't change hourly)

4. Create config class `ShippingRateConfig.java` (CDI Producer):
   ```java
   @Produces
   @ApplicationScoped
   ShippingRatePort shippingRate(@ConfigProperty(name = "shipping.provider") String provider) {
       if ("correios".equals(provider)) {
           return new ShippingRateCorreiosAdapter(...);
       } else {
           return new ShippingRateMockAdapter();
       }
   }
   ```

5. Write `ShippingRateMockAdapterTest.java`:
   - getQuotes() returns consistent results
   - createLabel() generates trackingNumber

6. If real Correios adapter: write test with mocked HTTP

**Done when:**
- [ ] `mvn clean compile -pl catalog-adapters` succeeds
- [ ] `mvn test -pl catalog-adapters -Dtest=ShippingRateMockAdapterTest` passes
- [ ] Config producer defaults to mock
- [ ] Trackingumber format matches carrier expectations

---

## Step 6 — Notification Adapter (story S6)

In `catalog-adapters/src/main/java/.../order/adapter/notification/`:

1. Move `NotificationPort.java` interface from `order-domain` to here

2. Create `NotificationMockAdapter.java`:
   ```java
   @ApplicationScoped
   public class NotificationMockAdapter implements NotificationPort {
       private static final Logger LOGGER = Logger.getLogger(NotificationMockAdapter.class.getName());
       
       @Override
       public void notifyOrderConfirmed(Order order) {
           LOGGER.info("MOCK: Order confirmed for customer " + order.customerId() + 
                      ", order id " + order.id());
       }
       
       @Override
       public void notifyOrderShipped(Order order, String trackingNumber) {
           LOGGER.info("MOCK: Order shipped with tracking " + trackingNumber);
       }
       
       @Override
       public void notifyRefundRequested(Order order, String reason) {
           LOGGER.info("MOCK: Refund requested: " + reason);
       }
   }
   ```

3. If using SendGrid, create `NotificationEmailAdapter.java`:
   - Inject SendGrid API key via config
   - Call `mail.send()` with template ID (Order Confirmed, Order Shipped, Refund Processed)
   - Translate SendGrid exceptions to `NotificationException`

4. If using SMTP, create `NotificationSmtpAdapter.java`:
   - Inject SMTP host/port/credentials via config
   - Send via Jakarta Mail API
   - Handle SMTP errors

5. Create config class `NotificationConfig.java`:
   ```java
   @Produces
   @ApplicationScoped
   NotificationPort notification(@ConfigProperty(name = "notification.provider") String provider) {
       if ("sendgrid".equals(provider)) {
           return new NotificationEmailAdapter(...);
       } else if ("smtp".equals(provider)) {
           return new NotificationSmtpAdapter(...);
       } else {
           return new NotificationMockAdapter();
       }
   }
   ```

6. Write `NotificationMockAdapterTest.java`:
   - No exception on any notification call
   - Calls are logged (for verification)

**Done when:**
- [ ] `mvn clean compile -pl catalog-adapters` succeeds
- [ ] `mvn test -pl catalog-adapters -Dtest=NotificationMockAdapterTest` passes
- [ ] Config producer defaults to mock for local dev

---

## Step 7 — Create OrderRepositoryJpaAdapter (story S2 continued)

In `catalog-adapters/src/main/java/.../order/adapter/persistence/`:

1. Implement `OrderRepositoryJpaAdapter.java`:
   ```java
   @ApplicationScoped
   public class OrderRepositoryJpaAdapter implements OrderRepositoryPort {
       @PersistenceContext
       private EntityManager em;
       
       @Inject
       private OrderJpaMapper mapper;
       
       @Override
       public Order save(Order domain) {
           OrderJpaEntity entity = mapper.mapToJpa(domain);
           if (entity.getId() == null) {
               em.persist(entity);
           } else {
               entity = em.merge(entity);
           }
           em.flush();
           return mapper.mapToDomain(entity);
       }
       
       @Override
       public Optional<Order> findById(Long id) {
           return Optional.ofNullable(em.find(OrderJpaEntity.class, id))
               .map(mapper::mapToDomain);
       }
       
       @Override
       public PageResult<Order> findByCustomerId(Long customerId, int page, int pageSize) {
           TypedQuery<OrderJpaEntity> query = em.createQuery(
               "SELECT o FROM OrderJpaEntity o WHERE o.customerId = :cid ORDER BY o.createdAt DESC",
               OrderJpaEntity.class
           );
           query.setParameter("cid", customerId);
           long total = em.createQuery(
               "SELECT COUNT(o) FROM OrderJpaEntity o WHERE o.customerId = :cid",
               Long.class
           ).setParameter("cid", customerId).getSingleResult();
           
           query.setFirstResult(page * pageSize);
           query.setMaxResults(pageSize);
           List<Order> items = query.getResultList().stream()
               .map(mapper::mapToDomain)
               .toList();
           
           return new PageResult<>(items, total, page, pageSize);
       }
       
       @Override
       public List<Order> findByStatus(OrderStatus status) {
           return em.createQuery(
               "SELECT o FROM OrderJpaEntity o WHERE o.status = :status ORDER BY o.createdAt DESC",
               OrderJpaEntity.class
           ).setParameter("status", status)
            .getResultList()
            .stream()
            .map(mapper::mapToDomain)
            .toList();
       }
   }
   ```

2. Test with `OrderRepositoryJpaAdapterTest.java` (Testcontainers)

**Done when:**
- [ ] All repository methods compile and pass tests
- [ ] Queries use indexes (verified via EXPLAIN PLAN comments in PR)

---

## Step 8 — Application Service (story S7)

In `order-domain/src/main/java/.../order/application/service/`:

1. Create `OrderApplicationService.java`:
   ```java
   @ApplicationScoped
   @Transactional
   public class OrderApplicationService implements CreateOrderFromCartUseCase, CancelOrderUseCase,
                                                    RequestRefundUseCase, GetOrderDetailsUseCase,
                                                    ListOrdersForCustomerUseCase {
       
       @Inject
       private OrderRepositoryPort orderRepository;
       
       @Inject
       private PaymentGatewayPort paymentGateway;
       
       @Inject
       private ShippingRatePort shippingRate;
       
       @Inject
       private NotificationPort notification;
       
       @Inject
       private InventoryReservationPort inventory;  // from Catalog
       
       @Override
       public Order createOrder(Long customerId, List<CartItem> cartItems, ShippingAddress address,
                               String shippingMethod, PaymentMethod paymentMethod) {
           // Impl per spec §5
       }
       
       @Override
       public void cancelOrder(Long orderId) {
           // Impl per spec §5
       }
       
       // ... other use cases
   }
   ```

2. Write comprehensive `OrderApplicationServiceTest.java`:
   - All use cases with mocked ports
   - Happy path + failure scenarios

**Done when:**
- [ ] All use cases implemented with correct orchestration logic
- [ ] `mvn test -pl order-domain -Dtest=OrderApplicationServiceTest` passes
- [ ] Transaction boundaries are correct (@Transactional on orchestration methods)

---

## Step 9 — Checkout UI (story S8)

In `catalog-web/servlet/src/main/java/.../web/jsf/beans/`:

1. Create `CheckoutBean.java`:
   - `@Named("checkoutBean")`
   - `@ViewScoped`
   - Inject `CreateOrderFromCartUseCase`
   - Implement 4-step form logic (per spec §9.1)

2. Extend `catalog-web/servlet/src/main/webapp/` with Facelets pages:
   - `checkout.xhtml` (4-step form)
   - `checkout-success.xhtml` (confirmation page)

3. **Payment form:** Use Stripe Elements iframe (client-side tokenization):
   - JavaScript snippet that calls `stripe.createToken()` on form submit
   - Token is POSTed to CheckoutBean
   - Bean calls `createOrderUseCase.createOrder(..., token)`

4. Write integration test: `CheckoutIntegrationTest.java`
   - Complete checkout flow with mocked services
   - Payment success/failure scenarios

**Done when:**
- [ ] `mvn clean compile -pl catalog-web` succeeds
- [ ] Manual QA: open checkout in browser, complete flow, see order confirmation
- [ ] No raw card data is ever logged/stored (only tokens)

---

## Step 10 — Order History UI (story S9)

In `catalog-web/servlet/src/main/java/.../web/jsf/beans/`:

1. Create `OrderHistoryBean.java`:
   - `@Named("orderHistoryBean")`
   - `@ViewScoped`
   - Inject `ListOrdersForCustomerUseCase`, `CancelOrderUseCase`, `RequestRefundUseCase`
   - Load orders on init
   - Implement pagination

2. Create `catalog-web/servlet/src/main/webapp/order-history.xhtml`:
   - Table of orders (status, date, total, actions)
   - Detail modal for individual order
   - "Cancel Order" button (if not already shipped)
   - "Request Refund" dialog

**Done when:**
- [ ] Customer can view all their orders
- [ ] Customer can click to details
- [ ] Customer can request cancellation/refund with reason

---

## Step 11 — Inventory Integration (story S10)

Integrate with Catalog module's `InventoryReservationPort`:

1. Confirm Catalog module exposes `InventoryReservationPort` in its `application.port.out` package
2. In `OrderApplicationService.createOrder()`:
   - Call `inventory.reserveInventory(order.lines())` BEFORE payment
   - If insufficient stock, throw `InsufficientInventoryException`
   - Call `inventory.decrementInventory(order.lines())` AFTER capture succeeds
   - On cancellation, call `inventory.releaseReservation(order.lines())`

3. Test concurrency: two simultaneous checkouts for the last item
   - One succeeds, one fails with insufficient inventory
   - No overselling

**Done when:**
- [ ] Inventory is reserved before payment (prevents overselling)
- [ ] Inventory is decremented after payment (committed state)
- [ ] Concurrent checkout test passes

---

## Step 12 — Idempotency & Concurrency (story S11)

1. Add **request ID** to Order:
   - Client generates UUID on checkout start
   - CheckoutBean includes `requestId` in `createOrder()` call
   - `OrderApplicationService` checks: "is there already an order with this requestId?"
   - If yes, return existing order (don't create duplicate)
   - If no, proceed

2. Add **optimistic locking** via `@Version` on OrderJpaEntity
   - Concurrent status updates fail with `OptimisticLockException`
   - Translate to user message: "Order was updated by another process, please reload"

3. Write concurrent tests:
   - `ConcurrentCheckoutTest.java` — two threads, same cart, verify one succeeds
   - `IdempotencyTest.java` — submit checkout twice, verify one order created

**Done when:**
- [ ] Idempotency test passes (duplicate submit returns same order)
- [ ] Concurrency test passes (two simultaneous checkouts handled correctly)
- [ ] `OptimisticLockException` translated to user message

---

## Step 13 — Architecture Tests (story S12)

In `catalog-adapters/src/test/java/.../order/`:

1. Create `OrderHexagonalArchitectureTest.java`:
   ```java
   public class OrderHexagonalArchitectureTest {
       @Test
       void domain_should_not_depend_on_framework() { /* assert no jakarta.* */ }
       
       @Test
       void domain_should_not_depend_on_adapters() { /* assert no adapter imports */ }
       
       @Test
       void application_should_only_depend_on_domain() { /* assert no adapter imports */ }
       
       @Test
       void payment_adapter_should_not_depend_on_shipping() { /* no cross-adapter deps */ }
       
       @Test
       void ports_should_reside_in_application() { /* assert *Port in .application.port */ }
       
       @Test
       void jpa_entities_should_reside_in_adapter() { /* assert @Entity in .adapter.persistence */ }
   }
   ```

2. Run tests in CI/CD as a gate before PR merge

**Done when:**
- [ ] All 6 ArchUnit tests pass
- [ ] No architectural violations detected
- [x] Tests integrated into CI pipeline (`.github/workflows/ci.yml`, added 2026-08-01)

---

## Full-Sequence Completion Checklist

- [ ] Step 0 — Environment & config (payment/shipping/notification keys available)
- [ ] Step 1 — Domain model + value objects, no framework imports
- [ ] Step 2 — Application ports & use-case interfaces
- [ ] Step 3 — JPA persistence adapter, migration applied
- [ ] Step 4 — Payment gateway port + mock adapter
- [ ] Step 5 — Shipping rate port + mock adapter
- [ ] Step 6 — Notification port + mock adapter
- [ ] Step 7 — OrderRepositoryJpaAdapter implemented
- [ ] Step 8 — OrderApplicationService orchestrates all use cases
- [ ] Step 9 — Checkout UI (4-step form, token-based payment)
- [ ] Step 10 — Order history UI (list, cancel, refund)
- [ ] Step 11 — Inventory integration (reserve/decrement/release)
- [ ] Step 12 — Idempotency & concurrency handling
- [ ] Step 13 — ArchUnit architecture tests passing

Only after every box above is checked is the epic (backlog document, "Epic-level Definition of Done") actually complete.

---

## Validation Commands to Run After Each Step

```bash
# After Step 1
mvn clean compile -pl order-domain
mvn test -pl order-domain -Dtest=OrderTest

# After Step 2
mvn clean compile -pl order-domain

# After Step 3
mvn clean compile -pl catalog-adapters
mvn flyway:migrate  # Test migration
mvn test -pl catalog-adapters -Dtest=OrderJpaMapperTest,OrderRepositoryJpaAdapterTest

# After Steps 4–6
mvn clean compile -pl catalog-adapters
mvn test -pl catalog-adapters -Dtest=PaymentGatewayMockAdapterTest,ShippingRateMockAdapterTest,NotificationMockAdapterTest

# After Step 8
mvn test -Dtest=OrderApplicationServiceTest -pl order-domain

# After Step 9
mvn clean compile -pl catalog-web
# Manual QA: open checkout in browser

# After Step 13
mvn test -Dtest=OrderHexagonalArchitectureTest -pl catalog-adapters
```

---

## Final Build Verification

```bash
# Clean build (must succeed)
mvn clean install

# Run all Order module tests
mvn test -Dtest=*Order*,*Checkout*,*Payment*,*Shipping*,*Notification*

# Build deployable EAR
mvn clean install -pl ear

# Deploy to Open Liberty and smoke test
# (Manual: open browser, add to cart, checkout, verify order created)
```
