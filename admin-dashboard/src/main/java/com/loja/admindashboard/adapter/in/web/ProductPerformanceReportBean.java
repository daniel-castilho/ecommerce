package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import com.loja.admindashboard.application.dto.ChartBar;
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

/**
 * Admin product performance report page (backlog S21): top sellers (by units),
 * top by revenue, bottom performers and a units-by-category chart, optionally
 * filtered by category.
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
