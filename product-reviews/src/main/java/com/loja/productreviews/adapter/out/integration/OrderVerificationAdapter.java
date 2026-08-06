package com.loja.productreviews.adapter.out.integration;

import java.util.EnumSet;
import java.util.Set;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.productreviews.domain.port.out.OrderVerificationPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Thin adapter for {@link OrderVerificationPort} that delegates to the public
 * {@code OrderRepositoryPort} of the order-checkout module.
 *
 * <p>A purchase counts iff the user has at least one order in
 * CONFIRMED, SHIPPED or DELIVERED state containing the product.
 */
@ApplicationScoped
public class OrderVerificationAdapter implements OrderVerificationPort {

    private static final Set<OrderStatus> PURCHASED_STATUSES =
            EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepositoryPort orderRepository;

    @Inject
    public OrderVerificationAdapter(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public boolean hasUserPurchasedProduct(String userId, String productId) {
        int page = 0;
        int pageSize = PageResult.MAX_PAGE_SIZE;
        while (true) {
            PageResult<Order> result = orderRepository.findByCustomerId(userId, page, pageSize);
            for (Order order : result.items()) {
                if (PURCHASED_STATUSES.contains(order.getStatus())
                        && order.getItems().stream()
                        .anyMatch(line -> line.getProductId().equals(productId))) {
                    return true;
                }
            }
            if (page + 1 >= result.totalPages()) {
                return false;
            }
            page++;
        }
    }
}
