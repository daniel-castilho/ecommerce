package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.util.List;

import com.loja.admindashboard.domain.port.in.OrderListUseCase;
import com.loja.admindashboard.domain.port.in.UpdateOrderStatusUseCase;
import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("orderManagementBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class OrderManagementBean implements Serializable {

    private static final int PAGE_SIZE = 10;

    @Inject
    private OrderListUseCase orderListUseCase;

    @Inject
    private UpdateOrderStatusUseCase updateOrderStatusUseCase;

    void setOrderListUseCase(OrderListUseCase orderListUseCase) {
        this.orderListUseCase = orderListUseCase;
    }

    void setUpdateOrderStatusUseCase(UpdateOrderStatusUseCase updateOrderStatusUseCase) {
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
    }

    private PageResult<Order> orderPage = new PageResult<>(List.of(), 0L, 0, PAGE_SIZE);
    private OrderStatus selectedStatus;
    private String selectedOrderId;
    private int page;

    @PostConstruct
    void load() {
        reloadOrders();
    }

    public List<Order> getOrders() {
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

    public List<OrderStatus> getOrderStatuses() {
        return List.of(OrderStatus.values());
    }

    public String getSelectedOrderId() {
        return selectedOrderId;
    }

    public void setSelectedOrderId(String selectedOrderId) {
        this.selectedOrderId = selectedOrderId;
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
        reloadOrders();
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

    public void selectOrder(String orderId) {
        this.selectedOrderId = orderId;
    }

    public void updateSelectedOrderStatus() {
        if (selectedOrderId == null || selectedStatus == null) {
            return;
        }
        updateOrderStatusUseCase.updateStatus(selectedOrderId, selectedStatus, null);
        reloadOrders();
    }
}
