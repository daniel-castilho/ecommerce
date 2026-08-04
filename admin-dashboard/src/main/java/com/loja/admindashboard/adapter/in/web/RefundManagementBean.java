package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.util.List;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.in.RefundManagementUseCase;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("refundManagementBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class RefundManagementBean implements Serializable {

    private static final int PAGE_SIZE = 20;

    @Inject
    private RefundManagementUseCase refundManagementUseCase;

    void setRefundManagementUseCase(RefundManagementUseCase refundManagementUseCase) {
        this.refundManagementUseCase = refundManagementUseCase;
    }

    private PageResult<RefundRequest> refundPage = new PageResult<>(List.of(), 0L, 0, PAGE_SIZE);
    private RefundStatus selectedStatus;
    private int page;

    @PostConstruct
    void load() {
        reloadRefunds();
    }

    public List<RefundRequest> getRefunds() {
        return refundPage.items();
    }

    public PageResult<RefundRequest> getRefundPage() {
        return refundPage;
    }

    public RefundStatus getSelectedStatus() {
        return selectedStatus;
    }

    public void setSelectedStatus(RefundStatus selectedStatus) {
        this.selectedStatus = selectedStatus;
    }

    public List<RefundStatus> getRefundStatuses() {
        return List.of(RefundStatus.values());
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public long getTotalPages() {
        return refundPage.totalPages();
    }

    public boolean isPreviousPageEnabled() {
        return page > 0;
    }

    public boolean isNextPageEnabled() {
        return page + 1 < refundPage.totalPages();
    }

    public void reloadRefunds() {
        page = 0;
        refundPage = refundManagementUseCase.listRefundRequests(selectedStatus, page, PAGE_SIZE);
    }

    public void filterRefunds() {
        reloadRefunds();
    }

    public void nextPage() {
        if (page + 1 < refundPage.totalPages()) {
            page++;
            refundPage = refundManagementUseCase.listRefundRequests(selectedStatus, page, PAGE_SIZE);
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            refundPage = refundManagementUseCase.listRefundRequests(selectedStatus, page, PAGE_SIZE);
        }
    }
}
