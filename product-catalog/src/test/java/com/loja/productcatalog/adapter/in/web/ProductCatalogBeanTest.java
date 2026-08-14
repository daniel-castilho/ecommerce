package com.loja.productcatalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.application.dto.ProductSearchHit;
import com.loja.productcatalog.application.dto.ProductSortField;
import com.loja.productcatalog.application.dto.SortDirection;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.in.SearchProductsUseCase;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;
import com.loja.shared.domain.Money;

class ProductCatalogBeanTest {

    private final SearchProductsUseCase searchProductsUseCase = mock(SearchProductsUseCase.class);
    private final CategoryRepositoryPort categoryRepository = mock(CategoryRepositoryPort.class);
    private final ProductImageStoragePort imageStorage = mock(ProductImageStoragePort.class);

    private ProductCatalogBean bean;

    @BeforeEach
    void setUp() throws Exception {
        bean = new ProductCatalogBean();
        injectField("searchProductsUseCase", searchProductsUseCase);
        injectField("categoryRepository", categoryRepository);
        injectField("imageStorage", imageStorage);
    }

    @Test
    void search_usesContextualSearchWithSnippets() {
        bean.setSearchTerm("smart");
        when(searchProductsUseCase.searchWithSnippets(any(ProductSearchCriteria.class)))
                .thenReturn(new PageResult<>(List.of(hit("p1", "<mark>Smart</mark> phone")), 1L, 0, 20));

        bean.search();

        verify(searchProductsUseCase).searchWithSnippets(any(ProductSearchCriteria.class));
        assertThat(bean.isSearchActive()).isTrue();
        assertThat(bean.getResultCountText()).isEqualTo("1 product found");
        assertThat(bean.getResults()).extracting(ProductSearchHit::snippet)
                .containsExactly("<mark>Smart</mark> phone");
    }

    @Test
    void clearSearch_afterActiveSearch_resetsTermAndPaginatesFromStart() {
        bean.setSearchTerm("nothing");
        when(searchProductsUseCase.searchWithSnippets(any()))
                .thenReturn(new PageResult<>(List.of(), 0L, 0, 20));
        bean.search();

        bean.clearSearch();

        assertThat(bean.isSearchActive()).isFalse();
        assertThat(bean.getPage()).isZero();
        assertThat(bean.getResultCountText()).isEqualTo("0 products");
    }

    @Test
    void clearSearch_keepsOtherFilters() {
        bean.setSearchTerm("smart");
        bean.setCategoryId(7L);
        when(searchProductsUseCase.searchWithSnippets(any()))
                .thenReturn(new PageResult<>(List.of(hit("p1", null)), 1L, 0, 20));
        bean.search();

        bean.clearSearch();

        assertThat(bean.getCategoryId()).isEqualTo(7L);
    }

    @Test
    void searchEmpty_whenActiveAndNoMatches() {
        bean.setSearchTerm("zzz");
        when(searchProductsUseCase.searchWithSnippets(any()))
                .thenReturn(new PageResult<>(List.of(), 0L, 0, 20));

        bean.search();

        assertThat(bean.isSearchActive()).isTrue();
        assertThat(bean.isSearchEmpty()).isTrue();
    }

    @Test
    void searchEmpty_whenBrowsingEmptyCatalog_returnsFalse() {
        bean.clearSearch();

        assertThat(bean.isSearchActive()).isFalse();
        assertThat(bean.isSearchEmpty()).isFalse();
        assertThat(bean.getTotalPages()).isEqualTo(1);
    }

    @Test
    void primaryImageUrl_usesPrimaryImageStorageUrl() throws Exception {
        Product product = new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Smartphone",
                "Short", null, new Money(new BigDecimal("1000.00")), null, 5,
                ProductStatus.ACTIVE, null, null, null,
                Set.of(1L), List.of(new ProductImage(1L, "products/ABC-123/img.webp", null, 0, true)));
        when(imageStorage.publicUrlFor("products/ABC-123/img.webp"))
                .thenReturn("https://cdn.example.com/products/ABC-123/img.webp");

        assertThat(bean.primaryImageUrl(product))
                .isEqualTo("https://cdn.example.com/products/ABC-123/img.webp");
        assertThat(bean.primaryImageAlt(product)).isEqualTo("Smartphone");
    }

    @Test
    void sortOptions_exposeHumanLabels() {
        assertThat(bean.getSortFieldOptions()).extracting("label")
                .containsExactly("Most relevant", "Name", "Price", "Date added");
        assertThat(bean.getSortDirectionOptions()).extracting("label")
                .containsExactly("Ascending", "Descending");
        assertThat(bean.getSortField()).isEqualByComparingTo(ProductSortField.RELEVANCE);
        assertThat(bean.getSortDirection()).isEqualByComparingTo(SortDirection.DESC);
    }

    private static ProductSearchHit hit(String id, String snippet) {
        return new ProductSearchHit(product(id), snippet);
    }

    private static Product product(String id) {
        return new Product(id, new Sku("ABC-123"), new Slug("abc-123"), "Smartphone",
                "Short description", null, new Money(new BigDecimal("1000.00")), null, 5,
                ProductStatus.ACTIVE, null, null, null, Set.of(1L), List.of());
    }

    private void injectField(String fieldName, Object value) throws Exception {
        Field field = ProductCatalogBean.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(bean, value);
    }
}