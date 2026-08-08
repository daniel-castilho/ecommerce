package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.loja.admindashboard.domain.port.in.ListProductsForAdminUseCase;
import com.loja.ordercheckout.domain.model.ProductSalesAggregate;
import com.loja.ordercheckout.domain.port.in.ProductSalesStatsUseCase;
import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.shared.domain.Money;

class ProductManagementBeanTest {

    @Test
    void refresh_usesCriteriaAndStoresPageResult() {
        ListProductsForAdminUseCase listProductsForAdminUseCase = mock(ListProductsForAdminUseCase.class);
        CategoryRepositoryPort categoryRepository = mock(CategoryRepositoryPort.class);
        ProductSalesStatsUseCase salesStatsUseCase = mock(ProductSalesStatsUseCase.class);
        ProductManagementBean bean = new ProductManagementBean();
        bean.setListProductsForAdminUseCase(listProductsForAdminUseCase);
        bean.setCategoryRepository(categoryRepository);
        bean.setProductSalesStatsUseCase(salesStatsUseCase);

        Product product = new Product("p-1", new Sku("SKU-1"), new Slug("sku-1"), "Keyboard",
                null, null, new Money(new BigDecimal("49.90")), null, 10, ProductStatus.ACTIVE,
                null, null, null, null, List.of());
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, null, null, null, null, 0, 20, false, null, null);
        when(listProductsForAdminUseCase.listProducts(criteria)).thenReturn(new PageResult<>(List.of(product), 1L, 0, 20));
        when(salesStatsUseCase.salesByProductId()).thenReturn(Map.of());

        bean.refresh();

        assertThat(bean.getProducts()).containsExactly(product);
        assertThat(bean.getTotalElements()).isEqualTo(1L);
        verify(listProductsForAdminUseCase).listProducts(criteria);
        verify(salesStatsUseCase).salesByProductId();
    }

    @Test
    void profitMargin_usesCostPriceAndSales() {
        ListProductsForAdminUseCase listProductsForAdminUseCase = mock(ListProductsForAdminUseCase.class);
        CategoryRepositoryPort categoryRepository = mock(CategoryRepositoryPort.class);
        ProductSalesStatsUseCase salesStatsUseCase = mock(ProductSalesStatsUseCase.class);
        ProductManagementBean bean = new ProductManagementBean();
        bean.setListProductsForAdminUseCase(listProductsForAdminUseCase);
        bean.setCategoryRepository(categoryRepository);
        bean.setProductSalesStatsUseCase(salesStatsUseCase);

        Product product = new Product("p-1", new Sku("SKU-1"), new Slug("sku-1"), "Keyboard",
                null, null, new Money(new BigDecimal("49.90")), null,
                new Money(new BigDecimal("30.00")), 10, ProductStatus.ACTIVE,
                null, null, null, null, List.of());
        when(salesStatsUseCase.salesByProductId()).thenReturn(Map.of(
                "p-1", new ProductSalesAggregate("p-1", 4L, new Money(new BigDecimal("199.60")))));
        when(listProductsForAdminUseCase.listProducts(any())).thenReturn(new PageResult<>(List.of(), 0L, 0, 20));
        bean.refresh();

        assertThat(bean.profitMargin(product)).isEqualByComparingTo("39.88");
        assertThat(bean.costPriceOf(product)).isEqualByComparingTo("30.00");
        assertThat(bean.formatMargin(bean.profitMargin(product))).isEqualTo("39.88%");
    }

    @Test
    void profitMargin_isNullWithoutSalesOrCostPrice() {
        ListProductsForAdminUseCase listProductsForAdminUseCase = mock(ListProductsForAdminUseCase.class);
        CategoryRepositoryPort categoryRepository = mock(CategoryRepositoryPort.class);
        ProductSalesStatsUseCase salesStatsUseCase = mock(ProductSalesStatsUseCase.class);
        ProductManagementBean bean = new ProductManagementBean();
        bean.setListProductsForAdminUseCase(listProductsForAdminUseCase);
        bean.setCategoryRepository(categoryRepository);
        bean.setProductSalesStatsUseCase(salesStatsUseCase);

        Product withoutCost = new Product("p-1", new Sku("SKU-1"), new Slug("sku-1"), "Keyboard",
                null, null, new Money(new BigDecimal("49.90")), null, 10, ProductStatus.ACTIVE,
                null, null, null, null, List.of());
        Product unsold = new Product("p-2", new Sku("SKU-2"), new Slug("sku-2"), "Mouse",
                null, null, new Money(new BigDecimal("29.90")), null,
                new Money(new BigDecimal("10.00")), 5, ProductStatus.ACTIVE,
                null, null, null, null, List.of());
        when(salesStatsUseCase.salesByProductId()).thenReturn(Map.of(
                "p-1", new ProductSalesAggregate("p-1", 4L, new Money(new BigDecimal("199.60")))));
        when(listProductsForAdminUseCase.listProducts(any())).thenReturn(new PageResult<>(List.of(), 0L, 0, 20));
        bean.refresh();

        assertThat(bean.profitMargin(withoutCost)).isNull();
        assertThat(bean.profitMargin(unsold)).isNull();
        assertThat(bean.costPriceOf(withoutCost)).isNull();
        assertThat(bean.formatMargin(null)).isEqualTo("—");
    }
}
