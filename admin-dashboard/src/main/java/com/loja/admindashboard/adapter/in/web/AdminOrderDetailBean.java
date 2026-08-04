package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.loja.admindashboard.application.dto.OrderDetailsDTO;
import com.loja.admindashboard.domain.port.in.GetOrderDetailsUseCase;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("adminOrderDetailBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class AdminOrderDetailBean implements Serializable {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Inject
    private GetOrderDetailsUseCase getOrderDetailsUseCase;

    private OrderDetailsDTO selectedOrder;

    void setGetOrderDetailsUseCase(GetOrderDetailsUseCase getOrderDetailsUseCase) {
        this.getOrderDetailsUseCase = getOrderDetailsUseCase;
    }

    @PostConstruct
    void init() {
        String orderId = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap().get("orderId");
        loadOrder(orderId);
    }

    public void loadOrder(String orderId) {
        selectedOrder = orderId == null || orderId.isBlank()
                ? null
                : getOrderDetailsUseCase.findById(orderId).orElse(null);
    }

    public OrderDetailsDTO getSelectedOrder() {
        return selectedOrder;
    }

    public String formatDate(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }
}
