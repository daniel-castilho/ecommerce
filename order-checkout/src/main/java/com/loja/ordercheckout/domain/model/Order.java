package com.loja.ordercheckout.domain.model;

import com.loja.ordercheckout.domain.exception.InvalidOrderStateException;
import com.loja.shared.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root of the order module. Pure domain: no framework imports.
 *
 * <p>Lifecycle (see {@link #canTransitionTo(OrderStatus)}):
 * PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED, with CANCELLED (before
 * delivery) and REFUNDED (after capture) as terminal branch states.
 */
public final class Order {

    private final String id;
    private final String userId;
    private String customerEmail;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    private final List<OrderLine> items = new ArrayList<>();
    private ShippingAddress shippingAddress;
    private Money shippingCost;
    private OrderStatus status;
    private PaymentInfo paymentInfo;
    private String trackingNumber;

    public Order(String id, String userId) {
        this(id, userId, Instant.now(), OrderStatus.PENDING);
    }

    public Order(String id, String userId, String customerEmail) {
        this(id, userId, Instant.now(), OrderStatus.PENDING);
        this.customerEmail = customerEmail;
    }

    /** Persistence round-trip constructor (createdAt comes from the store). */
    public Order(String id, String userId, Instant createdAt) {
        this(id, userId, createdAt, OrderStatus.PENDING);
    }

    public static Order create(String userId, List<OrderLine> lines, ShippingAddress shippingAddress) {
        Order order = new Order(UUID.randomUUID().toString(), userId);
        order.shippingAddress = shippingAddress;
        lines.forEach(order::addItem);
        return order;
    }

    public static Order create(String userId, String customerEmail, List<OrderLine> lines,
                               ShippingAddress shippingAddress) {
        Order order = create(userId, lines, shippingAddress);
        order.customerEmail = customerEmail;
        return order;
    }

    /** Restores an exact persisted snapshot; bypasses the state machine guards. */
    public static Order restore(String id, String userId, String customerEmail, Instant createdAt,
                                OrderStatus status, List<OrderLine> items, ShippingAddress shippingAddress,
                                Money shippingCost, String trackingNumber, PaymentInfo paymentInfo,
                                Instant updatedAt) {
        return restore(id, userId, customerEmail, createdAt, status, items, shippingAddress,
                shippingCost, trackingNumber, paymentInfo, updatedAt, 0L);
    }

    /** Restores an exact persisted snapshot including the optimistic-lock version. */
    public static Order restore(String id, String userId, String customerEmail, Instant createdAt,
                                OrderStatus status, List<OrderLine> items, ShippingAddress shippingAddress,
                                Money shippingCost, String trackingNumber, PaymentInfo paymentInfo,
                                Instant updatedAt, long version) {
        Order order = new Order(id, userId, createdAt, status);
        order.customerEmail = customerEmail;
        order.items.addAll(items);
        order.shippingAddress = shippingAddress;
        order.shippingCost = shippingCost;
        order.trackingNumber = trackingNumber;
        order.paymentInfo = paymentInfo;
        order.updatedAt = updatedAt;
        order.version = version;
        return order;
    }

    private Order(String id, String userId, Instant createdAt, OrderStatus status) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = status;
        this.shippingCost = Money.zero();
    }

    // ---- cart building (PENDING only) ----

    public void addItem(OrderLine line) {
        requireState(OrderStatus.PENDING);
        items.add(line);
    }

    public void setShippingAddress(ShippingAddress address) {
        requireState(OrderStatus.PENDING);
        this.shippingAddress = address;
    }

    public void setShippingCost(Money cost) {
        requireState(OrderStatus.PENDING);
        if (cost == null) {
            throw new IllegalArgumentException("Shipping cost is required");
        }
        this.shippingCost = cost;
    }

    // ---- lifecycle ----

    public void validateForCheckout() {
        if (items.isEmpty()) {
            throw new InvalidOrderStateException("Cart is empty");
        }
        if (shippingAddress == null) {
            throw new InvalidOrderStateException("Shipping address is required");
        }
    }

    public void authorize(PaymentAuthorization authorization) {
        requireState(OrderStatus.PENDING);
        this.paymentInfo = PaymentInfo.fromAuthorization(authorization);
    }

    public void capture(PaymentCapture capture) {
        requireState(OrderStatus.PENDING);
        if (paymentInfo == null || paymentInfo.getAuthorizationId() == null
                || paymentInfo.getAuthorizationId().isBlank()) {
            throw new InvalidOrderStateException("Order is not authorized");
        }
        this.paymentInfo = paymentInfo.withCapture(capture);
        transitionTo(OrderStatus.CONFIRMED);
    }

    /** Legacy no-payment confirmation: moves a cart straight to CONFIRMED. */
    public void confirm() {
        if (items.isEmpty()) {
            throw new InvalidOrderStateException("Order cannot be confirmed without items");
        }
        transitionTo(OrderStatus.CONFIRMED);
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
    }

    public void process() {
        transitionTo(OrderStatus.PROCESSING);
    }

    public void ship(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number is required");
        }
        transitionTo(OrderStatus.SHIPPED);
        this.trackingNumber = trackingNumber;
    }

    public void deliver() {
        transitionTo(OrderStatus.DELIVERED);
    }

    public void requestRefund(Money amount) {
        if (paymentInfo == null || !paymentInfo.isCaptured()) {
            throw new InvalidOrderStateException("Order has no captured payment to refund");
        }
        if (!paymentInfo.canRefund(amount)) {
            throw new InvalidOrderStateException("Refund amount exceeds the remaining balance");
        }
        this.paymentInfo = paymentInfo.withRefund(amount, Instant.now());
        transitionTo(OrderStatus.REFUNDED);
    }

    public boolean canTransitionTo(OrderStatus target) {
        return switch (status) {
            case PENDING -> target == OrderStatus.CONFIRMED || target == OrderStatus.CANCELLED;
            case CONFIRMED -> target == OrderStatus.PROCESSING || target == OrderStatus.SHIPPED
                    || target == OrderStatus.CANCELLED || target == OrderStatus.REFUNDED
                    || target == OrderStatus.REFUND_REQUESTED;
            case PROCESSING -> target == OrderStatus.SHIPPED || target == OrderStatus.CANCELLED
                    || target == OrderStatus.REFUNDED || target == OrderStatus.REFUND_REQUESTED;
            case SHIPPED -> target == OrderStatus.DELIVERED || target == OrderStatus.CANCELLED
                    || target == OrderStatus.REFUNDED || target == OrderStatus.REFUND_REQUESTED;
            case DELIVERED -> target == OrderStatus.REFUNDED || target == OrderStatus.REFUND_REQUESTED;
            case REFUND_REQUESTED -> target == OrderStatus.REFUNDED || target == OrderStatus.DELIVERED;
            case CANCELLED, REFUNDED -> false;
        };
    }

    /**
     * Moves the order to {@code target}, enforcing the state machine
     * ({@link #canTransitionTo(OrderStatus)}). Used by the refund workflow to
     * mark an order as awaiting a refund decision and to apply the outcome.
     *
     * @throws InvalidOrderStateException if the transition is not allowed
     */
    public void updateStatus(OrderStatus target) {
        transitionTo(target);
    }

    private void transitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidOrderStateException(
                    "Cannot transition order from " + status + " to " + target);
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    private void requireState(OrderStatus expected) {
        if (status != expected) {
            throw new InvalidOrderStateException("Cannot modify an order with status " + status);
        }
    }

    public Money getTotal() {
        Money linesTotal = items.stream()
                .map(OrderLine::lineTotal)
                .reduce(Money.zero(), Money::add);
        return shippingCost == null ? linesTotal : linesTotal.add(shippingCost);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getCustomerEmail() { return customerEmail; }
    public long getVersion() { return version; }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public OrderStatus getStatus() { return status; }
    public List<OrderLine> getItems() { return Collections.unmodifiableList(items); }
    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public Money getShippingCost() { return shippingCost; }
    public PaymentInfo getPaymentInfo() { return paymentInfo; }
    public String getTrackingNumber() { return trackingNumber; }
}
