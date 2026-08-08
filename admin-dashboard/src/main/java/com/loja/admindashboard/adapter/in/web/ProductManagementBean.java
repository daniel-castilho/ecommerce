package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.loja.admindashboard.domain.port.in.ListProductsForAdminUseCase;
import com.loja.ordercheckout.domain.model.ProductSalesAggregate;
import com.loja.ordercheckout.domain.port.in.ProductSalesStatsUseCase;
import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("productManagementBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class ProductManagementBean implements Serializable {

    private static final int PAGE_SIZE = 20;

    @Inject
    private ListProductsForAdminUseCase listProductsForAdminUseCase;

    @Inject
    private CategoryRepositoryPort categoryRepository;

    @Inject
    private ProductSalesStatsUseCase productSalesStatsUseCase;

    private List<Product> products = List.of();
    private List<Category> categories = List.of();
    private Map<String, ProductSalesAggregate> salesByProductId = Map.of();
    private long totalElements;
    private int page;
    private String keyword;
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private ProductStatus status;
    private boolean includeArchived;
    private boolean compactIncludeArchived;

    void setListProductsForAdminUseCase(ListProductsForAdminUseCase listProductsForAdminUseCase) {
        this.listProductsForAdminUseCase = listProductsForAdminUseCase;
    }

    void setCategoryRepository(CategoryRepositoryPort categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    void setProductSalesStatsUseCase(ProductSalesStatsUseCase productSalesStatsUseCase) {
        this.productSalesStatsUseCase = productSalesStatsUseCase;
    }

    @PostConstruct
    void load() {
        refresh();
    }

    public void refresh() {
        categories = categoryRepository.findAllActive();
        salesByProductId = productSalesStatsUseCase.salesByProductId();
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                keyword,
                categoryId,
                minPrice,
                maxPrice,
                status,
                page,
                PAGE_SIZE,
                includeArchived,
                null,
                null);
        PageResult<Product> result = listProductsForAdminUseCase.listProducts(criteria);
        products = result.items();
        totalElements = result.totalElements();
    }

    public void search() {
        page = 0;
        refresh();
    }

    public void nextPage() {
        if ((page + 1L) * PAGE_SIZE < totalElements) {
            page++;
            refresh();
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            refresh();
        }
    }

    public boolean hasPreviousPage() {
        return page > 0;
    }

    public boolean hasNextPage() {
        return (page + 1L) * PAGE_SIZE < totalElements;
    }

    public String newProduct() {
        return "/admin-dashboard/products/create.xhtml?faces-redirect=true";
    }

    public String viewProduct(String productId) {
        return "/admin-dashboard/products/edit.xhtml?faces-redirect=true&productId=" + productId;
    }

    public List<Product> getProducts() {
        return products;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public boolean isIncludeArchived() { return includeArchived; }
    public void setIncludeArchived(boolean includeArchived) { this.includeArchived = includeArchived; }

    public boolean isCompactIncludeArchived() { return compactIncludeArchived; }
    public void setCompactIncludeArchived(boolean compactIncludeArchived) { this.compactIncludeArchived = compactIncludeArchived; }

    public void toggleCompactIncludeArchived() {
        this.includeArchived = this.compactIncludeArchived;
        this.page = 0;
        refresh();
    }

    public ProductStatus[] getAvailableStatuses() {
        return ProductStatus.values();
    }

    /**
     * Gross profit margin percentage for one product row, derived from the
     * product's cost price and its all-time sales (backlog S10). {@code null}
     * when there is no cost price or no sales to compute against.
     */
    public BigDecimal profitMargin(Product product) {
        if (product == null) {
            return null;
        }
        ProductSalesAggregate sales = salesByProductId.get(product.getId());
        if (sales == null) {
            return null;
        }
        return product.profitMargin(sales.revenue(), sales.unitsSold());
    }

    public String formatMargin(BigDecimal margin) {
        return margin == null ? "—" : margin.toPlainString() + "%";
    }

    public BigDecimal costPriceOf(Product product) {
        return product != null && product.getCostPrice() != null
                ? product.getCostPrice().getAmount() : null;
    }
}
