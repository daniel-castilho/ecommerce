package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.shared.domain.Money;

import java.util.stream.Collectors;

/**
 * Builds email drafts (subject + plain body + HTML body) for order notifications. The
 * HTML variant carries the same facts as the text body and uses a single inline-styled
 * layout with the admin design-token hex values (see {@code docs/design-system.md}; no
 * runtime CSS scraping, no external stylesheets, no JS).
 */
final class OrderNotificationMessageBuilder {

    /** Immutable subject / plain-text body / HTML body triple ready for Jakarta Mail. */
    record Draft(String subject, String body, String htmlBody) {}

    private static final String SIGNATURE = "\n\nBest regards,\nThe Loja Team";
    private static final String FOOTER =
            "<p style=\"margin:24px 0 0;font-size:12px;color:#95a5a6;\">"
                    + "You\u2019re receiving this because you placed an order at Loja.</p>";

    private OrderNotificationMessageBuilder() {
    }

    static Draft orderConfirmed(Order order) {
        String id = order.getId();
        return new Draft(
                "Order " + id + " confirmed",
                "Hi,\n\nYour order " + id + " has been confirmed.\n\n"
                        + items(order)
                        + "\n\nTotal: " + format(order.getTotal()) + SIGNATURE,
                htmlLayout("Order " + escape(id) + " confirmed",
                        paragraph("Hi,")
                                + paragraph("Your order <strong>" + escape(id)
                                + "</strong> has been confirmed.")
                                + itemsTable(order)));
    }

    static Draft orderShipped(Order order, String trackingNumber) {
        String id = order.getId();
        return new Draft(
                "Order " + id + " shipped",
                "Hi,\n\nYour order " + id + " is on its way.\n\n"
                        + items(order)
                        + "\n\nTracking number: " + trackingNumber + SIGNATURE,
                htmlLayout("Order " + escape(id) + " shipped",
                        paragraph("Hi,")
                                + paragraph("Your order <strong>" + escape(id)
                                + "</strong> is on its way.")
                                + itemsTable(order)
                                + paragraph("Tracking number: <strong>"
                                + escape(trackingNumber) + "</strong>")));
    }

    static Draft refundRequested(Order order, String reason) {
        String id = order.getId();
        return new Draft(
                "Refund requested for order " + id,
                "Hi,\n\nA refund has been requested for order " + id + ".\n\n"
                        + "Reason: " + reason
                        + "\n\nWe will review it shortly." + SIGNATURE,
                htmlLayout("Refund requested for order " + escape(id),
                        paragraph("Hi,")
                                + paragraph("A refund has been requested for order "
                                + "<strong>" + escape(id) + "</strong>.")
                                + paragraph("Reason: " + escape(reason))
                                + paragraph("We will review it shortly.")));
    }

    static Draft refundApproved(Order order, RefundRequest request) {
        String id = order.getId();
        return new Draft(
                "Refund approved for order " + id,
                "Hi,\n\nYour refund for order " + id + " has been approved.\n\n"
                        + "Amount: " + format(request.getAmount())
                        + "\nReason: " + request.getReason() + SIGNATURE,
                htmlLayout("Refund approved for order " + escape(id),
                        paragraph("Hi,")
                                + paragraph("Your refund for order <strong>" + escape(id)
                                + "</strong> has been approved.")
                                + paragraph("Amount: <strong>"
                                + format(request.getAmount()) + "</strong>")
                                + paragraph("Reason: " + escape(request.getReason()))));
    }

    static Draft refundRejected(Order order, RefundRequest request) {
        String id = order.getId();
        return new Draft(
                "Refund rejected for order " + id,
                "Hi,\n\nYour refund for order " + id + " could not be approved.\n\n"
                        + "Reason: " + request.getReason()
                        + "\n\nRejection detail: " + request.getRejectionReason() + SIGNATURE,
                htmlLayout("Refund rejected for order " + escape(id),
                        paragraph("Hi,")
                                + paragraph("Your refund for order <strong>" + escape(id)
                                + "</strong> could not be approved.")
                                + paragraph("Reason: " + escape(request.getReason()))
                                + paragraph("Rejection detail: "
                                + escape(request.getRejectionReason()))));
    }

    private static String items(Order order) {
        return order.getItems().stream()
                .map(OrderNotificationMessageBuilder::item)
                .collect(Collectors.joining("\n"));
    }

    private static String item(OrderLine line) {
        return "- " + line.getProductName() + " x " + line.getQuantity()
                + " (" + format(line.getLineTotal()) + ")";
    }

    private static String format(Money money) {
        return "$" + money.getAmount();
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

    /** Reuses plain-text rows; each item renders name x qty with its line total. */
    private static String itemsTable(Order order) {
        StringBuilder table = new StringBuilder();
        table.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"border-collapse:collapse;margin:4px 0 16px;\">\n");
        for (OrderLine line : order.getItems()) {
            table.append("<tr>\n")
                    .append("<td style=\"padding:6px 0;border-bottom:1px solid #bdc3c7;\">")
                    .append(escape(line.getProductName()))
                    .append(" x ").append(line.getQuantity())
                    .append("</td>\n")
                    .append("<td style=\"padding:6px 0;border-bottom:1px solid #bdc3c7;"
                            + "text-align:right;\">")
                    .append(format(line.getLineTotal()))
                    .append("</td>\n</tr>\n");
        }
        table.append("<tr>\n")
                .append("<td style=\"padding:8px 0;font-weight:bold;\">Total</td>\n")
                .append("<td style=\"padding:8px 0;font-weight:bold;text-align:right;\">")
                .append(format(order.getTotal()))
                .append("</td>\n</tr>\n");
        table.append("</table>");
        return table.toString();
    }

    /** Escapes HTML-significant characters in user- or catalog-derived strings. */
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