package com.loja.productreviews.adapter.out.notification;

import com.loja.productreviews.domain.model.Review;

/**
 * Builds email drafts (subject + plain body + HTML body) for review moderation
 * notifications. The HTML variant carries the same facts as the text body and uses
 * a single inline-styled layout with the admin design-token hex values (see
 * {@code docs/design-system.md}; no runtime CSS scraping, no external stylesheets,
 * no JS) — mirroring {@code order-checkout}'s {@code OrderNotificationMessageBuilder}.
 */
final class ReviewNotificationMessageBuilder {

    /** Immutable subject / plain-text body / HTML body triple ready for Jakarta Mail. */
    record Draft(String subject, String body, String htmlBody) {}

    private static final String SIGNATURE = "\n\nBest regards,\nThe Loja Team";
    private static final String FOOTER =
            "<p style=\"margin:24px 0 0;font-size:12px;color:#95a5a6;\">"
                    + "You\u2019re receiving this because you wrote a review at Loja.</p>";

    private ReviewNotificationMessageBuilder() {
    }

    static Draft approved(Review review) {
        return new Draft(
                "Your review has been approved",
                "Hi,\n\n" + reviewPhrase(review)
                        + " has been approved and is now visible on the product page." + SIGNATURE,
                htmlLayout("Your review has been approved",
                        paragraph("Hi,")
                                + paragraph(reviewHtml(review)
                                + " has been approved and is now visible on the product page.")));
    }

    static Draft rejected(Review review, String rejectionReason) {
        return new Draft(
                "Your review was not approved",
                "Hi,\n\nWe could not approve " + reviewPhrase(review) + ".\n\n"
                        + "Review: " + reviewLine(review)
                        + "\nReason: " + rejectionReason
                        + "\n\nIf you have any questions, our support team is here to help." + SIGNATURE,
                htmlLayout("Your review was not approved",
                        paragraph("Hi,")
                                + paragraph("We could not approve " + reviewHtml(review) + ".")
                                + paragraph("Review: " + reviewLineHtml(review))
                                + paragraph("Reason: <strong>" + escape(rejectionReason) + "</strong>")
                                + paragraph("If you have any questions, our support team is here to help.")));
    }

    /** "your review \"Great title\"" when a title exists, "your review" otherwise. */
    private static String reviewPhrase(Review review) {
        if (review.getTitle() == null || review.getTitle().isBlank()) {
            return "your review";
        }
        return "your review \u201c" + review.getTitle() + "\u201d";
    }

    private static String reviewLine(Review review) {
        String title = review.getTitle() != null ? review.getTitle() : "(no title)";
        return "\u201c" + title + "\u201d, rated " + review.getRating().getValue() + " out of 5";
    }

    private static String reviewHtml(Review review) {
        if (review.getTitle() == null || review.getTitle().isBlank()) {
            return "your review";
        }
        return "your review \u201c<strong>" + escape(review.getTitle()) + "</strong>\u201d";
    }

    private static String reviewLineHtml(Review review) {
        String title = review.getTitle() != null ? review.getTitle() : "(no title)";
        return "\u201c" + escape(title) + "\u201d, rated " + review.getRating().getValue() + " out of 5";
    }

    /** Shared HTML skeleton: token header strip, title, content block, footer line. */
    private static String htmlLayout(String title, String content) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head><meta charset=\"utf-8\"/></head>\n"
                + "<body style=\"margin:0;padding:0;background-color:#ecf0f1;"
                + "font-family:'Segoe UI','Helvetica Neue',Arial,sans-serif;color:#2c3e50;\">\n"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background-color:#ecf0f1;padding:16px;\">\n"
                + "<tr><td align=\"center\">\n"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:600px;background-color:#ffffff;border:1px solid #bdc3c7;"
                + "border-radius:10px;\">\n"
                + "<tr><td style=\"background-color:#3498db;padding:16px 24px;"
                + "border-radius:10px 10px 0 0;\">\n"
                + "<span style=\"color:#ffffff;font-size:18px;font-weight:bold;\">Loja</span>\n"
                + "</td></tr>\n"
                + "<tr><td style=\"padding:24px;\">\n"
                + "<h1 style=\"margin:0 0 16px;font-size:20px;color:#2c3e50;\">"
                + escape(title) + "</h1>\n"
                + content + "\n"
                + FOOTER + "\n"
                + "</td></tr>\n"
                + "</table>\n"
                + "</td></tr>\n"
                + "</table>\n"
                + "</body>\n"
                + "</html>";
    }

    private static String paragraph(String innerHtml) {
        return "<p style=\"margin:0 0 12px;\">" + innerHtml + "</p>";
    }

    /** Escapes HTML-significant characters in user-derived strings. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}