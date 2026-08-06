package com.loja.productreviews.domain.port.out;

/**
 * Thin verification port against {@code order-checkout}.
 *
 * <p>Implemented by {@code OrderVerificationAdapter} in this module;
 * the adapter delegates to the public
 * {@code OrderRepositoryPort} of order-checkout and returns true iff
 * the user has at least one order in CONFIRMED, SHIPPED or DELIVERED
 * state that contains the given product.
 */
public interface OrderVerificationPort {

    /**
     * @param userId    customer id
     * @param productId target product
     * @return true iff the user has purchased the product and the order has not been cancelled/refunded
     */
    boolean hasUserPurchasedProduct(String userId, String productId);
}
