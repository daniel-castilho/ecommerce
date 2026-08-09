package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.application.dto.RefundSearchCriteria;
import com.loja.ordercheckout.application.dto.RefundSort;
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
    private String customerQuery;
    private LocalDate fromDate;
    private LocalDate toDate;
    private RefundSort sort = RefundSort.REQUESTED_DATE;
    private boolean ascending;
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

    public List<RefundSort> getSortOptions() {
        return List.of(RefundSort.values());
    }

    public String getCustomerQuery() {
        return customerQuery;
    }

    public void setCustomerQuery(String customerQuery) {
        this.customerQuery = customerQuery;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public RefundSort getSort() {
        return sort;
    }

    public void setSort(RefundSort sort) {
        this.sort = sort;
    }

    public boolean isAscending() {
        return ascending;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
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
        refundPage = refundManagementUseCase.listRefundRequests(criteria(), page, PAGE_SIZE);
    }

    public void filterRefunds() {
        reloadRefunds();
    }

    public void nextPage() {
        if (page + 1 < refundPage.totalPages()) {
            page++;
            refundPage = refundManagementUseCase.listRefundRequests(criteria(), page, PAGE_SIZE);
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            refundPage = refundManagementUseCase.listRefundRequests(criteria(), page, PAGE_SIZE);
        }
    }

    private RefundSearchCriteria criteria() {
        return new RefundSearchCriteria(
                selectedStatus,
                nullIfBlank(customerQuery),
                atStartOfDay(fromDate),
                atEndOfDay(toDate),
                sort != null ? sort : RefundSort.REQUESTED_DATE,
                ascending);
    }

    private String nullIfBlank(String value) {
        return value != null && value.isBlank() ? null : value;
    }

    private Instant atStartOfDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant atEndOfDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
    }
}
