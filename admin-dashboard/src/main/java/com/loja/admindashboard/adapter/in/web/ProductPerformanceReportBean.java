package com.loja.admindashboard.adapter.in.web;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.loja.admindashboard.application.dto.ChartBar;
import com.loja.admindashboard.application.dto.CsvTable;
import com.loja.admindashboard.application.dto.PdfChart;
import com.loja.admindashboard.application.dto.PdfDocument;
import com.loja.admindashboard.application.dto.PdfSection;
import com.loja.admindashboard.domain.exception.ReportGenerationException;
import com.loja.admindashboard.domain.port.out.ReportExportPort;
import com.loja.ordercheckout.domain.model.CategoryUnits;
import com.loja.ordercheckout.domain.model.ProductPerformanceReport;
import com.loja.ordercheckout.domain.model.ProductPerformanceRow;
import com.loja.ordercheckout.domain.port.in.ProductPerformanceReportUseCase;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.shared.domain.Money;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Admin product performance report page (backlog S21): top sellers (by units),
 * top by revenue, bottom performers and a units-by-category chart, optionally
 * filtered by category. Supports CSV/PDF export (backlog S23).
 */
@Named("productPerformanceReportBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class ProductPerformanceReportBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    @Inject
    private ProductPerformanceReportUseCase productPerformanceReportUseCase;

    @Inject
    private CategoryRepositoryPort categoryRepository;

    @Inject
    private ReportExportPort reportExportPort;

    private Long selectedCategoryId;
    private List<Category> categories = List.of();
    private ProductPerformanceReport report;
    private boolean generated;

    void setProductPerformanceReportUseCase(ProductPerformanceReportUseCase productPerformanceReportUseCase) {
        this.productPerformanceReportUseCase = productPerformanceReportUseCase;
    }

    void setCategoryRepository(CategoryRepositoryPort categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    void setReportExportPort(ReportExportPort reportExportPort) {
        this.reportExportPort = reportExportPort;
    }

    @PostConstruct
    void load() {
        categories = categoryRepository.findAllActive();
    }

    public void generate() {
        try {
            report = productPerformanceReportUseCase.productPerformanceReport(selectedCategoryId);
            generated = true;
        } catch (RuntimeException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Report failed",
                    "The product performance report could not be generated.");
        }
    }

    public List<ProductPerformanceRow> getTopSellers() {
        return report == null ? List.of() : report.topSellers();
    }

    public List<ProductPerformanceRow> getTopByRevenue() {
        return report == null ? List.of() : report.topByRevenue();
    }

    public List<ProductPerformanceRow> getBottomPerformers() {
        return report == null ? List.of() : report.bottomPerformers();
    }

    public List<CategoryUnits> getUnitsByCategory() {
        return report == null ? List.of() : report.unitsByCategory();
    }

    public List<ChartBar> getUnitsByCategoryChartBars() {
        if (report == null || report.unitsByCategory().isEmpty()) {
            return List.of();
        }
        long maxUnits = report.unitsByCategory().stream()
                .mapToLong(CategoryUnits::unitsSold)
                .max()
                .orElse(0L);
        return report.unitsByCategory().stream()
                .map(entry -> new ChartBar(
                        entry.categoryName(),
                        formatUnits(entry.unitsSold()),
                        maxUnits == 0 ? 0 : (int) (entry.unitsSold() * 100L / maxUnits)))
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
                    "text/csv; charset=UTF-8", baseFileName() + ".csv");
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
                    "application/pdf", baseFileName() + ".pdf");
        } catch (ReportGenerationException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Export failed",
                    "The PDF could not be generated.");
        }
    }

    private CsvTable buildCsv() {
        List<List<String>> rows = new ArrayList<>();
        if (!report.topSellers().isEmpty()) {
            rows.add(List.of("SKU", "Product Name", "Units Sold", "Revenue", "Profit Margin (%)"));
            for (ProductPerformanceRow row : report.topSellers()) {
                rows.add(List.of(row.sku(), row.name(), formatUnits(row.unitsSold()),
                        formatMoney(row.revenue()), formatMargin(row.profitMargin())));
            }
        }
        if (!report.topByRevenue().isEmpty()) {
            rows.add(List.of());
            rows.add(List.of("Top by Revenue", "SKU", "Product Name", "Revenue", "Profit Margin (%)"));
            for (ProductPerformanceRow row : report.topByRevenue()) {
                rows.add(List.of(row.sku(), row.name(), formatUnits(row.unitsSold()),
                        formatMoney(row.revenue()), formatMargin(row.profitMargin())));
            }
        }
        if (!report.bottomPerformers().isEmpty()) {
            rows.add(List.of());
            rows.add(List.of("Bottom Performers", "SKU", "Product Name", "Units Sold", "Profit Margin (%)"));
            for (ProductPerformanceRow row : report.bottomPerformers()) {
                rows.add(List.of(row.sku(), row.name(), formatUnits(row.unitsSold()),
                        formatMoney(row.revenue()), formatMargin(row.profitMargin())));
            }
        }
        if (!report.unitsByCategory().isEmpty()) {
            rows.add(List.of());
            rows.add(List.of("Units by Category", "Category", "Units Sold"));
            for (CategoryUnits entry : report.unitsByCategory()) {
                rows.add(List.of(entry.categoryName(), formatUnits(entry.unitsSold())));
            }
        }
        return new CsvTable(List.of("Product Performance Report"), rows);
    }

    private PdfDocument buildPdf() {
        List<PdfChart> charts = List.of(new PdfChart("Units sold by category", getUnitsByCategoryChartBars(), List.of()));
        List<PdfSection> sections = new ArrayList<>();
        if (!report.topSellers().isEmpty()) {
            sections.add(new PdfSection("Top Sellers by Units",
                    List.of("SKU", "Product Name", "Units Sold", "Revenue", "Profit Margin (%)"),
                    toRows(report.topSellers())));
        }
        if (!report.topByRevenue().isEmpty()) {
            sections.add(new PdfSection("Top by Revenue",
                    List.of("SKU", "Product Name", "Units Sold", "Revenue", "Profit Margin (%)"),
                    toRows(report.topByRevenue())));
        }
        if (!report.bottomPerformers().isEmpty()) {
            sections.add(new PdfSection("Bottom Performers",
                    List.of("SKU", "Product Name", "Units Sold", "Revenue", "Profit Margin (%)"),
                    toRows(report.bottomPerformers())));
        }
        if (!report.unitsByCategory().isEmpty()) {
            sections.add(new PdfSection("Units by Category",
                    List.of("Category", "Units Sold"),
                    report.unitsByCategory().stream()
                            .map(entry -> List.of(entry.categoryName(), formatUnits(entry.unitsSold())))
                            .toList()));
        }
        return new PdfDocument("Product Performance Report", subtitle(), List.of(), charts, sections);
    }

    private List<List<String>> toRows(List<ProductPerformanceRow> rows) {
        return rows.stream()
                .map(row -> List.of(row.sku(), row.name(), formatUnits(row.unitsSold()),
                        formatMoney(row.revenue()), formatMargin(row.profitMargin())))
                .toList();
    }

    private String subtitle() {
        if (selectedCategoryId == null) {
            return "All categories";
        }
        return categories.stream()
                .filter(category -> category.getId().equals(selectedCategoryId))
                .findFirst()
                .map(category -> "Category: " + category.getName())
                .orElse("All categories");
    }

    private String baseFileName() {
        if (selectedCategoryId == null) {
            return "product-performance-report";
        }
        String slug = categories.stream()
                .filter(category -> category.getId().equals(selectedCategoryId))
                .findFirst()
                .map(category -> category.getName().toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", ""))
                .orElse("category");
        return "product-performance-report-" + slug;
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

    public boolean isTopSellersEmpty() {
        return report == null || report.topSellers().isEmpty();
    }

    public boolean isTopByRevenueEmpty() {
        return report == null || report.topByRevenue().isEmpty();
    }

    public boolean isBottomPerformersEmpty() {
        return report == null || report.bottomPerformers().isEmpty();
    }

    public boolean isUnitsByCategoryEmpty() {
        return report == null || report.unitsByCategory().isEmpty();
    }

    public List<Category> getCategories() {
        return categories;
    }

    public String formatMoney(Money money) {
        return money == null ? "" : NumberFormat.getCurrencyInstance(BR).format(money.getAmount());
    }

    public String formatUnits(long units) {
        return String.format("%,d", units);
    }

    public String formatMargin(BigDecimal margin) {
        return margin == null ? "—" : NumberFormat.getNumberInstance(BR).format(margin) + "%";
    }

    public Long getSelectedCategoryId() {
        return selectedCategoryId;
    }

    public void setSelectedCategoryId(Long selectedCategoryId) {
        this.selectedCategoryId = selectedCategoryId;
    }

    public ProductPerformanceReport getReport() {
        return report;
    }

    private void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
    }
}
