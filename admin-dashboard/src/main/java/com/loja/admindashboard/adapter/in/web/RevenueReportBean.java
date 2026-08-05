package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.loja.ordercheckout.domain.model.OrderRevenueReport;
import com.loja.ordercheckout.domain.model.ReportGranularity;
import com.loja.ordercheckout.domain.model.RevenuePoint;
import com.loja.ordercheckout.domain.port.in.RevenueReportUseCase;
import com.loja.shared.domain.Money;

import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Admin revenue report page (backlog S20): date range filter + granularity,
 * KPIs, revenue by payment method and a daily/weekly/monthly series.
 */
@Named("revenueReportBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class RevenueReportBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Inject
    private RevenueReportUseCase revenueReportUseCase;

    private LocalDate fromDate = LocalDate.now().minusMonths(1);
    private LocalDate toDate = LocalDate.now();
    private ReportGranularity granularity = ReportGranularity.DAILY;
    private OrderRevenueReport report;
    private boolean generated;

    void setRevenueReportUseCase(RevenueReportUseCase revenueReportUseCase) {
        this.revenueReportUseCase = revenueReportUseCase;
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
            report = revenueReportUseCase.revenueReport(from, to, granularity);
            generated = true;
        } catch (RuntimeException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Report failed",
                    "The revenue report could not be generated.");
        }
    }

    public List<Map.Entry<String, Money>> getRevenueByPaymentMethodEntries() {
        if (report == null) {
            return List.of();
        }
        return report.revenueByPaymentMethod().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
    }

    public List<RevenuePoint> getDailySeries() {
        return report == null ? List.of() : report.dailySeries();
    }

    public boolean isGenerated() {
        return generated && report != null;
    }

    public boolean isSeriesEmpty() {
        return report == null || report.dailySeries().isEmpty();
    }

    public boolean isPaymentBreakdownEmpty() {
        return report == null || report.revenueByPaymentMethod().isEmpty();
    }

    public String getGranularityLabel() {
        return switch (granularity) {
            case WEEKLY -> "Weekly";
            case MONTHLY -> "Monthly";
            case DAILY -> "Daily";
        };
    }

    public ReportGranularity[] getGranularities() {
        return ReportGranularity.values();
    }

    public int barHeight(RevenuePoint point) {
        BigDecimal maxAmount = report == null ? null : report.dailySeries().stream()
                .map(seriesPoint -> seriesPoint.revenue().getAmount())
                .max(BigDecimal::compareTo)
                .orElse(null);
        if (point == null || maxAmount == null || maxAmount.signum() == 0) {
            return 0;
        }
        return point.revenue().getAmount()
                .multiply(BigDecimal.valueOf(100))
                .divide(maxAmount, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    public String formatMoney(Money money) {
        return money == null ? "" : NumberFormat.getCurrencyInstance(BR).format(money.getAmount());
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

    public ReportGranularity getGranularity() {
        return granularity;
    }

    public void setGranularity(ReportGranularity granularity) {
        this.granularity = granularity;
    }

    public OrderRevenueReport getReport() {
        return report;
    }

    private void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }
}
