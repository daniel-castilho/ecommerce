package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.domain.exception.PaymentFailedException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.PaymentAuthorization;
import com.loja.ordercheckout.domain.model.PaymentCapture;
import com.loja.ordercheckout.domain.model.PaymentMethod;
import com.loja.ordercheckout.domain.model.PaymentRefund;
import com.loja.shared.domain.Money;

/**
 * Outbound port for the payment gateway. Implementations wrap a real provider
 * (Stripe, PagSeguro) or a local mock. The authorize → capture → refund lifecycle
 * is initiated by the application service; the domain applies the results through
 * {@code Order.authorize}/{@code capture}/{@code requestRefund}.
 */
public interface PaymentGatewayPort {

    /**
     * Requests a hold on the funds for an order using the given tokenized payment
     * method. A successful authorization reserves the amount but does not charge it.
     *
     * @param order  the order being paid for; the authorization amount is {@code order.total()}
     * @param method the tokenized payment method supplied by the client
     * @return the authorization details, including a non-empty {@code authorizationId}
     * @throws PaymentFailedException if the provider rejects or cannot process the request
     */
    PaymentAuthorization authorize(Order order, PaymentMethod method) throws PaymentFailedException;

    /**
     * Confirms a previous authorization, actually moving the funds.
     *
     * @param authorizationId the id returned by {@link #authorize}
     * @return the capture details, including a non-empty {@code captureId} and the captured amount
     * @throws PaymentFailedException if the authorization id is unknown or the capture fails
     */
    PaymentCapture capture(String authorizationId) throws PaymentFailedException;

    /**
     * Returns part or all of a captured amount to the customer's payment method.
     *
     * @param captureId the id returned by {@link #capture}
     * @param amount    the amount to refund; must be positive
     * @return the refund details, including a non-empty {@code refundId} and the refunded amount
     * @throws PaymentFailedException if the capture id is unknown or the refund fails
     */
    PaymentRefund refund(String captureId, Money amount) throws PaymentFailedException;
}
