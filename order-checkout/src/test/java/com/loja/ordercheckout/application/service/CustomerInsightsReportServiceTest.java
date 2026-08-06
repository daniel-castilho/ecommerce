package com.loja.ordercheckout.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.loja.ordercheckout.domain.model.CustomerGrowthPoint;
import com.loja.ordercheckout.domain.model.CustomerInsightsReport;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.shared.domain.Money;
import com.loja.useraccount.domain.model.UserGrowthPoint;
import com.loja.useraccount.domain.port.out.UserRepositoryPort;

class CustomerInsightsReportServiceTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final UserRepositoryPort userRepository = mock(UserRepositoryPort.class);
    private final CustomerInsightsReportService service =
            new CustomerInsightsReportService(orderRepository, userRepository);

    @Test
    void customerInsightsReport_computesAllMetricsFromPorts() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.userGrowthSeries(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000)))
                .thenReturn(List.of(
                        new UserGrowthPoint(today, 2L),
                        new UserGrowthPoint(yesterday, 3L)));
        when(orderRepository.repeatCustomerCount()).thenReturn(4L);
        when(orderRepository.totalCustomerRevenue()).thenReturn(new Money(new BigDecimal("1000.00")));
        when(userRepository.countInactiveSince(any())).thenReturn(1L);

        CustomerInsightsReport report = service.customerInsightsReport(
                Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000));

        assertThat(report.totalCustomers()).isEqualTo(10L);
        assertThat(report.newCustomers()).isEqualTo(5L);
        assertThat(report.repeatCustomerRate()).isEqualTo(40.0);
        assertThat(report.averageLtv()).isEqualTo(new Money(new BigDecimal("100.00")));
        assertThat(report.churnRate()).isEqualTo(10.0);
        assertThat(report.newCustomersSeries()).containsExactly(
                new CustomerGrowthPoint(today, 2L),
                new CustomerGrowthPoint(yesterday, 3L));
        verify(orderRepository).repeatCustomerCount();
        verify(orderRepository).totalCustomerRevenue();
    }

    @Test
    void customerInsightsReport_withNoCustomers_returnsZeroMetrics() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.userGrowthSeries(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000)))
                .thenReturn(List.of());
        when(orderRepository.repeatCustomerCount()).thenReturn(0L);
        when(orderRepository.totalCustomerRevenue()).thenReturn(Money.zero());

        CustomerInsightsReport report = service.customerInsightsReport(
                Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000));

        assertThat(report.totalCustomers()).isZero();
        assertThat(report.newCustomers()).isZero();
        assertThat(report.repeatCustomerRate()).isEqualTo(0.0);
        assertThat(report.averageLtv()).isEqualTo(Money.zero());
        assertThat(report.churnRate()).isEqualTo(0.0);
        assertThat(report.newCustomersSeries()).isEmpty();
    }

    @Test
    void customerInsightsReport_rejectsNullRange() {
        assertThatThrownBy(() -> service.customerInsightsReport(null, Instant.ofEpochSecond(2000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        assertThatThrownBy(() -> service.customerInsightsReport(Instant.ofEpochSecond(1000), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customerInsightsReport_rejectsReversedRange() {
        assertThatThrownBy(() -> service.customerInsightsReport(
                Instant.ofEpochSecond(2000), Instant.ofEpochSecond(1000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be after");
    }

    @Test
    void customerInsightsReport_countsChurnAgainstA90DayCutoff() {
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.userGrowthSeries(any(), any())).thenReturn(List.of());
        when(orderRepository.repeatCustomerCount()).thenReturn(0L);
        when(orderRepository.totalCustomerRevenue()).thenReturn(Money.zero());
        when(userRepository.countInactiveSince(any())).thenReturn(2L);

        CustomerInsightsReport report = service.customerInsightsReport(
                Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000));

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).countInactiveSince(captor.capture());
        Instant expectedCutoff = Instant.now().minus(Duration.ofDays(90));
        assertThat(captor.getValue()).isBetween(
                expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
        assertThat(report.churnRate()).isEqualTo(20.0);
    }
}
