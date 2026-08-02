package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable payment state of an order, rebuilt on every payment event
 * (authorize → capture → refund).
 */
public final class PaymentInfo {

    public enum PaymentStatus { AUTHORIZED, CAPTURED, REFUNDED }

    private final String method;
    private final String authorizationId;
    private final String captureId;
    private final String gatewayTransactionId;
    private final Money authorizedAmount;
    private final Money capturedAmount;
    private final Money refundedAmount;
    private final Instant authorizationTime;
    private final Instant captureTime;
    private final PaymentStatus status;

    private PaymentInfo(String method, String authorizationId, String captureId,
                        String gatewayTransactionId, Money authorizedAmount, Money capturedAmount,
                        Money refundedAmount, Instant authorizationTime, Instant captureTime,
                        PaymentStatus status) {
        this.method = method;
        this.authorizationId = authorizationId;
        this.captureId = captureId;
        this.gatewayTransactionId = gatewayTransactionId;
        this.authorizedAmount = authorizedAmount;
        this.capturedAmount = capturedAmount;
        this.refundedAmount = refundedAmount;
        this.authorizationTime = authorizationTime;
        this.captureTime = captureTime;
        this.status = status;
    }

    public static PaymentInfo fromAuthorization(PaymentAuthorization authorization) {
        return new PaymentInfo(authorization.method(), authorization.authorizationId(), null,
                authorization.gatewayTransactionId(), authorization.amount(), Money.zero(),
                Money.zero(), authorization.authorizedAt(), null, PaymentStatus.AUTHORIZED);
    }

    public PaymentInfo withCapture(PaymentCapture capture) {
        return new PaymentInfo(method, authorizationId, capture.captureId(),
                capture.gatewayTransactionId(), authorizedAmount, capture.amount(), refundedAmount,
                authorizationTime, capture.capturedAt(), PaymentStatus.CAPTURED);
    }

    public PaymentInfo withRefund(Money amount, Instant refundedAt) {
        return new PaymentInfo(method, authorizationId, captureId, gatewayTransactionId,
                authorizedAmount, capturedAmount, refundedAmount.add(amount), authorizationTime,
                captureTime, PaymentStatus.REFUNDED);
    }

    /** Restores a persisted snapshot; bypasses the payment flow guards. */
    public static PaymentInfo restore(String method, String authorizationId, String captureId,
                                      String gatewayTransactionId, Money authorizedAmount,
                                      Money capturedAmount, Money refundedAmount,
                                      Instant authorizationTime, Instant captureTime,
                                      PaymentStatus status) {
        return new PaymentInfo(method, authorizationId, captureId, gatewayTransactionId,
                authorizedAmount == null ? Money.zero() : authorizedAmount,
                capturedAmount == null ? Money.zero() : capturedAmount,
                refundedAmount == null ? Money.zero() : refundedAmount,
                authorizationTime, captureTime, status);
    }

    public boolean isCaptured() {
        return status == PaymentStatus.CAPTURED || status == PaymentStatus.REFUNDED;
    }

    /** Remaining captured balance that can still be refunded. */
    public Money getRefundableAmount() {
        return new Money(capturedAmount.getAmount().subtract(refundedAmount.getAmount()));
    }

    /**
     * Whether another refund of the given amount is possible without exceeding
     * the captured amount (idempotency: already-refunded money is not refunded
     * again).
     */
    public boolean canRefund(Money amount) {
        if (amount == null || amount.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return refundedAmount.add(amount).getAmount().compareTo(capturedAmount.getAmount()) <= 0;
    }

    public String getMethod() { return method; }
    public String getAuthorizationId() { return authorizationId; }
    public String getCaptureId() { return captureId; }
    public String getGatewayTransactionId() { return gatewayTransactionId; }
    public Money getAuthorizedAmount() { return authorizedAmount; }
    public Money getCapturedAmount() { return capturedAmount; }
    public Money getRefundedAmount() { return refundedAmount; }
    public Instant getAuthorizationTime() { return authorizationTime; }
    public Instant getCaptureTime() { return captureTime; }
    public PaymentStatus getStatus() { return status; }
}
