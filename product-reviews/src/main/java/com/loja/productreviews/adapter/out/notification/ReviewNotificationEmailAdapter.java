package com.loja.productreviews.adapter.out.notification;

import com.loja.ordercheckout.domain.model.NotificationChannel;
import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.port.out.NotificationDeliveryLogPort;
import com.loja.productreviews.domain.model.Review;
import com.loja.productreviews.domain.port.out.ReviewNotificationPort;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link ReviewNotificationPort} that enqueues a transactional
 * outbox entry instead of sending synchronously. Each moderation event is claimed
 * with an idempotency key ({@code REVIEW_APPROVED:{reviewId}} /
 * {@code REVIEW_REJECTED:{reviewId}}) and the rendered email payload (recipient,
 * subject, body) is snapshotted inside the moderation transaction;
 * {@code order-checkout}'s {@code NotificationOutboxProcessor} dispatches the
 * emails on its scheduled poll — no SMTP work happens on the moderation thread.
 *
 * <p>Respects {@code UserProfile.notificationsEnabled}. The recipient address is
 * resolved from the author's account; when the author cannot be resolved the
 * notification is skipped (best-effort, never throws).
 */
@ApplicationScoped
public class ReviewNotificationEmailAdapter implements ReviewNotificationPort {

    private static final Logger LOG = Logger.getLogger(ReviewNotificationEmailAdapter.class.getName());
    private static final NotificationChannel CHANNEL = NotificationChannel.EMAIL;

    @Inject
    private FindUserUseCase findUserUseCase;

    @Inject
    private NotificationDeliveryLogPort deliveryLog;

    protected ReviewNotificationEmailAdapter() {
    }

    ReviewNotificationEmailAdapter(FindUserUseCase findUserUseCase,
                                   NotificationDeliveryLogPort deliveryLog) {
        this.findUserUseCase = findUserUseCase;
        this.deliveryLog = deliveryLog;
    }

    @Override
    public void notifyApproved(Review review) {
        enqueue(review, ReviewNotificationMessageBuilder.approved(review), "REVIEW_APPROVED");
    }

    @Override
    public void notifyRejected(Review review, String rejectionReason) {
        enqueue(review, ReviewNotificationMessageBuilder.rejected(review, rejectionReason),
                "REVIEW_REJECTED");
    }

    private void enqueue(Review review, ReviewNotificationMessageBuilder.Draft draft, String event) {
        Optional<User> author = findAuthor(review.getAuthorId());
        if (author.isEmpty()) {
            LOG.fine(() -> "Skipping " + event + " email for review " + review.getId()
                    + ": author not found");
            return;
        }
        User user = author.get();
        if (user.getProfile() != null && !user.getProfile().isNotificationsEnabled()) {
            LOG.fine(() -> "Skipping " + event + " email for review " + review.getId()
                    + ": notifications disabled");
            return;
        }
        String idempotencyKey = event + ":" + review.getId();
        NotificationDelivery delivery = NotificationDelivery.create(idempotencyKey, event,
                review.getId(), CHANNEL, user.getEmail().getValue(), draft.subject(), draft.body(),
                draft.htmlBody());
        if (deliveryLog.claim(delivery)) {
            LOG.info("Enqueued " + event + " email for review " + review.getId());
        } else {
            LOG.fine(() -> "Skipping duplicate " + event + " notification for review "
                    + review.getId() + " (already enqueued)");
        }
    }

    private Optional<User> findAuthor(String authorId) {
        try {
            return findUserUseCase.findById(authorId);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Author lookup failed for review author " + authorId
                    + "; skipping notification", e);
            return Optional.empty();
        }
    }
}