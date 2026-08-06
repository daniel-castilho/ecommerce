package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.loja.admindashboard.application.dto.ChartLine;
import com.loja.ordercheckout.domain.model.CustomerGrowthPoint;
import com.loja.ordercheckout.domain.model.CustomerInsightsReport;
import com.loja.ordercheckout.domain.port.in.CustomerInsightsReportUseCase;
import com.loja.shared.domain.Money;

import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Admin customer insights report page (backlog S22): date range filter, customer
 * KPIs (total, new, repeat rate, average LTV, churn rate) and a
 * "New Customers by Date" line chart.
 */
@Named("customerInsightsReportBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class CustomerInsightsReportBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Inject
    private CustomerInsightsReportUseCase customerInsightsReportUseCase;

    private LocalDate fromDate = LocalDate.now().minusMonths(1);
    private LocalDate toDate = LocalDate.now();
    private CustomerInsightsReport report;
    private boolean generated;

    void setCustomerInsightsReportUseCase(CustomerInsightsReportUseCase useCase) {
        this.customerInsightsReportUseCase = useCase;
    }

    public void generate() {
        if (fromDate == null || toDate == null) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Invalid date range",
                    "Both a 'from' and a 'to' date are required.");
            return;
        }
        if (fromDate.isAfter(toDate)) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Invalid date range",
                    "The 'from' date must not be after the 'to' date.");
            return;
        }
        try {
            ZoneId zone = ZoneId.systemDefault();
            Instant from = fromDate.atStartOfDay(zone).toInstant();
            Instant to = toDate.plusDays(1).atStartOfDay(zone).toInstant();
            report = customerInsightsReportUseCase.customerInsightsReport(from, to);
            generated = true;
        } catch (RuntimeException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Report failed",
                    "The customer insights report could not be generated.");
        }
    }

    public boolean isGenerated() {
        return generated && report != null;
    }

    public boolean isSeriesEmpty() {
        return report == null || report.newCustomersSeries().isEmpty();
    }

    public List<ChartLine> getNewCustomersChartLines() {
        if (report == null || report.newCustomersSeries().isEmpty()) {
            return List.of();
        }
        List<CustomerGrowthPoint> series = report.newCustomersSeries();
        long maxCount = series.stream()
                .mapToLong(CustomerGrowthPoint::count)
                .max()
                .orElse(1L);
        int size = series.size();
        List<ChartLine> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CustomerGrowthPoint point = series.get(i);
            int x = size == 1 ? 50 : i * 100 / (size - 1);
            int y = maxCount == 0 ? 100 : 100 - (int) Math.round(point.count() * 100.0 / maxCount);
            lines.add(new ChartLine(formatDate(point.date()),
                    formatCount(point.count()), x, y));
        }
        return lines;
    }

    public String getNewCustomersPolylinePoints() {
        if (report == null || report.newCustomersSeries().isEmpty()) {
            return "";
        }
        return getNewCustomersChartLines().stream()
                .map(line -> line.x() + "," + line.y())
                .collect(Collectors.joining(" "));
    }

    public String formatMoney(Money money) {
        return money == null ? "" : NumberFormat.getCurrencyInstance(BR).format(money.getAmount());
    }

    public String formatCount(long count) {
        return NumberFormat.getNumberInstance(BR).format(count);
    }

    public String formatPercent(double percent) {
        NumberFormat format = NumberFormat.getNumberInstance(BR);
        format.setMaximumFractionDigits(1);
        return format.format(percent) + "%";
    }

    public String formatDate(LocalDate date) {
        return date == null ? "" : DATE_FORMAT.format(date);
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

    public CustomerInsightsReport getReport() {
        return report;
    }

    private void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }
}
