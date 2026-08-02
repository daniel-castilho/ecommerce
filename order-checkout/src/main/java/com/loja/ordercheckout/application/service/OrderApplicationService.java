package com.loja.ordercheckout.application.service;

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
import com.loja.productcatalog.domain.exception.InsufficientStockException;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the checkout workflow: build the order, check inventory before any
 * payment is attempted, quote shipping, then authorize/capture the payment and
 * persist. Depends only on ports (DIP) — adapters are wired by CDI.
 */
@ApplicationScoped
public class OrderApplicationService implements CreateOrderFromCartUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ProductRepositoryPort productRepository;
    private final PaymentGatewayPort paymentGateway;
    private final ShippingRatePort shippingRate;
    private final NotificationPort notification;

    @Inject
    public OrderApplicationService(OrderRepositoryPort orderRepository,
                                   ProductRepositoryPort productRepository,
                                   PaymentGatewayPort paymentGateway,
                                   ShippingRatePort shippingRate,
                                   NotificationPort notification) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentGateway = paymentGateway;
        this.shippingRate = shippingRate;
        this.notification = notification;
    }

    @Transactional
    @Override
    public Order checkout(CheckoutCommand command) {
        String orderId = resolveOrderId(command.requestId());
        Optional<Order> existing = orderRepository.findById(orderId);
        if (existing.isPresent()) {
            return existing.get();
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

        for (OrderLine line : order.getItems()) {
            if (productRepository.decrementStock(line.getProductId(), line.getQuantity()) == 0) {
                throw new InsufficientStockException("Insufficient stock for product: " + line.getProductName());
            }
        }

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
        } catch (PaymentFailedException e) {
            return orderRepository.save(order);
        }

        Order saved = orderRepository.save(order);
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
