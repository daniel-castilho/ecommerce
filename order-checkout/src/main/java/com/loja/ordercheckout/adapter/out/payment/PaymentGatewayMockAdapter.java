package com.loja.ordercheckout.adapter.out.payment;

import com.loja.ordercheckout.domain.exception.PaymentFailedException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.PaymentAuthorization;
import com.loja.ordercheckout.domain.model.PaymentCapture;
import com.loja.ordercheckout.domain.model.PaymentMethod;
import com.loja.ordercheckout.domain.model.PaymentRefund;
import com.loja.ordercheckout.domain.port.out.PaymentGatewayPort;
import com.loja.shared.domain.Money;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * FOR LOCAL DEV ONLY.
 *
 * Mock payment gateway that returns fabricated authorization/capture/refund results
 * without contacting any real processor. Useful for running the checkout flow locally
 * and in tests. Never use in production — real providers are wired via
 * {@code PaymentGatewayStripeAdapter} / {@code PaymentGatewayPagSeguroAdapter}.
 *
 * <p>Failure behavior is configurable via the {@value #FAIL_MODE_PROPERTY} system
 * property (or {@link #setFailMode(boolean)}): when enabled, every operation throws a
 * {@link PaymentFailedException} with a user-friendly message. Every call is recorded
 * in an in-memory audit trail (dev debugging) — the tokenized card reference is never
 * logged.
 */
@ApplicationScoped
public class PaymentGatewayMockAdapter implements PaymentGatewayPort {

    public static final String FAIL_MODE_PROPERTY = "payment.gateway.mock.fail";

    private static final Logger LOGGER = Logger.getLogger(PaymentGatewayMockAdapter.class.getName());

    private volatile boolean failMode;
    private final Map<String, Money> authorizedAmounts = new HashMap<>();
    private final List<String> auditTrail = Collections.synchronizedList(new ArrayList<>());

    public PaymentGatewayMockAdapter() {
        this(Boolean.parseBoolean(System.getProperty(FAIL_MODE_PROPERTY)));
    }

    public PaymentGatewayMockAdapter(boolean failMode) {
        this.failMode = failMode;
    }

    public void setFailMode(boolean failMode) {
        this.failMode = failMode;
    }

    /** Unmodifiable snapshot of the audit trail entries (for tests and dev debugging). */
    public List<String> getAuditEntries() {
        synchronized (auditTrail) {
            return List.copyOf(auditTrail);
        }
    }

    @Override
    public PaymentAuthorization authorize(Order order, PaymentMethod method) {
        if (method == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        audit("authorize", "order=" + order.getId() + " method=" + method.method()
                + " amount=" + order.getTotal());
        if (failMode) {
            throw new PaymentFailedException(
                    "Payment authorization was declined by the provider. Please try again or use a different card.");
        }
        String authorizationId = "mock-auth-" + UUID.randomUUID();
        authorizedAmounts.put(authorizationId, order.getTotal());
        return new PaymentAuthorization(method.method(), authorizationId, order.getTotal(),
                "mock-tx-" + UUID.randomUUID(), Instant.now());
    }

    @Override
    public PaymentCapture capture(String authorizationId) {
        audit("capture", "authorizationId=" + authorizationId);
        if (failMode) {
            throw new PaymentFailedException("Payment capture failed. The funds could not be captured.");
        }
        if (authorizationId == null || authorizationId.isBlank()) {
            throw new PaymentFailedException("Capture requires a valid authorization id.");
        }
        Money amount = authorizedAmounts.get(authorizationId);
        if (amount == null) {
            throw new PaymentFailedException("Unknown authorization id; the funds cannot be captured.");
        }
        return new PaymentCapture(authorizationId, "mock-cap-" + UUID.randomUUID(), amount,
                "mock-tx-" + UUID.randomUUID(), Instant.now());
    }

    @Override
    public PaymentRefund refund(String captureId, Money amount) {
        audit("refund", "captureId=" + captureId + " amount=" + amount);
        if (failMode) {
            throw new PaymentFailedException("Payment refund failed. Please try again later.");
        }
        if (captureId == null || captureId.isBlank()) {
            throw new PaymentFailedException("Refund requires a valid capture id.");
        }
        if (amount == null || amount.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentFailedException("Refund amount must be greater than zero.");
        }
        return new PaymentRefund(captureId, "mock-ref-" + UUID.randomUUID(), amount,
                "mock-tx-" + UUID.randomUUID(), Instant.now());
    }

    private void audit(String operation, String detail) {
        String entry = "[" + Instant.now() + "] " + operation + " " + detail;
        LOGGER.info(entry);
        auditTrail.add(entry);
    }
}
