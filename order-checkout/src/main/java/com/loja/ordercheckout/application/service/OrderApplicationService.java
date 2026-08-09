package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.application.dto.CheckoutCommand;
import com.loja.ordercheckout.application.dto.ItemCheckoutRequest;
import com.loja.ordercheckout.domain.exception.AccountSuspendedException;
import com.loja.ordercheckout.domain.exception.PaymentFailedException;
import com.loja.ordercheckout.domain.exception.ShippingException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.PaymentAuthorization;
import com.loja.ordercheckout.domain.model.PaymentCapture;
import com.loja.ordercheckout.domain.model.ShippingOption;
import com.loja.ordercheckout.domain.port.in.CreateOrderFromCartUseCase;
import com.loja.ordercheckout.domain.port.out.NotificationPort;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.PaymentGatewayPort;
import com.loja.ordercheckout.domain.port.out.ShippingRatePort;
import com.loja.productcatalog.application.dto.ReservationRequest;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.out.InventoryReservationPort;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.promotions.application.dto.DiscountQuote;
import com.loja.promotions.domain.port.in.QuoteDiscountUseCase;
import com.loja.promotions.domain.port.in.RecordCouponRedemptionUseCase;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.UserRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the checkout workflow: build the order, reserve inventory before
 * any payment is attempted, quote shipping, then authorize/capture the payment
 * and persist. Depends only on ports (DIP) — adapters are wired by CDI.
 */
@ApplicationScoped
public class OrderApplicationService implements CreateOrderFromCartUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ProductRepositoryPort productRepository;
    private final PaymentGatewayPort paymentGateway;
    private final ShippingRatePort shippingRate;
    private final NotificationPort notification;
    private final InventoryReservationPort inventoryReservation;
    private final UserRepositoryPort userRepository;
    private final QuoteDiscountUseCase couponQuote;
    private final RecordCouponRedemptionUseCase couponRedemption;

    @Inject
    public OrderApplicationService(OrderRepositoryPort orderRepository,
                                   ProductRepositoryPort productRepository,
                                   PaymentGatewayPort paymentGateway,
                                   ShippingRatePort shippingRate,
                                   NotificationPort notification,
                                   InventoryReservationPort inventoryReservation,
                                   UserRepositoryPort userRepository,
                                   QuoteDiscountUseCase couponQuote,
                                   RecordCouponRedemptionUseCase couponRedemption) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentGateway = paymentGateway;
        this.shippingRate = shippingRate;
        this.notification = notification;
        this.inventoryReservation = inventoryReservation;
        this.userRepository = userRepository;
        this.couponQuote = couponQuote;
        this.couponRedemption = couponRedemption;
    }

    @Transactional
    @Override
    public Order checkout(CheckoutCommand command) {
        String orderId = resolveOrderId(command.requestId());
        Optional<Order> existing = orderRepository.findById(orderId);
        if (existing.isPresent()) {
            return existing.get();
        }

        User customer = userRepository.findById(command.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + command.userId()));
        if (!customer.isActive()) {
            throw new AccountSuspendedException(
                    "Your account has been blocked. Contact support to restore access.");
        }

        Order order = new Order(orderId, command.userId(), command.customerEmail());
        order.setShippingAddress(command.shippingAddress());
        int position = 0;
        for (ItemCheckoutRequest req : command.items()) {
            if (req.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            Product product = productRepository.findById(req.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + req.productId()));
            order.addItem(new OrderLine(product.getId(), product.getName(), product.getPrice(),
                    req.quantity(), position++));
        }
        order.validateForCheckout();

        String couponCode = command.couponCode();
        if (couponCode != null && !couponCode.isBlank()) {
            DiscountQuote quote = couponQuote.quote(couponCode, order.getMerchandiseSubtotal());
            order.applyCoupon(quote.code(), quote.discountAmount());
        }

        List<ReservationRequest> reservations = order.getItems().stream()
                .map(line -> new ReservationRequest(line.getProductId(), line.getQuantity()))
                .toList();
        inventoryReservation.reserve(order.getId(), reservations);

        ShippingOption selected = shippingRate.getQuotes(command.shippingAddress()).stream()
                .filter(option -> option.method().equals(command.shippingMethod()))
                .findFirst()
                .orElseThrow(() -> new ShippingException(
                        "Shipping method not available: " + command.shippingMethod()));
        order.setShippingCost(selected.cost());

        PaymentAuthorization authorization = paymentGateway.authorize(order, command.paymentMethod());
        order.authorize(authorization);

        try {
            PaymentCapture capture = paymentGateway.capture(authorization.authorizationId());
            order.capture(capture);
            inventoryReservation.confirm(order.getId());
        } catch (PaymentFailedException e) {
            inventoryReservation.release(order.getId());
            return orderRepository.save(order);
        }

        Order saved = orderRepository.save(order);
        if (saved.getCouponCode() != null) {
            couponRedemption.redeem(saved.getCouponCode());
        }
        notification.notifyOrderConfirmed(saved);
        return saved;
    }

    private String resolveOrderId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }
}
