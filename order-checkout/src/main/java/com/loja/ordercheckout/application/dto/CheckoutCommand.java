package com.loja.ordercheckout.application.dto;

import com.loja.ordercheckout.domain.model.PaymentMethod;
import com.loja.ordercheckout.domain.model.ShippingAddress;

/**
 * All inputs required to place an order. Carried by
 * {@link com.loja.ordercheckout.domain.port.in.CreateOrderFromCartUseCase}.
 *
 * <p>The order items are <b>not</b> part of the command: the service loads the
 * user's persisted cart by {@code userId} as the single source of truth (see
 * {@code tasks/persistent-cart-implementation-sequence.md} step 6) and clears it
 * on a confirmed order.
 *
 * @param requestId      client-generated idempotency key; the resulting order id
 * @param userId         owner of the cart
 * @param customerEmail  confirmation recipient
 * @param shippingAddress destination address used for the shipping quote
 * @param shippingMethod selected shipping method id
 * @param paymentMethod  selected payment method and token
 * @param couponCode     optional coupon code entered at checkout (may be null)
 */
public record CheckoutCommand(String requestId, String userId, String customerEmail,
                              ShippingAddress shippingAddress,
                              String shippingMethod, PaymentMethod paymentMethod,
                              String couponCode) { }
