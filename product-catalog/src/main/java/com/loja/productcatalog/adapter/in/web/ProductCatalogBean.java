package com.loja.productcatalog.adapter.in.web;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.application.dto.ProductSortField;
import com.loja.productcatalog.application.dto.SortDirection;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.port.in.SearchProductsUseCase;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Public catalog bean: search/filter/pagination over ACTIVE products (spec §8).
 * {@code @ViewScoped} so the filter state (search term, category, price range, sort)
 * survives pagination clicks — {@code @RequestScoped} would lose it between postbacks.
 * Calls {@link SearchProductsUseCase} only; categories are read for the filter dropdown.
 */
@Named
@ViewScoped
public class ProductCatalogBean implements Serializable {

    private static final int PAGE_SIZE = ProductSearchCriteria.DEFAULT_PAGE_SIZE;

    @Inject
    private SearchProductsUseCase searchProductsUseCase;

    @Inject
    private CategoryRepositoryPort categoryRepository;

    @Inject
    private ProductImageStoragePort imageStorage;

    private String searchTerm;
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private ProductSortField sortField = ProductSortField.RELEVANCE;
    private SortDirection sortDirection = SortDirection.ASC;
    private int page;

    private List<Category> categories = List.of();
    private PageResult<Product> result;

    @PostConstruct
    void init() {
        categories = categoryRepository.findAllActive();
        refresh();
    }

    public void search() {
        page = 0;
        refresh();
    }

    public void nextPage() {
        if (hasNextPage()) {
            page++;
            refresh();
        }
    }

    public void previousPage() {
        if (hasPreviousPage()) {
            page--;
            refresh();
        }
    }

    private void refresh() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                searchTerm, categoryId, minPrice, maxPrice, ProductStatus.ACTIVE,
                page, PAGE_SIZE, false, sortField, sortDirection);
        result = searchProductsUseCase.search(criteria);
    }

    public boolean hasNextPage() {
        return result != null && page + 1 < result.totalPages();
    }

    public boolean hasPreviousPage() {
        return result != null && page > 0;
    }

    public int getTotalPages() {
        return result != null ? Math.max(1, result.totalPages()) : 1;
    }

    public long getTotalElements() {
        return result != null ? result.totalElements() : 0;
    }

    public List<Product> getResults() {
        return result != null ? result.items() : List.of();
    }

    public String primaryImageUrl(Product product) {
        return primaryImage(product).map(image -> imageStorage.publicUrlFor(image.getObjectKey()))
                .orElse(null);
    }

    public String primaryImageAlt(Product product) {
        return primaryImage(product).map(ProductImage::getAltText)
                .filter(altText -> altText != null && !altText.isBlank())
                .orElse(product.getName());
    }

    private static java.util.Optional<ProductImage> primaryImage(Product product) {
        return product.getImages().stream()
                .filter(ProductImage::isPrimary)
                .findFirst();
    }

    public ProductSortField[] getAvailableSortFields() { return ProductSortField.values(); }
    public SortDirection[] getAvailableSortDirections() { return SortDirection.values(); }

    public List<Category> getCategories() { return categories; }
    public String getSearchTerm() { return searchTerm; }
    public void setSearchTerm(String searchTerm) { this.searchTerm = searchTerm; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
    public ProductSortField getSortField() { return sortField; }
    public void setSortField(ProductSortField sortField) { this.sortField = sortField; }
    public SortDirection getSortDirection() { return sortDirection; }
    public void setSortDirection(SortDirection sortDirection) { this.sortDirection = sortDirection; }
    public int getPage() { return page; }
}
