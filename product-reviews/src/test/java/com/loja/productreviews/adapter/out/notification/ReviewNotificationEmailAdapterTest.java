package com.loja.productreviews.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import com.loja.ordercheckout.domain.port.out.NotificationDeliveryLogPort;
import com.loja.productreviews.domain.model.Rating;
import com.loja.productreviews.domain.model.Review;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.in.FindUserUseCase;

class ReviewNotificationEmailAdapterTest {

    private static final String AUTHOR_ID = "u-1";
    private static final String AUTHOR_EMAIL = "author@example.com";

    private static Review review(String reviewId, ReviewStatus status) {
        return Review.reconstitute(reviewId, "p-1", AUTHOR_ID, Rating.of(5),
                "Great product", "Loved it", true, Instant.parse("2026-08-01T10:00:00Z"),
                status, status == ReviewStatus.REJECTED ? Instant.now() : null,
                status == ReviewStatus.REJECTED ? "Off-topic" : null);
    }

    private static User user(boolean notificationsEnabled) {
        return User.create(new Email(AUTHOR_EMAIL), UserPassword.fromHash("argon2id-placeholder"),
                new UserProfile("Ana", "Souza", "11999999999", "pt", notificationsEnabled));
    }

    @Test
    void notifyApproved_claimsPendingRowWithEmailSnapshot() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById(AUTHOR_ID)).thenReturn(Optional.of(user(true)));
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        when(log.claim(any(NotificationDelivery.class))).thenReturn(true);
        ReviewNotificationEmailAdapter adapter = new ReviewNotificationEmailAdapter(users, log);

        assertThatCode(() -> adapter.notifyApproved(review("r-1", ReviewStatus.PENDING)))
                .doesNotThrowAnyException();

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(log).claim(captor.capture());
        NotificationDelivery claimed = captor.getValue();
        assertThat(claimed.getIdempotencyKey()).isEqualTo("REVIEW_APPROVED:r-1");
        assertThat(claimed.getEventType()).isEqualTo("REVIEW_APPROVED");
        assertThat(claimed.getAggregateId()).isEqualTo("r-1");
        assertThat(claimed.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(claimed.getRecipientEmail()).isEqualTo(AUTHOR_EMAIL);
        assertThat(claimed.getSubject()).isEqualTo("Your review has been approved");
        assertThat(claimed.getBody()).contains("Great product");
        assertThat(claimed.getBodyHtml())
                .contains("Great product")
                .contains("You\u2019re receiving this because you wrote a review at Loja.");
    }

    @Test
    void notifyRejected_claimsPendingRowWithRejectionSnapshot() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById(AUTHOR_ID)).thenReturn(Optional.of(user(true)));
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        when(log.claim(any(NotificationDelivery.class))).thenReturn(true);
        ReviewNotificationEmailAdapter adapter = new ReviewNotificationEmailAdapter(users, log);

        adapter.notifyRejected(review("r-1", ReviewStatus.REJECTED), "Off-topic");

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(log).claim(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("REVIEW_REJECTED:r-1");
        assertThat(captor.getValue().getEventType()).isEqualTo("REVIEW_REJECTED");
        assertThat(captor.getValue().getSubject()).isEqualTo("Your review was not approved");
        assertThat(captor.getValue().getBody()).contains("Reason: Off-topic");
    }

    @Test
    void notifyApproved_notificationsDisabled_skipsWithoutClaiming() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById(AUTHOR_ID)).thenReturn(Optional.of(user(false)));
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        ReviewNotificationEmailAdapter adapter = new ReviewNotificationEmailAdapter(users, log);

        assertThatCode(() -> adapter.notifyApproved(review("r-1", ReviewStatus.PENDING)))
                .doesNotThrowAnyException();

        verify(log, never()).claim(any(NotificationDelivery.class));
    }

    @Test
    void notifyApproved_duplicateEvent_skipsEnqueue() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById(AUTHOR_ID)).thenReturn(Optional.of(user(true)));
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        when(log.claim(any(NotificationDelivery.class))).thenReturn(false);
        ReviewNotificationEmailAdapter adapter = new ReviewNotificationEmailAdapter(users, log);

        assertThatCode(() -> adapter.notifyApproved(review("r-1", ReviewStatus.PENDING)))
                .doesNotThrowAnyException();

        verify(log).claim(any(NotificationDelivery.class));
        verify(log, never()).updateStatus(any(), any(), any());
    }

    @Test
    void notifyApproved_authorNotFound_skipsWithoutClaiming() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById(AUTHOR_ID)).thenReturn(Optional.empty());
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        ReviewNotificationEmailAdapter adapter = new ReviewNotificationEmailAdapter(users, log);

        adapter.notifyApproved(review("r-1", ReviewStatus.PENDING));

        verify(log, never()).claim(any(NotificationDelivery.class));
    }

    @Test
    void notifyApproved_authorLookupFailure_skipsWithoutClaiming() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById(AUTHOR_ID)).thenThrow(new RuntimeException("db down"));
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        ReviewNotificationEmailAdapter adapter = new ReviewNotificationEmailAdapter(users, log);

        assertThatCode(() -> adapter.notifyApproved(review("r-1", ReviewStatus.PENDING)))
                .doesNotThrowAnyException();

        verify(log, never()).claim(any(NotificationDelivery.class));
    }
}