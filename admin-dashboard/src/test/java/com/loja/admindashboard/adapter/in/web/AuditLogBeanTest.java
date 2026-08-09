package com.loja.admindashboard.adapter.in.web;

import com.loja.useraccount.application.dto.AuditLogSearchCriteria;
import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.domain.model.AuditLogEvent;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import com.loja.useraccount.domain.port.in.ListAuditLogsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogBeanTest {

    private ListAuditLogsUseCase listAuditLogsUseCase;
    private FindUserUseCase findUserUseCase;
    private AuditLogBean bean;

    @BeforeEach
    void setUp() {
        listAuditLogsUseCase = mock(ListAuditLogsUseCase.class);
        findUserUseCase = mock(FindUserUseCase.class);
        bean = new AuditLogBean();
        bean.setListAuditLogsUseCase(listAuditLogsUseCase);
        bean.setFindUserUseCase(findUserUseCase);
        when(listAuditLogsUseCase.distinctEventTypes()).thenReturn(List.of("LOGIN_SUCCESS", "REGISTRATION"));
    }

    @Test
    void init_loadsFirstPageAndEventTypes() {
        when(listAuditLogsUseCase.listAuditLogs(any(), anyInt(), anyInt()))
                .thenReturn(pageWith(List.of(singleLog()), 1));

        bean.init();

        assertThat(bean.getLogs()).hasSize(1);
        assertThat(bean.getTotalElements()).isEqualTo(1);
        assertThat(bean.getEventTypes()).containsExactly("LOGIN_SUCCESS", "REGISTRATION");
    }

    @Test
    void refresh_usesCriteriaWithFiltersSet() {
        bean.setActorFilter("admin-9");
        bean.setEventTypeFilter("LOGIN_SUCCESS");
        bean.setDetailsFilter("chrome");
        bean.setFromDate(LocalDate.of(2026, 8, 1));
        bean.setToDate(LocalDate.of(2026, 8, 7));
        when(listAuditLogsUseCase.listAuditLogs(any(), anyInt(), anyInt()))
                .thenReturn(pageWith(List.of(singleLog()), 1));

        bean.refresh();

        ArgumentCaptor<AuditLogSearchCriteria> captor = ArgumentCaptor.forClass(AuditLogSearchCriteria.class);
        verify(listAuditLogsUseCase).listAuditLogs(captor.capture(), anyInt(), anyInt());
        AuditLogSearchCriteria criteria = captor.getValue();
        assertThat(criteria.actorId()).isEqualTo("admin-9");
        assertThat(criteria.eventType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(criteria.detailsKeyword()).isEqualTo("chrome");
        assertThat(criteria.from()).isNotNull();
        assertThat(criteria.to()).isNotNull();
    }

    @Test
    void search_resetsPageAndRefreshes() {
        when(listAuditLogsUseCase.listAuditLogs(any(), anyInt(), anyInt()))
                .thenReturn(pageWith(List.of(singleLog()), 21));

        bean.refresh();
        bean.nextPage();
        assertThat(bean.getPage()).isEqualTo(1);

        bean.search();

        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(listAuditLogsUseCase, org.mockito.Mockito.atLeast(3)).listAuditLogs(any(), pageCaptor.capture(), anyInt());
        assertThat(pageCaptor.getAllValues().get(pageCaptor.getAllValues().size() - 1)).isZero();
        assertThat(bean.getPage()).isZero();
    }

    @Test
    void resetFilters_clearsAllFilters() {
        bean.setActorFilter("admin-9");
        bean.setEventTypeFilter("LOGIN_SUCCESS");
        bean.setFromDate(LocalDate.of(2026, 8, 1));
        when(listAuditLogsUseCase.listAuditLogs(any(), anyInt(), anyInt()))
                .thenReturn(pageWith(List.of(singleLog()), 1));

        bean.resetFilters();

        ArgumentCaptor<AuditLogSearchCriteria> captor = ArgumentCaptor.forClass(AuditLogSearchCriteria.class);
        verify(listAuditLogsUseCase).listAuditLogs(captor.capture(), anyInt(), anyInt());
        AuditLogSearchCriteria criteria = captor.getValue();
        assertThat(criteria.actorId()).isNull();
        assertThat(criteria.eventType()).isNull();
        assertThat(criteria.from()).isNull();
        assertThat(criteria.to()).isNull();
    }

    @Test
    void pagination_tracksPageBounds() {
        when(listAuditLogsUseCase.listAuditLogs(any(), anyInt(), anyInt()))
                .thenReturn(pageWith(List.of(singleLog()), 21));

        bean.refresh();
        assertThat(bean.hasPreviousPage()).isFalse();
        assertThat(bean.hasNextPage()).isTrue();

        bean.nextPage();
        assertThat(bean.getPage()).isEqualTo(1);
        assertThat(bean.hasPreviousPage()).isTrue();
        assertThat(bean.hasNextPage()).isFalse();

        bean.previousPage();
        assertThat(bean.getPage()).isEqualTo(0);
        assertThat(bean.hasPreviousPage()).isFalse();
    }

    @Test
    void displayDetails_truncatesLongDetails() {
        String longDetails = "x".repeat(200);
        AuditLogEvent log = new AuditLogEvent(1L, "user-1", "admin-1", "LOGIN_SUCCESS", "USER", "user-1",
                null, null, longDetails, Instant.now());

        assertThat(bean.isTruncated(log)).isTrue();
        assertThat(bean.displayDetails(log)).hasSize(61);
        assertThat(bean.displayDetails(log)).endsWith("…");
        assertThat(bean.fullDetails(log)).isEqualTo(longDetails);
    }

    @Test
    void displayDetails_keepsShortDetails() {
        AuditLogEvent log = new AuditLogEvent(1L, "user-1", "admin-1", "LOGIN_SUCCESS", "USER", "user-1",
                null, null, "Short", Instant.now());

        assertThat(bean.isTruncated(log)).isFalse();
        assertThat(bean.displayDetails(log)).isEqualTo("Short");
    }

    @Test
    void actorDisplayName_resolvesAdminName() {
        when(findUserUseCase.findById("admin-1"))
                .thenReturn(Optional.of(new User("admin-1", null, null, UserProfile.fromFullName("Ada Lovelace"))));

        assertThat(bean.actorDisplayName(singleLog())).isEqualTo("Ada Lovelace");
    }

    @Test
    void actorDisplayName_fallsBackToSubjectNameWhenActorIsNull() {
        AuditLogEvent log = new AuditLogEvent(1L, "user-1", null, "LOGIN_SUCCESS", "USER", "user-1",
                "127.0.0.1", "Mozilla", "Login", Instant.now());
        when(findUserUseCase.findById("user-1"))
                .thenReturn(Optional.of(new User("user-1", null, null, UserProfile.fromFullName("Grace Hopper"))));

        assertThat(bean.actorDisplayName(log)).isEqualTo("Grace Hopper");
    }

    @Test
    void actorDisplayName_fallsBackToRawIdWhenAccountMissing() {
        when(findUserUseCase.findById("admin-1")).thenReturn(Optional.empty());

        assertThat(bean.actorDisplayName(singleLog())).isEqualTo("admin-1");
    }

    @Test
    void actorDisplayName_returnsDashWhenNoActor() {
        AuditLogEvent log = new AuditLogEvent(1L, null, null, "PRODUCT_ARCHIVED", "PRODUCT", "p1",
                "127.0.0.1", "Mozilla", "Archived", Instant.now());

        assertThat(bean.actorDisplayName(log)).isEqualTo("—");
    }

    private AuditLogEvent singleLog() {
        return new AuditLogEvent(1L, "user-1", "admin-1", "LOGIN_SUCCESS", "USER", "user-1",
                "127.0.0.1", "Mozilla", "Login", Instant.now());
    }

    private PageResult<AuditLogEvent> pageWith(List<AuditLogEvent> items, long total) {
        return new PageResult<>(items, total, 0, 20);
    }
}
