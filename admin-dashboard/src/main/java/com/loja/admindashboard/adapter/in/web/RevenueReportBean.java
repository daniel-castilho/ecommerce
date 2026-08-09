package com.loja.admindashboard.adapter.in.web;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.loja.admindashboard.application.dto.ChartBar;
import com.loja.admindashboard.application.dto.CsvTable;
import com.loja.admindashboard.application.dto.PdfDocument;
import com.loja.admindashboard.application.dto.PdfKeyValue;
import com.loja.admindashboard.application.dto.PdfSection;
import com.loja.admindashboard.domain.exception.ReportGenerationException;
import com.loja.admindashboard.domain.port.out.ReportExportPort;
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
import jakarta.servlet.http.HttpServletResponse;

/**
 * Admin revenue report page (backlog S20): date range filter + granularity,
 * KPIs, revenue by payment method and a daily/weekly/monthly series. Supports
 * CSV/PDF export (backlog S23).
 */
@Named("revenueReportBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class RevenueReportBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Inject
    private RevenueReportUseCase revenueReportUseCase;

    @Inject
    private ReportExportPort reportExportPort;

    private LocalDate fromDate = LocalDate.now().minusMonths(1);
    private LocalDate toDate = LocalDate.now();
    private ReportGranularity granularity = ReportGranularity.DAILY;
    private OrderRevenueReport report;
    private boolean generated;

    void setRevenueReportUseCase(RevenueReportUseCase revenueReportUseCase) {
        this.revenueReportUseCase = revenueReportUseCase;
    }

    void setReportExportPort(ReportExportPort reportExportPort) {
        this.reportExportPort = reportExportPort;
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

    public List<ChartBar> getRevenueChartBars() {
        if (report == null || report.dailySeries().isEmpty()) {
            return List.of();
        }
        BigDecimal maxAmount = report.dailySeries().stream()
                .map(point -> point.revenue().getAmount())
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return report.dailySeries().stream()
                .map(point -> new ChartBar(
                        formatDate(point.date()),
                        formatMoney(point.revenue()),
                        maxAmount.signum() == 0 ? 0
                                : point.revenue().getAmount()
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(maxAmount, 0, RoundingMode.HALF_UP)
                                        .intValue()))
                .toList();
    }

    public boolean isGenerated() {
        return generated && report != null;
    }

    public void exportCsv() {
        if (!isGenerated()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Report not generated",
                    "Generate the report before exporting.");
            return;
        }
        try {
            download(reportExportPort.generateCsv(buildCsv()),
                    "text/csv; charset=UTF-8", "revenue-report-" + fileDateRange() + ".csv");
        } catch (ReportGenerationException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Export failed",
                    "The CSV could not be generated.");
        }
    }

    public void exportPdf() {
        if (!isGenerated()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Report not generated",
                    "Generate the report before exporting.");
            return;
        }
        try {
            download(reportExportPort.generatePdf(buildPdf()),
                    "application/pdf", "revenue-report-" + fileDateRange() + ".pdf");
        } catch (ReportGenerationException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Export failed",
                    "The PDF could not be generated.");
        }
    }

    private CsvTable buildCsv() {
        List<List<String>> rows = new ArrayList<>();
        for (RevenuePoint point : report.dailySeries()) {
            rows.add(List.of(formatDate(point.date()), formatMoney(point.revenue())));
        }
        if (!report.revenueByPaymentMethod().isEmpty()) {
            rows.add(List.of());
            rows.add(List.of("Payment Method", "Revenue"));
            report.revenueByPaymentMethod().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> rows.add(List.of(entry.getKey(), formatMoney(entry.getValue()))));
        }
        return new CsvTable(List.of("Date", "Revenue"), rows);
    }

    private PdfDocument buildPdf() {
        List<PdfKeyValue> kpis = List.of(
                new PdfKeyValue("Total Revenue", formatMoney(report.totalRevenue())),
                new PdfKeyValue("Items Revenue", formatMoney(report.itemsRevenue())),
                new PdfKeyValue("Shipping Revenue", formatMoney(report.shippingRevenue())),
                new PdfKeyValue("Total Orders", Long.toString(report.orderCount())),
                new PdfKeyValue("Average Order Value", formatMoney(report.averageOrderValue())));
        List<PdfSection> sections = new ArrayList<>();
        sections.add(new PdfSection("Revenue over time", List.of("Date", "Revenue"),
                report.dailySeries().stream()
                        .map(point -> List.of(formatDate(point.date()), formatMoney(point.revenue())))
                        .toList()));
        sections.add(new PdfSection("Revenue by payment method", List.of("Payment Method", "Revenue"),
                report.revenueByPaymentMethod().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> List.of(entry.getKey(), formatMoney(entry.getValue())))
                        .toList()));
        return new PdfDocument("Revenue Report", subtitle(), kpis, sections);
    }

    private String subtitle() {
        return formatDate(fromDate) + " - " + formatDate(toDate) + " (" + getGranularityLabel() + ")";
    }

    private String fileDateRange() {
        return FILE_DATE_FORMAT.format(fromDate) + "-" + FILE_DATE_FORMAT.format(toDate);
    }

    private void download(byte[] bytes, String contentType, String filename) {
        HttpServletResponse response = (HttpServletResponse) FacesContext.getCurrentInstance()
                .getExternalContext().getResponse();
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        try {
            response.getOutputStream().write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        FacesContext.getCurrentInstance().responseComplete();
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
