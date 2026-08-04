package com.loja.admindashboard.adapter.in.web;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.port.in.CustomerOrderHistoryUseCase;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserStatus;
import com.loja.useraccount.domain.port.in.ChangeUserStatusUseCase;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

/**
 * Admin-only customer detail view: loads a customer with recent orders and offers
 * block/unblock actions. Thin adapter: delegates to user-account input ports only.
 */
@Named("adminCustomerDetailBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class AdminCustomerDetailBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private FindUserUseCase findUserUseCase;

    @Inject
    private CustomerOrderHistoryUseCase customerOrderHistoryUseCase;

    @Inject
    private ChangeUserStatusUseCase changeUserStatusUseCase;

    private String customerId;
    private User customer;
    private List<Order> recentOrders;

    void setFindUserUseCase(FindUserUseCase findUserUseCase) {
        this.findUserUseCase = findUserUseCase;
    }

    void setCustomerOrderHistoryUseCase(CustomerOrderHistoryUseCase customerOrderHistoryUseCase) {
        this.customerOrderHistoryUseCase = customerOrderHistoryUseCase;
    }

    void setChangeUserStatusUseCase(ChangeUserStatusUseCase changeUserStatusUseCase) {
        this.changeUserStatusUseCase = changeUserStatusUseCase;
    }

    public void loadCustomer() {
        if (customerId != null && !customerId.isBlank()) {
            findUserUseCase.findById(customerId).ifPresent(user -> {
                this.customer = user;
                PageResult<Order> orders = customerOrderHistoryUseCase.listByCustomer(customerId, 0, 5);
                if (orders != null && orders.items() != null) {
                    this.recentOrders = orders.items();
                }
            });
        }
    }

    public void block() {
        if (customer == null) {
            return;
        }
        changeUserStatusUseCase.blockUser(customer.getId());
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Customer Blocked",
                        customer.getEmail() + " has been blocked."));
        loadCustomer();
    }

    public void unblock() {
        if (customer == null) {
            return;
        }
        changeUserStatusUseCase.unblockUser(customer.getId());
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Customer Unblocked",
                        customer.getEmail() + " has been activated."));
        loadCustomer();
    }

    public boolean isBlocked() {
        return customer != null
                && (customer.getStatus() == UserStatus.INACTIVE
                        || customer.getStatus() == UserStatus.LOCKED);
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public User getCustomer() {
        return customer;
    }

    public List<Order> getRecentOrders() {
        return recentOrders;
    }
}
