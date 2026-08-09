package com.loja.ordercheckout.application.dto;

import com.loja.ordercheckout.domain.model.PaymentMethod;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import java.util.List;

/**
 * All inputs required to place an order. Carried by
 * {@link com.loja.ordercheckout.domain.port.in.CreateOrderFromCartUseCase}.
 *
 * @param requestId      client-generated idempotency key; the resulting order id
 * @param userId         owner of the cart
 * @param customerEmail  confirmation recipient
 * @param items          products and quantities to buy
 * @param shippingAddress destination address used for the shipping quote
 * @param shippingMethod selected shipping method id
 * @param paymentMethod  selected payment method and token
 * @param couponCode     optional coupon code entered at checkout (may be null)
 */
public record CheckoutCommand(String requestId, String userId, String customerEmail,
                              List<ItemCheckoutRequest> items, ShippingAddress shippingAddress,
                              String shippingMethod, PaymentMethod paymentMethod,
                              String couponCode) { }
