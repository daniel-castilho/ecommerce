package com.loja.ordercheckout.domain.port.in;

import com.loja.ordercheckout.application.dto.CartView;

/**
 * Read the customer's cart enriched with live catalog data for display.
 */
public interface GetCartUseCase {

    /**
     * @param userId authenticated owner
     * @return the cart with live product snapshots; an empty {@link CartView}
     *         (never null) when the user has no cart yet
     */
    CartView getCart(String userId);
}
