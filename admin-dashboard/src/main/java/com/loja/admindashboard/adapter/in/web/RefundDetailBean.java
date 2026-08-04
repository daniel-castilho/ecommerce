package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.in.RefundManagementUseCase;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("refundDetailBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class RefundDetailBean implements Serializable {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Inject
    private RefundManagementUseCase refundManagementUseCase;

    private RefundRequest selectedRefund;
    private String refundId;
    private String rejectionReason;

    void setRefundManagementUseCase(RefundManagementUseCase refundManagementUseCase) {
        this.refundManagementUseCase = refundManagementUseCase;
    }

    @PostConstruct
    void init() {
        refundId = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap().get("refundId");
        loadRefund(refundId);
    }

    public void loadRefund(String id) {
        refundId = id;
        selectedRefund = id == null || id.isBlank()
                ? null
                : refundManagementUseCase.findRefundById(id).orElse(null);
    }

    public void approve() {
        if (selectedRefund == null) {
            return;
        }
        try {
            refundManagementUseCase.approveRefund(selectedRefund.getId());
            addMessage(FacesMessage.SEVERITY_INFO, "Refund approved",
                    "The refund was processed successfully.");
        } catch (RuntimeException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Refund approval failed",
                    "The payment processor could not process the refund. The request remains pending.");
        }
        loadRefund(refundId);
    }

    public void reject() {
        if (selectedRefund == null) {
            return;
        }
        if (rejectionReason == null || rejectionReason.isBlank()) {
            addMessage(FacesMessage.SEVERITY_WARN, "Rejection reason required",
                    "Provide a reason before rejecting the refund.");
            return;
        }
        refundManagementUseCase.rejectRefund(selectedRefund.getId(), rejectionReason);
        addMessage(FacesMessage.SEVERITY_INFO, "Refund rejected",
                    "The refund request was rejected.");
        loadRefund(refundId);
    }

    public boolean isPending() {
        return selectedRefund != null && selectedRefund.getStatus() == RefundStatus.PENDING;
    }

    public String formatDate(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    public RefundRequest getSelectedRefund() {
        return selectedRefund;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    private void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }
}
