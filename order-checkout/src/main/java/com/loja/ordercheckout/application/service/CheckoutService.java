package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderItem;
import com.loja.ordercheckout.domain.port.in.CheckoutUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.productcatalog.domain.exception.InsufficientStockException;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * This service depends only on PORTS from other modules
 * (ProductRepositoryPort), never on a concrete adapter from product-catalog.
 * This keeps modules loosely coupled within the monolith.
 */
@ApplicationScoped
public class CheckoutService implements CheckoutUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ProductRepositoryPort productRepository;

    @Inject
    public CheckoutService(OrderRepositoryPort orderRepository,
                            ProductRepositoryPort productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    @Override
    public Order checkout(String userId, List<ItemCheckoutRequest> itemsRequest) {
        Order order = new Order(UUID.randomUUID().toString(), userId);

        for (ItemCheckoutRequest req : itemsRequest) {
            if (req.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }

            Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + req.productId()));

            int affected = productRepository.decrementStock(req.productId(), req.quantity());
            if (affected == 0) {
                throw new InsufficientStockException("Insufficient stock for product: " + product.getName());
            }

            order.addItem(new OrderItem(product.getId(), req.quantity(), product.getPrice()));
        }

        order.confirm();
        return orderRepository.save(order);
    }
}
