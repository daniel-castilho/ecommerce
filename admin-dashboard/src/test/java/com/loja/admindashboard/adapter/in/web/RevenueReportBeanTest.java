package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.admindashboard.application.dto.ChartBar;
import com.loja.ordercheckout.domain.model.OrderRevenueReport;
import com.loja.ordercheckout.domain.model.ReportGranularity;
import com.loja.ordercheckout.domain.model.RevenuePoint;
import com.loja.ordercheckout.domain.port.in.RevenueReportUseCase;
import com.loja.shared.domain.Money;

import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.Application;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

class RevenueReportBeanTest {

    static final class FacesContextAccessor extends FacesContext {
        static void setCurrent(FacesContext context) { setCurrentInstance(context); }
        @Override public Application getApplication() { return null; }
        @Override public ExternalContext getExternalContext() { return null; }
        @Override public void addMessage(String clientId, FacesMessage message) {}
        @Override public void release() {}
        @Override public jakarta.faces.context.ResponseStream getResponseStream() { return null; }
        @Override public void setResponseStream(jakarta.faces.context.ResponseStream responseStream) {}
        @Override public jakarta.faces.context.ResponseWriter getResponseWriter() { return null; }
        @Override public void setResponseWriter(jakarta.faces.context.ResponseWriter responseWriter) {}
        @Override public jakarta.faces.component.UIViewRoot getViewRoot() { return null; }
        @Override public void setViewRoot(jakarta.faces.component.UIViewRoot root) {}
        @Override public void renderResponse() {}
        @Override public jakarta.faces.lifecycle.Lifecycle getLifecycle() { return null; }
        @Override public java.util.Iterator<String> getClientIdsWithMessages() { return null; }
        @Override public FacesMessage.Severity getMaximumSeverity() { return null; }
        @Override public java.util.Iterator<FacesMessage> getMessages() { return null; }
        @Override public java.util.Iterator<FacesMessage> getMessages(String clientId) { return null; }
        @Override public jakarta.faces.render.RenderKit getRenderKit() { return null; }
        @Override public boolean getRenderResponse() { return false; }
        @Override public boolean getResponseComplete() { return false; }
        @Override public void responseComplete() {}
    }

    private final RevenueReportUseCase revenueReportUseCase = mock(RevenueReportUseCase.class);
    private final RevenueReportBean bean = new RevenueReportBean();

    @BeforeEach
    void setUp() {
        FacesContextAccessor.setCurrent(new FacesContextAccessor());
        bean.setRevenueReportUseCase(revenueReportUseCase);
    }

    @AfterEach
    void tearDown() {
        FacesContextAccessor.setCurrent(null);
    }

    @Test
    void revenueReportBean_isExposedAsViewScopedAdminBean() {
        Class<RevenueReportBean> beanClass = RevenueReportBean.class;

        Named named = beanClass.getAnnotation(Named.class);
        ViewScoped viewScoped = beanClass.getAnnotation(ViewScoped.class);
        RolesAllowed rolesAllowed = beanClass.getAnnotation(RolesAllowed.class);

        assertThat(named).isNotNull();
        assertThat(named.value()).isEqualTo("revenueReportBean");
        assertThat(viewScoped).isNotNull();
        assertThat(rolesAllowed).isNotNull();
        assertThat(rolesAllowed.value()).containsExactly("ADMIN");
    }

    @Test
    void generate_withValidRange_delegatesToUseCaseWithExclusiveToBoundary() {
        LocalDate fromDate = LocalDate.of(2026, 8, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 15);
        bean.setFromDate(fromDate);
        bean.setToDate(toDate);
        bean.setGranularity(ReportGranularity.WEEKLY);
        when(revenueReportUseCase.revenueReport(any(Instant.class), any(Instant.class), any(ReportGranularity.class)))
                .thenReturn(report());

        bean.generate();

        ZoneId zone = ZoneId.systemDefault();
        verify(revenueReportUseCase).revenueReport(
                fromDate.atStartOfDay(zone).toInstant(),
                toDate.plusDays(1).atStartOfDay(zone).toInstant(),
                ReportGranularity.WEEKLY);
        assertThat(bean.isGenerated()).isTrue();
        assertThat(bean.getReport()).isNotNull();
        assertThat(bean.getGranularityLabel()).isEqualTo("Weekly");
    }

    @Test
    void generate_withFromAfterTo_doesNotCallUseCase() {
        bean.setFromDate(LocalDate.of(2026, 8, 15));
        bean.setToDate(LocalDate.of(2026, 8, 1));

        bean.generate();

        verify(revenueReportUseCase, never()).revenueReport(any(Instant.class), any(Instant.class), any(ReportGranularity.class));
        assertThat(bean.isGenerated()).isFalse();
    }

    @Test
    void generate_withNullDates_doesNotCallUseCase() {
        bean.setFromDate(null);
        bean.setToDate(LocalDate.of(2026, 8, 1));

        bean.generate();

        verify(revenueReportUseCase, never()).revenueReport(any(Instant.class), any(Instant.class), any(ReportGranularity.class));
        assertThat(bean.isGenerated()).isFalse();
    }

    @Test
    void displayHelpers_formatCurrencyDatesAndChartBars() {
        bean.setFromDate(LocalDate.of(2026, 8, 1));
        bean.setToDate(LocalDate.of(2026, 8, 15));
        bean.setGranularity(ReportGranularity.DAILY);
        when(revenueReportUseCase.revenueReport(any(Instant.class), any(Instant.class), any(ReportGranularity.class)))
                .thenReturn(report());
        bean.generate();

        assertThat(bean.formatMoney(new Money(new BigDecimal("1234.50"))).replace('\u00A0', ' '))
                .isEqualTo("R$ 1.234,50");
        assertThat(bean.formatDate(LocalDate.of(2026, 8, 1))).isEqualTo("01/08/2026");
        assertThat(bean.getRevenueByPaymentMethodEntries())
                .extracting(Map.Entry::getKey)
                .containsExactly("card", "pix");
        assertThat(bean.isSeriesEmpty()).isFalse();
        assertThat(bean.isPaymentBreakdownEmpty()).isFalse();

        assertThat(bean.getRevenueChartBars())
                .extracting(ChartBar::label)
                .containsExactly("10/08/2026", "11/08/2026");
        assertThat(bean.getRevenueChartBars())
                .extracting(ChartBar::title)
                .allSatisfy(title -> assertThat(title).startsWith("R$"));
        assertThat(bean.getRevenueChartBars())
                .extracting(ChartBar::height)
                .containsExactly(50, 100);
    }

    @Test
    void displayHelpers_beforeGeneration_areEmpty() {
        assertThat(bean.isGenerated()).isFalse();
        assertThat(bean.getRevenueByPaymentMethodEntries()).isEmpty();
        assertThat(bean.getRevenueChartBars()).isEmpty();
        assertThat(bean.isSeriesEmpty()).isTrue();
        assertThat(bean.isPaymentBreakdownEmpty()).isTrue();
    }

    private static OrderRevenueReport report() {        return new OrderRevenueReport(
                new Money(new BigDecimal("300.00")),
                new Money(new BigDecimal("270.00")),
                new Money(new BigDecimal("30.00")),
                2L,
                new Money(new BigDecimal("150.00")),
                Map.of("card", new Money(new BigDecimal("200.00")),
                        "pix", new Money(new BigDecimal("100.00"))),
                List.of(
                        new RevenuePoint(LocalDate.of(2026, 8, 10), new Money(new BigDecimal("100.00"))),
                        new RevenuePoint(LocalDate.of(2026, 8, 11), new Money(new BigDecimal("200.00")))));
    }

    private static Instant anyInstant() {
        return org.mockito.ArgumentMatchers.any(Instant.class);
    }

    private static ReportGranularity anyGranularity() {
        return org.mockito.ArgumentMatchers.any(ReportGranularity.class);
    }
}
