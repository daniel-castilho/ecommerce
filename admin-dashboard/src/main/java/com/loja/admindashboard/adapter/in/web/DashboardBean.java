package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.loja.admindashboard.application.dto.DashboardSummaryDTO;
import com.loja.admindashboard.domain.port.in.DashboardMetricsUseCase;
import com.loja.admindashboard.domain.port.in.OrderListUseCase;
import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.shared.domain.Money;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("dashboardBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class DashboardBean implements Serializable {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final int PAGE_SIZE = 5;

    @Inject
    private DashboardMetricsUseCase dashboardMetricsUseCase;

    @Inject
    private OrderListUseCase orderListUseCase;

    @Inject
    private com.loja.admindashboard.domain.port.in.UpdateOrderStatusUseCase updateOrderStatusUseCase;

    void setDashboardMetricsUseCase(DashboardMetricsUseCase dashboardMetricsUseCase) {
        this.dashboardMetricsUseCase = dashboardMetricsUseCase;
    }

    void setOrderListUseCase(OrderListUseCase orderListUseCase) {
        this.orderListUseCase = orderListUseCase;
    }

    void setUpdateOrderStatusUseCase(com.loja.admindashboard.domain.port.in.UpdateOrderStatusUseCase updateOrderStatusUseCase) {
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
    }

    private DashboardSummaryDTO summary;
    private PageResult<Order> orderPage = new PageResult<>(List.of(), 0L, 0, PAGE_SIZE);
    private OrderStatus selectedStatus;
    private OrderStatus statusToApply;
    private String selectedOrderId;
    private String trackingNumber;
    private int page;

    @PostConstruct
    void load() {
        summary = dashboardMetricsUseCase.getSummary();
        reloadOrders();
    }

    public DashboardSummaryDTO getSummary() {
        return summary;
    }

    public long getTotalUsers() {
        return summary == null ? 0L : summary.totalUsers();
    }

    public long getTotalProducts() {
        return summary == null ? 0L : summary.totalProducts();
    }

    public long getTotalOrders() {
        return summary == null ? 0L : summary.totalOrders();
    }

    public long getNewCustomersToday() {
        return summary == null ? 0L : summary.newCustomersToday();
    }

    public long getNewCustomersThisMonth() {
        return summary == null ? 0L : summary.newCustomersThisMonth();
    }

    public long getOrdersToday() {
        return summary == null ? 0L : summary.orderMetrics().ordersToday();
    }

    public List<Order> getRecentOrders() {
        return orderPage.items();
    }

    public PageResult<Order> getOrderPage() {
        return orderPage;
    }

    public OrderStatus getSelectedStatus() {
        return selectedStatus;
    }

    public void setSelectedStatus(OrderStatus selectedStatus) {
        this.selectedStatus = selectedStatus;
    }

    public OrderStatus getStatusToApply() {
        return statusToApply;
    }

    public void setStatusToApply(OrderStatus statusToApply) {
        this.statusToApply = statusToApply;
    }

    public String getSelectedOrderId() {
        return selectedOrderId;
    }

    public void setSelectedOrderId(String selectedOrderId) {
        this.selectedOrderId = selectedOrderId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public List<OrderStatus> getOrderStatuses() {
        return List.of(OrderStatus.values());
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public long getTotalPages() {
        return orderPage.totalPages();
    }

    public boolean isPreviousPageEnabled() {
        return page > 0;
    }

    public boolean isNextPageEnabled() {
        return page + 1 < orderPage.totalPages();
    }

    public void reloadOrders() {
        page = 0;
        if (selectedStatus == null) {
            orderPage = orderListUseCase.listOrders(page, PAGE_SIZE);
        } else {
            orderPage = orderListUseCase.listOrders(selectedStatus, page, PAGE_SIZE);
        }
    }

    public void filterOrders() {
        page = 0;
        if (selectedStatus == null) {
            orderPage = orderListUseCase.listOrders(page, PAGE_SIZE);
        } else {
            orderPage = orderListUseCase.listOrders(selectedStatus, page, PAGE_SIZE);
        }
    }

    public void nextPage() {
        if (page + 1 < orderPage.totalPages()) {
            page++;
            if (selectedStatus == null) {
                orderPage = orderListUseCase.listOrders(page, PAGE_SIZE);
            } else {
                orderPage = orderListUseCase.listOrders(selectedStatus, page, PAGE_SIZE);
            }
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            if (selectedStatus == null) {
                orderPage = orderListUseCase.listOrders(page, PAGE_SIZE);
            } else {
                orderPage = orderListUseCase.listOrders(selectedStatus, page, PAGE_SIZE);
            }
        }
    }

    public void updateSelectedOrderStatus() {
        if (selectedOrderId == null || statusToApply == null) {
            addMessage(FacesMessage.SEVERITY_WARN,
                    "Select an order and a target status before saving.");
            return;
        }

        if (statusToApply == OrderStatus.SHIPPED && (trackingNumber == null || trackingNumber.isBlank())) {
            addMessage(FacesMessage.SEVERITY_WARN,
                    "Shipping requires a tracking number before saving.");
            return;
        }

        updateOrderStatusUseCase.updateStatus(selectedOrderId, statusToApply, trackingNumber);
        addMessage(FacesMessage.SEVERITY_INFO,
                "Order " + selectedOrderId + " was updated to " + statusToApply + ".");
        reloadOrders();
    }

    private void addMessage(FacesMessage.Severity severity, String summary) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(severity, summary, null));
    }

    public long getOrdersThisMonth() {
        return summary == null ? 0L : summary.orderMetrics().ordersThisMonth();
    }

    public String getRevenueTodayFormatted() {
        return summary == null ? "R$ 0,00" : formatCurrency(summary.orderMetrics().revenueToday());
    }

    public String getRevenueThisMonthFormatted() {
        return summary == null ? "R$ 0,00" : formatCurrency(summary.orderMetrics().revenueThisMonth());
    }

    public String getAverageOrderValueFormatted() {
        return summary == null ? "R$ 0,00" : formatCurrency(summary.orderMetrics().averageOrderValueThisMonth());
    }

    /** Order counts by status, highest first, for the dashboard table. */
    public List<OrderStatusRow> getOrdersByStatus() {
        if (summary == null) {
            return List.of();
        }
        return summary.orderMetrics().ordersByStatus().entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(e -> new OrderStatusRow(String.valueOf(e.getKey()), e.getValue()))
                .toList();
    }

    private String formatCurrency(Money money) {
        return NumberFormat.getCurrencyInstance(BR).format(money.getAmount());
    }

    public record OrderStatusRow(String status, long count) { }
}
