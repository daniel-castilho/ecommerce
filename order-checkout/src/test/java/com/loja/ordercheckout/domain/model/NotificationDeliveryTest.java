package com.loja.ordercheckout.domain.model;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryTest {

    @Test
    void backoffDelayFor_firstFailureIsThirtySeconds() {
        assertThat(NotificationDelivery.backoffDelayFor(1)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void backoffDelayFor_secondFailureIsTwoMinutes() {
        assertThat(NotificationDelivery.backoffDelayFor(2)).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void backoffDelayFor_laterFailuresUseCap() {
        assertThat(NotificationDelivery.backoffDelayFor(3)).isEqualTo(Duration.ofMinutes(5));
        assertThat(NotificationDelivery.backoffDelayFor(9)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void create_startsPendingWithZeroAttemptsAndImmediateGate() {
        NotificationDelivery delivery = NotificationDelivery.create(
                "ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1", NotificationChannel.EMAIL,
                "buyer@example.com", "Subject", "Body");

        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(delivery.getAttemptCount()).isZero();
        assertThat(delivery.getNextAttemptAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void markFailed_bumpsAttemptAndGatesNextTryBehindBackoff() {
        NotificationDelivery delivery = NotificationDelivery.create(
                "ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1", NotificationChannel.EMAIL,
                "buyer@example.com", "Subject", "Body");

        delivery.markFailed("boom");

        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getErrorMessage()).isEqualTo("boom");
        assertThat(delivery.getNextAttemptAt()).isAfter(Instant.now().plusSeconds(25));
    }
}
