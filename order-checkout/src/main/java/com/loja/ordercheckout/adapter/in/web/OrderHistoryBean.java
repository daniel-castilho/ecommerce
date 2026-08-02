package com.loja.ordercheckout.adapter.in.web;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.exception.InvalidOrderStateException;
import com.loja.ordercheckout.domain.exception.OrderConcurrentModificationException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.port.in.CustomerOrderHistoryUseCase;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Thin JSF adapter for the customer order history: a paginated list of the
 * caller's orders plus a detail view with cancel and refund actions. All
 * business rules live in {@link CustomerOrderHistoryUseCase}; this bean only
 * maps view events to use-case calls and translates exceptions into messages.
 */
@Named("orderHistoryBean")
@ViewScoped
public class OrderHistoryBean implements Serializable {

    private static final int PAGE_SIZE = PageResult.DEFAULT_PAGE_SIZE;
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** Prefix for the carrier tracking page; the tracking code is appended. */
    private static final String TRACKING_URL_PREFIX = "https://www.linkcorreios.com.br/?id=";

    @Inject
    private CustomerOrderHistoryUseCase orderHistory;

    @Inject
    private SessionPort session;

    private int page;
    private PageResult<Order> result;
    private Order selectedOrder;
    private String refundReason;

    @PostConstruct
    void init() {
        String orderId = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap().get("orderId");
        if (orderId != null && !orderId.isBlank()) {
            selectedOrder = orderHistory.findById(orderId, currentUserId()).orElse(null);
        } else {
            refresh();
        }
    }

    private String currentUserId() {
        return currentUser()
                .map(User::getId)
                .orElse(null);
    }

    private Optional<User> currentUser() {
        return session.getCurrentUser();
    }

    public boolean isLoggedIn() {
        return currentUser().isPresent();
    }

    private void refresh() {
        String userId = currentUserId();
        result = userId == null ? null : orderHistory.listByCustomer(userId, page, PAGE_SIZE);
    }

    public void nextPage() {
        if (hasNextPage()) {
            page++;
            refresh();
        }
    }

    public void previousPage() {
        if (hasPreviousPage()) {
            page--;
            refresh();
        }
    }

    public boolean hasNextPage() {
        return result != null && page + 1 < result.totalPages();
    }

    public boolean hasPreviousPage() {
        return result != null && page > 0;
    }

    public int getTotalPages() {
        return result != null ? Math.max(1, result.totalPages()) : 1;
    }

    public long getTotalElements() {
        return result != null ? result.totalElements() : 0;
    }

    // ---- actions ----

    public String cancel(Order order) {
        try {
            orderHistory.cancel(order.getId(), currentUserId());
            addMessage(FacesMessage.SEVERITY_INFO, "Order cancelled",
                    "Order " + order.getId() + " was cancelled");
        } catch (IllegalArgumentException | InvalidOrderStateException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Cancel failed", e.getMessage());
        } catch (OrderConcurrentModificationException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Order was updated by another process",
                    "Please reload the page and try again");
        }
        if (selectedOrder != null && selectedOrder.getId().equals(order.getId())) {
            selectedOrder = orderHistory.findById(order.getId(), currentUserId()).orElse(null);
        }
        refresh();
        return null;
    }

    public String startRefund(Order order) {
        refundReason = "";
        return "/order-checkout/order-detail.xhtml?faces-redirect=true&orderId=" + order.getId();
    }

    public String submitRefund() {
        Order order = selectedOrder;
        if (order == null) {
            return null;
        }
        try {
            orderHistory.requestRefund(order.getId(), currentUserId(), refundReason);
            selectedOrder = orderHistory.findById(order.getId(), currentUserId()).orElse(null);
            addMessage(FacesMessage.SEVERITY_INFO, "Refund requested",
                    "Order " + order.getId() + " was refunded");
        } catch (IllegalArgumentException | InvalidOrderStateException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Refund failed", e.getMessage());
        } catch (OrderConcurrentModificationException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Order was updated by another process",
                    "Please reload the page and try again");
        }
        return null;
    }

    // ---- view rules ----

    /** Cancel is offered for orders not yet in the fulfilment pipeline. */
    public boolean canCancel(Order order) {
        OrderStatus status = order.getStatus();
        return status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED;
    }

    /** Refund is offered for captured orders in the fulfilment pipeline. */
    public boolean canRequestRefund(Order order) {
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.CONFIRMED && status != OrderStatus.PROCESSING
                && status != OrderStatus.SHIPPED) {
            return false;
        }
        return order.getPaymentInfo() != null && order.getPaymentInfo().isCaptured();
    }

    public String formatDate(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    public String trackingUrl(Order order) {
        String trackingNumber = order.getTrackingNumber();
        return trackingNumber == null ? "" : TRACKING_URL_PREFIX + trackingNumber;
    }

    private static void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(severity, summary, detail));
    }

    // ---- accessors ----

    public List<Order> getOrders() {
        return result != null ? result.items() : List.of();
    }

    public Order getSelectedOrder() { return selectedOrder; }
    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public int getPage() { return page; }
}
