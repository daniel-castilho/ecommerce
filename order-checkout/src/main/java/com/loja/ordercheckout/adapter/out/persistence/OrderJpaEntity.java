package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.OrderTimelineEntry;
import com.loja.ordercheckout.domain.model.PaymentInfo;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.shared.domain.Money;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA persistence entity for Order.
 * Isolated in the adapter layer — the domain object (Order) never carries
 * framework annotations. Mapping is explicit via {@code fromDomain}/{@code toDomain},
 * following the UserJpaEntity/ProductJpaEntity pattern.
 */
@Entity
@Table(name = "tb_order")
public class OrderJpaEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Embedded
    private ShippingAddressEmbeddable shippingAddress;

    @Column(name = "shipping_cost", precision = 19, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Column(name = "coupon_code", length = 36)
    private String couponCode;

    @Column(name = "discount_amount", precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Embedded
    private PaymentInfoEmbeddable paymentInfo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tb_order_item", joinColumns = @JoinColumn(name = "order_id"))
    @OrderBy("position")
    private List<OrderLineEmbeddable> items = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_status_history", joinColumns = @JoinColumn(name = "order_id"))
    @OrderBy("occurredAt")
    private List<OrderStatusHistoryEmbeddable> timeline = new ArrayList<>();

    protected OrderJpaEntity() { }

    public static OrderJpaEntity fromDomain(Order order) {
        OrderJpaEntity e = new OrderJpaEntity();
        e.id = order.getId();
        e.userId = order.getUserId();
        e.customerEmail = order.getCustomerEmail();
        e.status = order.getStatus();
        e.createdAt = order.getCreatedAt();
        e.updatedAt = order.getUpdatedAt();
        e.shippingAddress = ShippingAddressEmbeddable.fromDomain(order.getShippingAddress());
        e.shippingCost = order.getShippingCost() == null ? null : order.getShippingCost().getAmount();
        e.trackingNumber = order.getTrackingNumber();
        e.couponCode = order.getCouponCode();
        e.discountAmount = order.getDiscountAmount() == null ? null : order.getDiscountAmount().getAmount();
        e.paymentInfo = PaymentInfoEmbeddable.fromDomain(order.getPaymentInfo());
        e.version = order.getVersion();
        e.items = new ArrayList<>(order.getItems().stream()
                .map(OrderLineEmbeddable::fromDomain)
                .toList());
        e.timeline = new ArrayList<>(order.getTimeline().stream()
                .map(OrderStatusHistoryEmbeddable::fromDomain)
                .toList());
        return e;
    }

    public Order toDomain() {
        return Order.restore(id, userId, customerEmail, createdAt, status,
                items.stream().map(OrderLineEmbeddable::toDomain).toList(),
                shippingAddress == null ? null : shippingAddress.toDomain(),
                shippingCost == null ? null : new Money(shippingCost),
                trackingNumber,
                paymentInfo == null ? null : paymentInfo.toDomain(),
                updatedAt, version,
                timeline.stream().map(OrderStatusHistoryEmbeddable::toDomain).toList(),
                couponCode, discountAmount == null ? null : new Money(discountAmount));
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
    public List<OrderLineEmbeddable> getItems() { return items; }
    public List<OrderStatusHistoryEmbeddable> getTimeline() { return timeline; }

    @Embeddable
    public static class OrderLineEmbeddable {

        @Column(name = "product_id", nullable = false, length = 36)
        private String productId;

        @Column(name = "product_name", length = 255)
        private String productName;

        @Column(nullable = false)
        private int quantity;

        @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
        private BigDecimal unitPrice;

        @Column(name = "position")
        private int position;

        protected OrderLineEmbeddable() { }

        public static OrderLineEmbeddable fromDomain(OrderLine line) {
            OrderLineEmbeddable e = new OrderLineEmbeddable();
            e.productId = line.getProductId();
            e.productName = line.getProductName();
            e.quantity = line.getQuantity();
            e.unitPrice = line.getUnitPrice().getAmount();
            e.position = line.getPosition();
            return e;
        }

        public OrderLine toDomain() {
            return new OrderLine(productId, productName, new Money(unitPrice), quantity, position);
        }

        public String getProductId() { return productId; }
        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public int getPosition() { return position; }
    }

    @Embeddable
    public static class OrderStatusHistoryEmbeddable {

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private OrderStatus status;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        @Column(nullable = false, length = 120)
        private String label;

        protected OrderStatusHistoryEmbeddable() { }

        public static OrderStatusHistoryEmbeddable fromDomain(OrderTimelineEntry entry) {
            OrderStatusHistoryEmbeddable e = new OrderStatusHistoryEmbeddable();
            e.status = entry.status();
            e.occurredAt = entry.occurredAt();
            e.label = entry.label();
            return e;
        }

        public OrderTimelineEntry toDomain() {
            return new OrderTimelineEntry(status, occurredAt, label);
        }

        public OrderStatus getStatus() { return status; }
        public Instant getOccurredAt() { return occurredAt; }
        public String getLabel() { return label; }
    }

    @Embeddable
    public static class ShippingAddressEmbeddable {

        @Column(name = "recipient_name", length = 120)
        private String recipientName;

        @Column(name = "street", length = 255)
        private String street;

        @Column(name = "number", length = 20)
        private String number;

        @Column(name = "complement", length = 120)
        private String complement;

        @Column(name = "neighborhood", length = 120)
        private String neighborhood;

        @Column(name = "city", length = 120)
        private String city;

        @Column(name = "state", length = 2)
        private String state;

        @Column(name = "postal_code", length = 9)
        private String postalCode;

        @Column(name = "phone_number", length = 30)
        private String phoneNumber;

        protected ShippingAddressEmbeddable() { }

        public static ShippingAddressEmbeddable fromDomain(ShippingAddress address) {
            if (address == null) {
                return null;
            }
            ShippingAddressEmbeddable e = new ShippingAddressEmbeddable();
            e.recipientName = address.getRecipientName();
            e.street = address.getStreet();
            e.number = address.getNumber();
            e.complement = address.getComplement();
            e.neighborhood = address.getNeighborhood();
            e.city = address.getCity();
            e.state = address.getState();
            e.postalCode = address.getPostalCode();
            e.phoneNumber = address.getPhoneNumber();
            return e;
        }

        public ShippingAddress toDomain() {
            if (recipientName == null && street == null && city == null) {
                return null;
            }
            return new ShippingAddress(recipientName, street, number, complement, neighborhood,
                    city, state, postalCode, phoneNumber);
        }

        public String getRecipientName() { return recipientName; }
        public String getStreet() { return street; }
        public String getNumber() { return number; }
        public String getComplement() { return complement; }
        public String getNeighborhood() { return neighborhood; }
        public String getCity() { return city; }
        public String getState() { return state; }
        public String getPostalCode() { return postalCode; }
        public String getPhoneNumber() { return phoneNumber; }
    }

    @Embeddable
    public static class PaymentInfoEmbeddable {

        @Column(name = "payment_method", length = 20)
        private String method;

        @Column(name = "authorization_id", length = 64)
        private String authorizationId;

        @Column(name = "capture_id", length = 64)
        private String captureId;

        @Column(name = "gateway_transaction_id", length = 255)
        private String gatewayTransactionId;

        @Column(name = "authorized_amount", precision = 19, scale = 2)
        private BigDecimal authorizedAmount;

        @Column(name = "captured_amount", precision = 19, scale = 2)
        private BigDecimal capturedAmount;

        @Column(name = "refunded_amount", precision = 19, scale = 2)
        private BigDecimal refundedAmount;

        @Column(name = "authorization_time")
        private Instant authorizationTime;

        @Column(name = "capture_time")
        private Instant captureTime;

        @Column(name = "payment_status", length = 20)
        @Enumerated(EnumType.STRING)
        private PaymentInfo.PaymentStatus status;

        protected PaymentInfoEmbeddable() { }

        public static PaymentInfoEmbeddable fromDomain(PaymentInfo info) {
            if (info == null) {
                return null;
            }
            PaymentInfoEmbeddable e = new PaymentInfoEmbeddable();
            e.method = info.getMethod();
            e.authorizationId = info.getAuthorizationId();
            e.captureId = info.getCaptureId();
            e.gatewayTransactionId = info.getGatewayTransactionId();
            e.authorizedAmount = info.getAuthorizedAmount().getAmount();
            e.capturedAmount = info.getCapturedAmount().getAmount();
            e.refundedAmount = info.getRefundedAmount().getAmount();
            e.authorizationTime = info.getAuthorizationTime();
            e.captureTime = info.getCaptureTime();
            e.status = info.getStatus();
            return e;
        }

        public PaymentInfo toDomain() {
            if (authorizationId == null && captureId == null && status == null) {
                return null;
            }
            return PaymentInfo.restore(method, authorizationId, captureId, gatewayTransactionId,
                    new Money(authorizedAmount == null ? BigDecimal.ZERO : authorizedAmount),
                    new Money(capturedAmount == null ? BigDecimal.ZERO : capturedAmount),
                    new Money(refundedAmount == null ? BigDecimal.ZERO : refundedAmount),
                    authorizationTime, captureTime, status);
        }

        public String getMethod() { return method; }
        public String getAuthorizationId() { return authorizationId; }
        public String getCaptureId() { return captureId; }
        public String getGatewayTransactionId() { return gatewayTransactionId; }
        public BigDecimal getAuthorizedAmount() { return authorizedAmount; }
        public BigDecimal getCapturedAmount() { return capturedAmount; }
        public BigDecimal getRefundedAmount() { return refundedAmount; }
        public Instant getAuthorizationTime() { return authorizationTime; }
        public Instant getCaptureTime() { return captureTime; }
        public PaymentInfo.PaymentStatus getStatus() { return status; }
    }
}
