package com.loja.admindashboard.adapter.in.web;

import com.loja.useraccount.application.dto.AuditLogSearchCriteria;
import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.domain.model.AuditLogEvent;
import com.loja.useraccount.domain.port.in.ListAuditLogsUseCase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Named
@ViewScoped
@RolesAllowed("ADMIN")
public class AuditLogBean implements Serializable {

    private static final int PAGE_SIZE = 20;
    private static final int DETAILS_PREVIEW_LENGTH = 60;

    @Inject
    private ListAuditLogsUseCase listAuditLogsUseCase;

    void setListAuditLogsUseCase(ListAuditLogsUseCase listAuditLogsUseCase) {
        this.listAuditLogsUseCase = listAuditLogsUseCase;
    }

    private List<AuditLogEvent> logs;
    private long totalElements;
    private int page = 0;

    private String actorFilter;
    private String eventTypeFilter;
    private String detailsFilter;
    private LocalDate fromDate;
    private LocalDate toDate;

    private List<String> eventTypes;

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        this.eventTypes = listAuditLogsUseCase.distinctEventTypes();
        PageResult<AuditLogEvent> result = listAuditLogsUseCase.listAuditLogs(criteria(), page, PAGE_SIZE);
        this.logs = result.items();
        this.totalElements = result.totalElements();
    }

    public void search() {
        page = 0;
        refresh();
    }

    public void resetFilters() {
        actorFilter = null;
        eventTypeFilter = null;
        detailsFilter = null;
        fromDate = null;
        toDate = null;
        search();
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

    public String displayDetails(AuditLogEvent log) {
        String details = log.details();
        if (details == null) {
            return "";
        }
        return isTruncated(log) ? details.substring(0, DETAILS_PREVIEW_LENGTH) + "…" : details;
    }

    public boolean isTruncated(AuditLogEvent log) {
        String details = log.details();
        return details != null && details.length() > DETAILS_PREVIEW_LENGTH;
    }

    public String fullDetails(AuditLogEvent log) {
        return log.details() != null ? log.details() : "";
    }

    public LocalDateTime logDate(AuditLogEvent log) {
        return log.createdAt().atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    private AuditLogSearchCriteria criteria() {
        return new AuditLogSearchCriteria(
                nullIfBlank(actorFilter),
                nullIfBlank(eventTypeFilter),
                nullIfBlank(detailsFilter),
                atStartOfDay(fromDate),
                atEndOfDay(toDate));
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

    public String getActorFilter() {
        return actorFilter;
    }

    public void setActorFilter(String actorFilter) {
        this.actorFilter = actorFilter;
    }

    public String getEventTypeFilter() {
        return eventTypeFilter;
    }

    public void setEventTypeFilter(String eventTypeFilter) {
        this.eventTypeFilter = eventTypeFilter;
    }

    public String getDetailsFilter() {
        return detailsFilter;
    }

    public void setDetailsFilter(String detailsFilter) {
        this.detailsFilter = detailsFilter;
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

    public List<String> getEventTypes() {
        return eventTypes;
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
