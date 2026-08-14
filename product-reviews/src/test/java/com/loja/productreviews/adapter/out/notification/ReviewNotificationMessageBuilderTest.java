package com.loja.productreviews.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.loja.productreviews.domain.model.Rating;
import com.loja.productreviews.domain.model.Review;
import com.loja.productreviews.domain.model.ReviewStatus;

class ReviewNotificationMessageBuilderTest {

    private static Review review(String id, int rating, String title, String body) {
        return Review.reconstitute(id, "p-1", "u-1", Rating.of(rating),
                title, body, true, Instant.parse("2026-08-01T10:00:00Z"),
                ReviewStatus.PENDING, null, null);
    }

    @Test
    void approved_subjectAndBodyMentionApprovalAndTitle() {
        ReviewNotificationMessageBuilder.Draft draft = ReviewNotificationMessageBuilder.approved(
                review("r-1", 5, "Great product", "Loved it"));

        assertThat(draft.subject()).isEqualTo("Your review has been approved");
        assertThat(draft.body())
                .contains("Great product")
                .contains("approved")
                .contains("visible on the product page");
        assertThat(draft.htmlBody())
                .contains("Great product")
                .contains("approved")
                .contains("You\u2019re receiving this because you wrote a review at Loja.");
    }

    @Test
    void approved_withoutTitle_keepsGenericWording() {
        ReviewNotificationMessageBuilder.Draft draft = ReviewNotificationMessageBuilder.approved(
                review("r-1", 4, null, null));

        assertThat(draft.body())
                .contains("your review")
                .doesNotContain("\u201c");
    }

    @Test
    void rejected_bodyIncludesReasonAndReviewReference() {
        ReviewNotificationMessageBuilder.Draft draft = ReviewNotificationMessageBuilder.rejected(
                review("r-1", 2, "Not impressed", "Too expensive"), "Off-topic");

        assertThat(draft.subject()).isEqualTo("Your review was not approved");
        assertThat(draft.body())
                .contains("Not impressed")
                .contains("rated 2 out of 5")
                .contains("Reason: Off-topic");
        assertThat(draft.htmlBody())
                .contains("Not impressed")
                .contains("Reason: <strong>Off-topic</strong>")
                .doesNotContain("<script>");
    }

    @Test
    void htmlBody_escapesHtmlSignificantCharactersInUserSuppliedStrings() {
        ReviewNotificationMessageBuilder.Draft draft = ReviewNotificationMessageBuilder.rejected(
                review("r-1", 1, "Bad <script>alert(1)</script>", "x"),
                "Offensive & <b>bold</b>");

        assertThat(draft.htmlBody())
                .contains("Bad &lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("Offensive &amp; &lt;b&gt;bold&lt;/b&gt;")
                .doesNotContain("<script>");
    }
}