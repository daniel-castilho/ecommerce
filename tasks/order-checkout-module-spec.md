# Order & Checkout Module — Implementation Specification

**Audience:** this document is written for an AI coding agent that will implement the changes directly in the `java-ee-online-shop` repository. It assumes the stack and conventions already established in the project's `coding-standards.md` (Jakarta EE 10/11, multi-module Maven layout, CDI/hexagonal architecture, JPA, JSF/Facelets, `java.util.logging`, Lombok available) **and the patterns established by the Product Catalog module**, which serves as the reference implementation.

**Status:** draft for implementation. Section 14 lists assumptions the implementer should flag back to the human if they turn out to be wrong. Companion documents: `order-checkout-backlog.md` (story breakdown) and `order-checkout-implementation-sequence.md` (step-by-step build order).

---

## 0. Architecture: Hexagonal with Catalog Integration

This module **reuses the established hexagonal pattern** from the Product Catalog module. The key difference: Order has a **richer state machine** (PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED + CANCELLED/REFUNDED branches) and **multiple outbound integrations** (payment gateway, shipping carrier, notification service).

**Dependency rule (critical):** Order depends **inward only**. Additionally, Order module depends on **Catalog module's ports** to check product existence and reserve inventory.

```
Domain (order.domain)
    ↑
Application (order.application.port.in + .service)
    ↑
Adapters:
  - Persistence (order.adapter.persistence)
  - Payment (order.adapter.payment)
  - Shipping (order.adapter.shipping)
  - Notification (order.adapter.notification)
  - Inventory (calls catalog.application.port.out.InventoryReservationPort)
    ↑
Web (order.web.jsf.beans)
```

**Key principle:** Each external system (payment processor, shipping carrier, notification service) is behind a **port interface**. This allows swapping implementations without changing domain/application logic.

---

## 1. Purpose & Scope

Extend the e-commerce application with a complete Order and Checkout workflow: shopping cart → order creation → payment processing → shipping integration → customer notification → order fulfillment tracking.

**In scope:**
- Order domain aggregate (Order, OrderLine, OrderStatus state machine)
- Checkout UI (multi-step form: cart review → shipping → payment → confirmation)
- Payment gateway integration (authorize/capture/refund cycle)
- Shipping rate lookup and label generation
- Email/SMS notification on order events
- Order management (view history, cancel, request refund)
- Inventory integration (reserve stock on order creation, decrement on confirmation)
- Audit logging (every payment transaction, every status change)

**Out of scope (explicitly deferred):**
- Subscription/recurring orders — future feature
- Multi-currency pricing — single currency (BRL) assumed
- Marketplace/vendor fulfillment — all orders fulfilled by system, not vendors
- Complex refund rules (e.g., restocking fees, partial refunds) — start simple (full refund or no refund)
- PCI-DSS compliance details — use tokenized payment providers (Stripe/PagSeguro) to avoid storing raw card data

---

## 2. Module / Package Layout

Assume a new Maven module `order-domain` (similar to the `catalog-domain` separation in Catalog module) for domain purity, plus existing `catalog-adapters` and `catalog-web` for adapters and web layer.

```
order-domain/src/main/java/.../order/domain/
├── Order.java                    (aggregate root, no JPA)
├── OrderLine.java                (immutable value object)
├── OrderStatus.java              (enum: PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED)
├── ShippingAddress.java          (value object)
├── PaymentInfo.java              (value object, tracks payment method + status)
├── CustomerId.java               (strongly-typed ID, future use if user/customer module added)
├── OrderId.java                  (strongly-typed ID)
├── exception/
│   ├── OrderNotFoundException.java
│   ├── InvalidOrderStateException.java
│   ├── InsufficientInventoryException.java
│   └── PaymentFailedException.java

order-domain/src/main/java/.../order/application/
├── port/in/
│   ├── CreateOrderFromCartUseCase.java
│   ├── CancelOrderUseCase.java
│   ├── RequestRefundUseCase.java
│   ├── GetOrderDetailsUseCase.java
│   └── ListOrdersForCustomerUseCase.java
├── port/out/
│   ├── OrderRepositoryPort.java
│   ├── PaymentGatewayPort.java
│   ├── ShippingRatePort.java
│   ├── NotificationPort.java
│   └── InventoryReservationPort.java      (calls into catalog.application)
├── service/
│   ├── OrderApplicationService.java       (orchestrates all use cases)
│   └── OrderSearchCriteria.java, PageResult.java (DTOs)

catalog-adapters/src/main/java/.../order/adapter/persistence/
├── entity/
│   ├── OrderJpaEntity.java
│   ├── OrderLineJpaEntity.java
│   ├── PaymentTransactionJpaEntity.java
│   └── AuditableJpaEntity.java            (reuse from catalog module)
├── OrderJpaMapper.java
├── OrderRepositoryJpaAdapter.java

catalog-adapters/src/main/java/.../order/adapter/payment/
├── PaymentGatewayPort.java                (interface, moved here for adapter package)
├── PaymentGatewayStripeAdapter.java       (real Stripe implementation)
├── PaymentGatewayPagSeguroAdapter.java    (real PagSeguro implementation, optional)
└── PaymentGatewayMockAdapter.java         (local dev mock)

catalog-adapters/src/main/java/.../order/adapter/shipping/
├── ShippingRatePort.java                  (interface)
├── ShippingRateCorreiosAdapter.java       (Correios API integration)
└── ShippingRateMockAdapter.java           (local dev mock)

catalog-adapters/src/main/java/.../order/adapter/notification/
├── NotificationPort.java                  (interface)
├── NotificationEmailAdapter.java          (SendGrid or SMTP)
├── NotificationSmsAdapter.java            (Twilio or local provider)
└── NotificationMockAdapter.java           (local dev mock)

catalog-web/servlet/src/main/java/.../web/jsf/beans/
├── CheckoutBean.java                      (multi-step checkout form, @ViewScoped)
└── OrderHistoryBean.java                  (view orders, cancel, track, @ViewScoped)

catalog-web/servlet/src/main/webapp/
├── checkout.xhtml                         (4-step form)
├── checkout-success.xhtml                 (confirmation page)
└── order-history.xhtml                    (customer order list + details)
```

---

## 3. Domain Model

### 3.1 `Order` Domain Object (Core Aggregate)

```java
public final class Order {
    private OrderId id;
    private CustomerId customerId;
    private List<OrderLine> lines;          // immutable
    private ShippingAddress shippingAddress;
    private Money total;
    private Money shippingCost;
    private OrderStatus status;
    private PaymentInfo paymentInfo;
    private String shippingTrackingNumber;
    private Instant createdAt;
    private Instant updatedAt;
}
```

**Business methods (domain logic, no framework):**

1. **`validateForCheckout()`** — throws `InvalidOrderStateException` if:
   - Cart is empty (no lines)
   - Any line quantity ≤ 0
   - Total < 0 (defensive check)
   - Shipping address is incomplete

2. **`authorize(PaymentGateway gateway, PaymentMethod method)`** — orchestrates:
   - Call `gateway.authorize(this, method)` → returns `PaymentAuthorization`
   - Store authorization in `paymentInfo`
   - Does NOT transition status (caller decides next step)
   - Throws `PaymentFailedException` if authorize fails

3. **`capture(PaymentGateway gateway)`** — after authorization succeeds:
   - Call `gateway.capture(authorizationId)` → returns `PaymentCapture`
   - Update `paymentInfo` with capture details
   - Transition status: PENDING → CONFIRMED
   - Throws `PaymentFailedException` if capture fails

4. **`canTransitionTo(OrderStatus target)`** — state machine enforcement:
   ```
   PENDING → CONFIRMED (only via capture)
   CONFIRMED → PROCESSING
   PROCESSING → SHIPPED
   SHIPPED → DELIVERED
   
   ANY_STATUS → CANCELLED (if not already DELIVERED)
   CONFIRMED/PROCESSING/SHIPPED → REFUNDED (special state after refund)
   
   DELIVERED and REFUNDED are terminal
   ```

5. **`requestRefund(Money amount)`** — throws if:
   - Status is not CONFIRMED/PROCESSING/SHIPPED/DELIVERED (no refund on PENDING)
   - Refund amount > remaining balance (idempotency: already-refunded amount doesn't get refunded again)
   - Sets status to REFUNDED
   - Stores refund metadata in `paymentInfo`

### 3.2 `OrderLine` Value Object

Immutable line item in the order. Captures product snapshot at time of order (price may have changed since).

```java
public final class OrderLine {
    private final Long productId;
    private final String productName;
    private final Money unitPrice;
    private final int quantity;
    private final int position;              // for ordering
    
    public Money lineTotal() { return unitPrice * quantity; }
}
```

### 3.3 `OrderStatus` Enum

```java
public enum OrderStatus {
    PENDING,      // Created, awaiting payment
    CONFIRMED,    // Payment captured, inventory reserved
    PROCESSING,   // Preparing for shipment
    SHIPPED,      // Handed off to carrier
    DELIVERED,    // Customer received (terminal)
    
    CANCELLED,    // Cancelled before delivery (terminal)
    REFUNDED      // Refund processed (terminal after payment)
}
```

### 3.4 `ShippingAddress` Value Object

```java
public final class ShippingAddress {
    private final String recipientName;
    private final String street;
    private final String number;
    private final String complement;
    private final String neighborhood;
    private final String city;
    private final String state;
    private final String postalCode;        // CEP for Brazil
    private final String phoneNumber;
    
    // Constructor validates all fields non-empty, CEP format, etc.
}
```

### 3.5 `PaymentInfo` Value Object

```java
public final class PaymentInfo {
    private final String method;            // "card", "pix", "boleto"
    private final String authorizationId;   // token from gateway
    private final String captureId;
    private final PaymentStatus status;     // AUTHORIZED, CAPTURED, REFUNDED
    private final Money authorizedAmount;
    private final Money capturedAmount;
    private final Money refundedAmount;
    private final Instant authorizationTime;
    private final Instant captureTime;
    private final String gatewayTransactionId;  // for support/debugging
}
```

### 3.6 JPA Entities (Adapter Layer Only)

These live in `catalog-adapters/.../adapter/persistence/entity/`, **not** in domain.

`OrderJpaEntity` (extends `AuditableJpaEntity`):
```java
@Entity
@Table(name = "ORDER_ENTITY")
public class OrderJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long customerId;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal shippingCost;
    
    @Column(length = 255)
    private String shippingTrackingNumber;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineJpaEntity> lines = new ArrayList<>();
    
    @Embedded
    private ShippingAddressEmbeddable shippingAddress;
    
    @Embedded
    private PaymentInfoEmbeddable paymentInfo;
    
    // ... getters/setters, no business logic
}
```

`OrderLineJpaEntity`:
```java
@Entity
@Table(name = "ORDER_LINE_ENTITY")
public class OrderLineJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    private OrderJpaEntity order;
    
    @Column(nullable = false)
    private Long productId;
    
    @Column(nullable = false, length = 255)
    private String productName;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(nullable = false)
    private Integer position;
}
```

`PaymentTransactionJpaEntity` (audit log, separate table):
```java
@Entity
@Table(name = "PAYMENT_TRANSACTION")
public class PaymentTransactionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long orderId;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private String type;  // AUTHORIZE, CAPTURE, REFUND
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private String status;  // SUCCESS, FAILED, PENDING
    
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(length = 255)
    private String gatewayTransactionId;
    
    @Column(length = 2000)
    private String gatewayResponse;  // for debugging
    
    @Column(nullable = false)
    private Instant createdAt;
}
```

---

## 4. Application Ports (Interfaces)

### 4.1 Inbound Ports (Use Cases)

All in `order-domain/src/main/java/.../order/application/port/in/`:

```java
public interface CreateOrderFromCartUseCase {
    /**
     * Creates an order from cart contents and applies payment processing.
     * @param customerId buyer
     * @param cartItems products to order
     * @param shippingAddress delivery address
     * @param shippingMethod selected method (affects cost)
     * @param paymentMethod card/pix/boleto
     * @return Order in CONFIRMED state (payment captured) or throws PaymentFailedException
     */
    Order createOrder(Long customerId, List<CartItem> cartItems, ShippingAddress shippingAddress,
                      String shippingMethod, PaymentMethod paymentMethod) throws PaymentFailedException;
}

public interface CancelOrderUseCase {
    /**
     * Cancels an order and initiates refund if payment was captured.
     * @param orderId
     * @throws InvalidOrderStateException if order is DELIVERED or already CANCELLED
     */
    void cancelOrder(Long orderId) throws InvalidOrderStateException;
}

public interface RequestRefundUseCase {
    /**
     * Customer requests a refund. Admin must approve (not auto-refunded).
     * @param orderId
     * @param reason cancellation reason (for support tracking)
     */
    void requestRefund(Long orderId, String reason) throws InvalidOrderStateException;
}

public interface GetOrderDetailsUseCase {
    Optional<Order> getOrderDetails(Long orderId);
}

public interface ListOrdersForCustomerUseCase {
    /**
     * Paginated list of customer's orders.
     */
    PageResult<Order> listOrders(Long customerId, int page, int pageSize, OrderSortField sortBy);
}
```

### 4.2 Outbound Ports (Adapter Boundaries)

All in `order-domain/src/main/java/.../order/application/port/out/`:

```java
public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(Long orderId);
    PageResult<Order> findByCustomerId(Long customerId, int page, int pageSize);
    List<Order> findByStatus(OrderStatus status);  // for admin dashboard
}

public interface PaymentGatewayPort {
    PaymentAuthorization authorize(Order order, PaymentMethod method) 
        throws PaymentFailedException;
    PaymentCapture capture(String authorizationId) 
        throws PaymentFailedException;
    PaymentRefund refund(String captureId, Money amount) 
        throws PaymentFailedException;
}

public interface ShippingRatePort {
    List<ShippingOption> getQuotes(ShippingAddress address) 
        throws ShippingException;
    ShippingLabel createLabel(Order order, ShippingOption selected) 
        throws ShippingException;
}

public interface NotificationPort {
    void notifyOrderConfirmed(Order order) throws NotificationException;
    void notifyOrderShipped(Order order, String trackingNumber) throws NotificationException;
    void notifyRefundRequested(Order order, String reason) throws NotificationException;
}

public interface InventoryReservationPort {
    // Called into catalog.application.port.out
    void reserveInventory(List<OrderLine> lines) throws InsufficientInventoryException;
    void decrementInventory(List<OrderLine> lines) throws InsufficientInventoryException;
    void releaseReservation(List<OrderLine> lines);  // on cancellation
}
```

---

## 5. Application Service

**`OrderApplicationService`** implements all inbound use cases and orchestrates outbound port calls:

```java
@ApplicationScoped
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
    private InventoryReservationPort inventory;  // injected from catalog module
    
    @Transactional
    @Override
    public Order createOrder(Long customerId, List<CartItem> cartItems, ShippingAddress address,
                            String shippingMethod, PaymentMethod paymentMethod) {
        // 1. Create Order (domain object, no JPA yet)
        Order order = Order.create(customerId, cartItems, address, shippingMethod);
        order.validateForCheckout();  // throws if incomplete
        
        // 2. Get shipping quote
        List<ShippingOption> options = shippingRate.getQuotes(address);
        ShippingOption selected = options.stream()
            .filter(o -> o.method().equals(shippingMethod))
            .findFirst()
            .orElseThrow(() -> new ShippingException("Shipping method not available"));
        order.setShippingCost(selected.cost());
        
        // 3. Reserve inventory (before payment, so we know stock is available)
        inventory.reserveInventory(order.lines());
        
        // 4. Authorize payment
        PaymentAuthorization auth = paymentGateway.authorize(order, paymentMethod);
        order.storeAuthorizationId(auth.id());
        
        // 5. Capture payment
        PaymentCapture capture = paymentGateway.capture(auth.id());
        order.capture(capture);  // domain method transitions PENDING → CONFIRMED
        
        // 6. Persist order
        order = orderRepository.save(order);
        
        // 7. Notify customer
        notification.notifyOrderConfirmed(order);
        
        // 8. Emit event (for analytics, async shipping label generation, etc.)
        events.emit(new OrderCreatedEvent(order.id(), customerId));
        
        return order;
    }
    
    @Transactional
    @Override
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        
        if (!order.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new InvalidOrderStateException("Cannot cancel order in status " + order.status());
        }
        
        // Release reserved inventory
        inventory.releaseReservation(order.lines());
        
        // Refund if payment was captured
        if (order.isCaptured()) {
            PaymentRefund refund = paymentGateway.refund(order.captureId(), order.total());
            order.applyRefund(refund);
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }
    
    // ... other use cases
}
```

---

## 6. Payment Gateway Integration (Port + Adapters)

### 6.1 Adapter Strategy: Tokenized Payments

**Never store raw card data.** Use payment provider's tokenization (Stripe Elements, PagSeguro iframe).

**PaymentGatewayStripeAdapter** (for Stripe):
- Receives a **token** from the client (created by Stripe Elements JS)
- Calls `Stripe.authorize()` with token → gets `charge_id`
- On confirmation, calls `Stripe.capture(charge_id)` → money deducted
- On refund, calls `Stripe.refund(charge_id)` → money returned

**PaymentGatewayPagSeguroAdapter** (for PagSeguro, Brazilian market):
- Similar flow but using PagSeguro XML API or webhook for notifications

**PaymentGatewayMockAdapter** (local dev):
- Returns fake authorization/capture/refund results
- Never fails (unless told to via a flag)
- Useful for testing checkout flow without real payment processor

### 6.2 Config & Secrets

Environment variables (`.env` or K8s secrets):
```
PAYMENT_GATEWAY_TYPE=stripe|pagseguro   # which adapter to instantiate
STRIPE_SECRET_KEY=sk_test_...           # if using Stripe
STRIPE_PUBLISHABLE_KEY=pk_test_...      # for frontend
PAGSEGURO_EMAIL=...                     # if using PagSeguro
PAGSEGURO_TOKEN=...
```

---

## 7. Shipping Integration

### 7.1 Correios Adapter (Brazil)

`ShippingRateCorreiosAdapter` calls Correios XML API to fetch:
- Available shipping methods (PAC, SEDEX, etc.)
- Cost for given ZIP code
- Estimated delivery time

Adapter also handles **shipping label generation** (SIGEPWEB API) to send to Correios.

### 7.2 Shipping Options Returned

```java
public record ShippingOption(
    String method,           // "pac", "sedex"
    Money cost,
    int estimatedDays,
    String description      // "PAC - 15 dias úteis"
)
```

### 7.3 Local Dev: Mock Adapter

Returns flat rates (e.g., PAC always $15, SEDEX always $30) so checkout works without Correios API.

---

## 8. Notification Integration

### 8.1 Email Adapter

Uses SendGrid API or internal SMTP to send transactional emails:
- Order confirmed (with order summary, tracking link once shipped)
- Order shipped (with carrier tracking number)
- Refund processed

Templates: `.hbs` or `.freemarker` files, not hardcoded HTML in Java.

### 8.2 SMS Adapter (Optional)

Twilio or local Brazilian provider (Zenvia) for SMS notifications on key events (shipped, delivered).

### 8.3 Local Dev: Mock Adapter

Logs notifications to console/logs instead of sending real emails/SMS.

---

## 9. Web Layer (Checkout UI)

### 9.1 `CheckoutBean.java` (`@ViewScoped`)

Multi-step form managing checkout conversation:

```java
@Named("checkoutBean")
@ViewScoped
public class CheckoutBean implements Serializable {
    
    @Inject
    private CreateOrderFromCartUseCase createOrderUseCase;
    
    @Inject
    private ShippingRatePort shippingRate;
    
    private int currentStep = 1;  // 1: review, 2: shipping, 3: payment, 4: confirm
    private Cart cart;
    private ShippingAddress shippingAddress;
    private List<ShippingOption> shippingOptions;
    private String selectedShippingMethod;
    private String stripeTokenId;  // from Stripe Elements on client
    
    @PostConstruct
    void initialize() {
        cart = cartService.getCart();  // from session
    }
    
    public void nextStep() {
        if (currentStep == 1) {
            // Review complete, move to shipping
            currentStep = 2;
        } else if (currentStep == 2) {
            // Shipping selected, get rates
            shippingOptions = shippingRate.getQuotes(shippingAddress);
            currentStep = 3;
        } else if (currentStep == 3) {
            // Payment form submitted (token from client-side JS)
            currentStep = 4;  // review before final confirm
        }
    }
    
    public void placeOrder() {
        try {
            PaymentMethod method = new PaymentMethod("card", stripeTokenId);
            Order order = createOrderUseCase.createOrder(
                getCurrentCustomerId(),
                cart.items(),
                shippingAddress,
                selectedShippingMethod,
                method
            );
            // Success: redirect to confirmation page
            FacesContext.getCurrentInstance().getExternalContext()
                .redirect("checkout-success.xhtml?orderId=" + order.id());
        } catch (PaymentFailedException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Payment failed", e.getMessage()));
            currentStep = 3;  // back to payment
        } catch (ShippingException e) {
            // ... similar error handling
        }
    }
}
```

### 9.2 `checkout.xhtml` (4-step form)

- **Step 1:** Cart review (product list, quantities, total)
- **Step 2:** Shipping address (CEP lookup for autocomplete) + select method
- **Step 3:** Payment form (Stripe Elements iframe, NOT raw card input)
- **Step 4:** Confirm (summary, "Place Order" button)

### 9.3 `OrderHistoryBean.java`

List customer's orders, show status, allow cancellation, request refund.

---

## 10. Database Schema & Migrations

**Flyway migration file:** `V4__order_and_checkout_schema.sql`

Tables:
- `ORDER_ENTITY` — orders with status, total, shipping address
- `ORDER_LINE_ENTITY` — line items (denormalized product snapshot)
- `PAYMENT_TRANSACTION` — audit log of every authorization/capture/refund
- `SHIPPING_LABEL_ENTITY` (optional) — track generated shipping labels

Indexes:
- `ORDER_ENTITY.customer_id, status` (frequent queries: "get my pending orders")
- `ORDER_ENTITY.created_at DESC` (list orders by most recent)
- `PAYMENT_TRANSACTION.order_id` (refund audit trail)

---

## 11. Testing Requirements

**Domain layer tests** (`OrderTest.java`):
- State machine transitions (only allowed paths work, illegal paths rejected)
- Order validation (validateForCheckout conditions)
- Line item calculations (quantity × price, total)

**Application service tests** (`OrderApplicationServiceTest.java`):
- CreateOrderFromCartUseCase: successful flow + payment failure recovery
- CancelOrderUseCase: cancellation with/without refund
- All use cases with mocked ports

**Adapter tests**:
- `OrderRepositoryJpaAdapterTest` — Testcontainers with real DB
- `PaymentGatewayStripeAdapterTest` — Stripe test account (if using Stripe)
- `ShippingRateCorreiosAdapterTest` — mock Correios for local dev
- `NotificationEmailAdapterTest` — mock SendGrid/SMTP

**E2E scenario test** (integration):
- Customer adds items to cart (via Catalog)
- Proceeds to checkout
- Enters address, selects shipping method
- Enters (mock) payment
- Clicks "Place Order"
- Order created in DB with status CONFIRMED
- Email notification sent (to mock SMTP)
- Customer can view order in history

---

## 12. Security & Authorization

- **Checkout:** Must be authenticated (customer logged in). Check via `@RolesAllowed("CUSTOMER")` or implicit via cart ownership.
- **Payment data:** Never logged or exposed. Only gateway transaction ID stored.
- **PCI-DSS:** Not in scope (payment provider handles compliance via tokenization).
- **HTTPS only:** Checkout page served over HTTPS. CSP headers configured.

---

## 13. Non-Functional Requirements

- **Checkout performance:** < 3s from cart to confirmation page (excluding network I/O to payment gateway)
- **Payment gateway timeouts:** 10s timeout; if gateway doesn't respond, fail gracefully with user message (don't hang)
- **Idempotency:** Same cart contents → same order (prevent duplicate orders if customer submits form twice)
- **Concurrency:** Two simultaneous checkouts from the same cart: second one fails with "cart already checked out" message
- **Audit trail:** Every payment action logged in `PAYMENT_TRANSACTION` table (success/failure, amount, gateway response)

---

## 14. Open Questions / Assumptions for Review

Flag these back if any assumption is wrong before implementing:

1. **Payment provider:** Stripe or PagSeguro? (Affects PaymentGatewayPort signature, config keys, SDK dependency)
2. **Shipping provider:** Correios only, or multi-carrier support? (Affects ShippingRatePort design)
3. **Notification channels:** Email only, or SMS too? SendGrid, SMTP, or other?
4. **Refund policy:** Auto-refund on cancellation, or manual approval? (Affects CancelOrderUseCase flow)
5. **Customer authentication:** Existing user module, or assume all checkouts are guest? (Affects CustomerId type)
6. **Inventory reservation:** How long to hold reservation if customer abandons checkout? (TTL for reservation release)
7. **Order confirmation number:** Generate custom format (ORD-20260726-0001) or use DB ID? (Affects Order domain)
8. **Multi-step checkout:** Required to be 4 steps, or can be condensed to 1-page checkout?
9. **Guest checkout:** Allow checkout without account creation?
10. **Subscription orders:** Recurring billing support in scope now, or defer to future?
