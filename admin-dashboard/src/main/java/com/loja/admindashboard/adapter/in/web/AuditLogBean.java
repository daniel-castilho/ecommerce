package com.loja.admindashboard.adapter.in.web;

import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.domain.model.AuditLogEvent;
import com.loja.useraccount.domain.port.in.ListAuditLogsUseCase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
@RolesAllowed("ADMIN")
public class AuditLogBean implements Serializable {

    private static final int PAGE_SIZE = 20;

    @Inject
    private ListAuditLogsUseCase listAuditLogsUseCase;

    private List<AuditLogEvent> logs;
    private long totalElements;
    private int page = 0;

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        PageResult<AuditLogEvent> result = listAuditLogsUseCase.listAuditLogs(page, PAGE_SIZE);
        this.logs = result.items();
        this.totalElements = result.totalElements();
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

    public boolean hasPreviousPage() {
        return page > 0;
    }

    public boolean hasNextPage() {
        return (page + 1) * PAGE_SIZE < totalElements;
    }

    public List<AuditLogEvent> getLogs() {
        return logs;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getPage() {
        return page;
    }
}
