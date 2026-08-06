package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.admindashboard.application.dto.ChartLine;
import com.loja.ordercheckout.domain.model.CustomerGrowthPoint;
import com.loja.ordercheckout.domain.model.CustomerInsightsReport;
import com.loja.ordercheckout.domain.port.in.CustomerInsightsReportUseCase;
import com.loja.shared.domain.Money;

import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.Application;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

class CustomerInsightsReportBeanTest {

    static final class FacesContextAccessor extends FacesContext {
        static void setCurrent(FacesContext context) { setCurrentInstance(context); }
        @Override public Application getApplication() { return null; }
        @Override public ExternalContext getExternalContext() { return null; }
        @Override public void addMessage(String clientId, jakarta.faces.application.FacesMessage message) {}
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
        @Override public jakarta.faces.application.FacesMessage.Severity getMaximumSeverity() { return null; }
        @Override public java.util.Iterator<jakarta.faces.application.FacesMessage> getMessages() { return null; }
        @Override public java.util.Iterator<jakarta.faces.application.FacesMessage> getMessages(String clientId) { return null; }
        @Override public jakarta.faces.render.RenderKit getRenderKit() { return null; }
        @Override public boolean getRenderResponse() { return false; }
        @Override public boolean getResponseComplete() { return false; }
        @Override public void responseComplete() {}
    }

    private final CustomerInsightsReportUseCase useCase = mock(CustomerInsightsReportUseCase.class);
    private final CustomerInsightsReportBean bean = new CustomerInsightsReportBean();

    @BeforeEach
    void setUp() {
        FacesContextAccessor.setCurrent(new FacesContextAccessor());
        bean.setCustomerInsightsReportUseCase(useCase);
    }

    @AfterEach
    void tearDown() {
        FacesContextAccessor.setCurrent(null);
    }

    @Test
    void customerInsightsReportBean_isExposedAsViewScopedAdminBean() {
        Class<CustomerInsightsReportBean> beanClass = CustomerInsightsReportBean.class;

        Named named = beanClass.getAnnotation(Named.class);
        ViewScoped viewScoped = beanClass.getAnnotation(ViewScoped.class);
        RolesAllowed rolesAllowed = beanClass.getAnnotation(RolesAllowed.class);

        assertThat(named).isNotNull();
        assertThat(named.value()).isEqualTo("customerInsightsReportBean");
        assertThat(viewScoped).isNotNull();
        assertThat(rolesAllowed).isNotNull();
        assertThat(rolesAllowed.value()).containsExactly("ADMIN");
    }

    @Test
    void generate_delegatesToUseCaseWithInclusiveDayRange() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        bean.setFromDate(from);
        bean.setToDate(to);
        when(useCase.customerInsightsReport(any(), any())).thenReturn(report());

        bean.generate();

        verify(useCase).customerInsightsReport(
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant());
        assertThat(bean.isGenerated()).isTrue();
        assertThat(bean.getReport()).isNotNull();
    }

    @Test
    void generate_withReversedRange_doesNotCallUseCase() {
        bean.setFromDate(LocalDate.of(2026, 6, 30));
        bean.setToDate(LocalDate.of(2026, 6, 1));

        bean.generate();

        assertThat(bean.isGenerated()).isFalse();
    }

    @Test
    void generate_whenUseCaseFails_doesNotMarkGenerated() {
        bean.setFromDate(LocalDate.of(2026, 6, 1));
        bean.setToDate(LocalDate.of(2026, 6, 30));
        when(useCase.customerInsightsReport(any(), any()))
                .thenThrow(new RuntimeException("boom"));

        bean.generate();

        assertThat(bean.isGenerated()).isFalse();
        assertThat(bean.getNewCustomersChartLines()).isEmpty();
    }

    @Test
    void displayHelpers_returnEmptyBeforeGeneration() {
        assertThat(bean.isGenerated()).isFalse();
        assertThat(bean.isSeriesEmpty()).isTrue();
        assertThat(bean.getNewCustomersChartLines()).isEmpty();
        assertThat(bean.getNewCustomersPolylinePoints()).isEmpty();
    }

    @Test
    void displayHelpers_buildChartLinesAndPolyline() {
        LocalDate day1 = LocalDate.of(2026, 6, 1);
        LocalDate day2 = LocalDate.of(2026, 6, 2);
        LocalDate day3 = LocalDate.of(2026, 6, 3);
        when(useCase.customerInsightsReport(any(), any()))
                .thenReturn(new CustomerInsightsReport(100L, 7L, 25.0,
                        new Money(new BigDecimal("50.00")), 10.0,
                        List.of(
                                new CustomerGrowthPoint(day1, 1L),
                                new CustomerGrowthPoint(day2, 2L),
                                new CustomerGrowthPoint(day3, 4L))));

        bean.setFromDate(LocalDate.of(2026, 6, 1));
        bean.setToDate(LocalDate.of(2026, 6, 30));
        bean.generate();

        assertThat(bean.isSeriesEmpty()).isFalse();
        assertThat(bean.getNewCustomersChartLines())
                .containsExactly(
                        new ChartLine("01/06/2026", "1", 0, 75),
                        new ChartLine("02/06/2026", "2", 50, 50),
                        new ChartLine("03/06/2026", "4", 100, 0));
        assertThat(bean.getNewCustomersPolylinePoints()).isEqualTo("0,75 50,50 100,0");
    }

    @Test
    void displayHelpers_singlePointCentersChart() {
        LocalDate day = LocalDate.of(2026, 6, 1);
        when(useCase.customerInsightsReport(any(), any()))
                .thenReturn(new CustomerInsightsReport(1L, 3L, 0.0, Money.zero(), 0.0,
                        List.of(new CustomerGrowthPoint(day, 3L))));

        bean.setFromDate(LocalDate.of(2026, 6, 1));
        bean.setToDate(LocalDate.of(2026, 6, 30));
        bean.generate();

        assertThat(bean.getNewCustomersChartLines())
                .containsExactly(new ChartLine("01/06/2026", "3", 50, 0));
    }

    @Test
    void formatHelpers_formatValuesForBrLocale() {
        assertThat(bean.formatCount(1234)).isEqualTo("1.234");
        assertThat(bean.formatMoney(new Money(new BigDecimal("1234.50"))).replace('\u00A0', ' '))
                .isEqualTo("R$ 1.234,50");
        assertThat(bean.formatPercent(40.0)).isEqualTo("40%");
    }

    private static CustomerInsightsReport report() {
        return new CustomerInsightsReport(100L, 5L, 25.0,
                new Money(new BigDecimal("50.00")), 10.0,
                List.of(new CustomerGrowthPoint(LocalDate.now(), 5L)));
    }
}
