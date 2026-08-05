package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.domain.model.OrderRevenueReport;
import com.loja.ordercheckout.domain.model.ReportGranularity;
import com.loja.ordercheckout.domain.model.RevenuePoint;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevenueReportServiceTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final RevenueReportService service = new RevenueReportService(orderRepository);

    private static final Instant FROM =
            LocalDate.of(2026, 8, 1).atStartOfDay(ZoneId.systemDefault()).toInstant();

    private static OrderRevenueReport dailyReport() {
        return new OrderRevenueReport(
                new Money(new BigDecimal("300.00")),
                new Money(new BigDecimal("270.00")),
                new Money(new BigDecimal("30.00")),
                2L,
                new Money(new BigDecimal("150.00")),
                Map.of("card", new Money(new BigDecimal("300.00"))),
                List.of(
                        new RevenuePoint(LocalDate.of(2026, 8, 10), new Money(new BigDecimal("100.00"))),
                        new RevenuePoint(LocalDate.of(2026, 8, 11), new Money(new BigDecimal("200.00")))));
    }

    @Test
    void revenueReport_withDailyGranularity_returnsRepositorySeriesUnchanged() {
        when(orderRepository.revenueReport(any(), any())).thenReturn(dailyReport());

        OrderRevenueReport report = service.revenueReport(FROM, FROM.plusSeconds(86400L), ReportGranularity.DAILY);

        assertThat(report).isEqualTo(dailyReport());
        assertThat(report.dailySeries()).hasSize(2);
        verify(orderRepository).revenueReport(FROM, FROM.plusSeconds(86400L));
    }

    @Test
    void revenueReport_withWeeklyGranularity_rollsUpSeriesToIsoWeekMondays() {
        when(orderRepository.revenueReport(any(), any())).thenReturn(dailyReport());

        OrderRevenueReport report = service.revenueReport(FROM, FROM.plusSeconds(86400L), ReportGranularity.WEEKLY);

        assertThat(report.totalRevenue()).isEqualTo(new Money(new BigDecimal("300.00")));
        assertThat(report.orderCount()).isEqualTo(2L);
        assertThat(report.dailySeries()).hasSize(1);
        RevenuePoint week = report.dailySeries().get(0);
        assertThat(week.date()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(week.revenue()).isEqualTo(new Money(new BigDecimal("300.00")));
    }

    @Test
    void revenueReport_withMonthlyGranularity_rollsUpSeriesToMonthStart() {
        when(orderRepository.revenueReport(any(), any())).thenReturn(dailyReport());

        OrderRevenueReport report = service.revenueReport(FROM, FROM.plusSeconds(86400L), ReportGranularity.MONTHLY);

        assertThat(report.dailySeries()).hasSize(1);
        RevenuePoint month = report.dailySeries().get(0);
        assertThat(month.date()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(month.revenue()).isEqualTo(new Money(new BigDecimal("300.00")));
    }

    @Test
    void revenueReport_withNullGranularity_defaultsToDaily() {
        when(orderRepository.revenueReport(any(), any())).thenReturn(dailyReport());

        OrderRevenueReport report = service.revenueReport(FROM, FROM.plusSeconds(86400L), null);

        assertThat(report.dailySeries()).hasSize(2);
        verify(orderRepository).revenueReport(any(), any());
    }

    @Test
    void revenueReport_withNullRange_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.revenueReport(null, FROM.plusSeconds(1L), ReportGranularity.DAILY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        assertThatThrownBy(() -> service.revenueReport(FROM, null, ReportGranularity.DAILY))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderRepository, never()).revenueReport(any(), any());
    }

    @Test
    void revenueReport_withFromAfterTo_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.revenueReport(FROM.plusSeconds(86400L), FROM, ReportGranularity.DAILY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after");
        verify(orderRepository, never()).revenueReport(any(), any());
    }
}
