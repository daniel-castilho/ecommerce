package com.loja.productreviews.domain.port.out;

import com.loja.productreviews.domain.model.Review;

/**
 * Outbound port for notifying the author about a moderation decision.
 *
 * <p>The application layer calls it right after persisting an approved or
 * rejected review; the adapter renders and enqueues the email without ever
 * throwing, so moderation never fails because of notification delivery.
 */
public interface ReviewNotificationPort {

    /** Notify the author that their review was approved and is now public. */
    void notifyApproved(Review review);

    /** Notify the author that their review was rejected, with the given reason. */
    void notifyRejected(Review review, String rejectionReason);
}