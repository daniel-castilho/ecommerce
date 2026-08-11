package com.loja.admindashboard.adapter.in.web;

import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import com.loja.ordercheckout.domain.port.in.NotificationDeliveryManagementUseCase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Admin notification delivery log (Phase D): lists outbox rows — optionally filtered by
 * status — and lets an admin re-queue a delivery whose attempts were exhausted, so a
 * stuck email can be retried from the UI instead of staying invisible in the database.
 */
@Named
@ViewScoped
@RolesAllowed("ADMIN")
public class DeliveryLogBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int ERROR_PREVIEW_LENGTH = 80;

    @Inject
    private NotificationDeliveryManagementUseCase deliveryManagement;

    private NotificationDeliveryStatus statusFilter;
    private List<NotificationDelivery> deliveries;

    void setDeliveryManagement(NotificationDeliveryManagementUseCase deliveryManagement) {
        this.deliveryManagement = deliveryManagement;
    }

    @PostConstruct
    void init() {
        refresh();
    }

    public void refresh() {
        deliveries = deliveryManagement.listDeliveries(statusFilter);
    }

    public void resend(NotificationDelivery delivery) {
        boolean resent = deliveryManagement.resend(delivery.getIdempotencyKey());
        refresh();
        addMessage(resent ? FacesMessage.SEVERITY_INFO : FacesMessage.SEVERITY_ERROR,
                resent ? "Delivery re-queued" : "Delivery not found",
                delivery.getIdempotencyKey());
    }

    public NotificationDeliveryStatus[] getStatuses() {
        return NotificationDeliveryStatus.values();
    }

    public String errorPreview(NotificationDelivery delivery) {
        String error = delivery.getErrorMessage();
        if (error == null) {
            return "";
        }
        return isErrorTruncated(delivery) ? error.substring(0, ERROR_PREVIEW_LENGTH) + "…" : error;
    }

    public boolean isErrorTruncated(NotificationDelivery delivery) {
        String error = delivery.getErrorMessage();
        return error != null && error.length() > ERROR_PREVIEW_LENGTH;
    }

    public String fullError(NotificationDelivery delivery) {
        return delivery.getErrorMessage() != null ? delivery.getErrorMessage() : "";
    }

    public LocalDateTime deliveryTime(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    private void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }

    public NotificationDeliveryStatus getStatusFilter() {
        return statusFilter;
    }

    public void setStatusFilter(NotificationDeliveryStatus statusFilter) {
        this.statusFilter = statusFilter;
    }

    public List<NotificationDelivery> getDeliveries() {
        return deliveries;
    }
}
