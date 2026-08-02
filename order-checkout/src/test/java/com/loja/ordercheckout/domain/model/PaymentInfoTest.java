package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentInfoTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    private static Money money(String amount) {
        return new Money(new BigDecimal(amount));
    }

    private PaymentInfo capturedInfo() {
        PaymentInfo info = PaymentInfo.fromAuthorization(
                new PaymentAuthorization("card", "auth-1", money("100.00"), "tx-1", NOW));
        return info.withCapture(new PaymentCapture("auth-1", "capture-1", money("100.00"), "tx-1", NOW));
    }

    @Test
    void getRefundableAmount_afterCapture_returnsCapturedAmount() {
        assertThat(capturedInfo().getRefundableAmount()).isEqualTo(money("100.00"));
    }

    @Test
    void getRefundableAmount_afterPartialRefund_returnsRemainingBalance() {
        PaymentInfo info = capturedInfo().withRefund(money("30.00"), NOW);
        assertThat(info.getRefundableAmount()).isEqualTo(money("70.00"));
    }

    @Test
    void getRefundableAmount_whenOnlyAuthorized_isZero() {
        PaymentInfo info = PaymentInfo.fromAuthorization(
                new PaymentAuthorization("card", "auth-1", money("100.00"), "tx-1", NOW));
        assertThat(info.getRefundableAmount()).isEqualTo(Money.zero());
    }

    @Test
    void isCaptured_afterCaptureOrRefund_isTrue() {
        assertThat(capturedInfo().isCaptured()).isTrue();
        assertThat(capturedInfo().withRefund(money("10.00"), NOW).isCaptured()).isTrue();
        PaymentInfo authorized = PaymentInfo.fromAuthorization(
                new PaymentAuthorization("card", "auth-1", money("100.00"), "tx-1", NOW));
        assertThat(authorized.isCaptured()).isFalse();
    }
}
