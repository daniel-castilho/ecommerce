package com.loja.ordercheckout.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.loja.ordercheckout.domain.model.CategoryUnits;
import com.loja.ordercheckout.domain.model.ProductPerformanceReport;
import com.loja.ordercheckout.domain.model.ProductPerformanceRow;
import com.loja.ordercheckout.domain.model.ProductSalesAggregate;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.shared.domain.Money;

class ProductPerformanceReportServiceTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final ProductRepositoryPort productRepository = mock(ProductRepositoryPort.class);
    private final CategoryRepositoryPort categoryRepository = mock(CategoryRepositoryPort.class);
    private final ProductPerformanceReportService service =
            new ProductPerformanceReportService(orderRepository, productRepository, categoryRepository);

    @Test
    void productPerformanceReport_ordersTopSellersByUnitsThenName() {
        when(productRepository.findAll()).thenReturn(List.of(
                product("p1", "SKU-1", "Alpha", Set.of(1L)),
                product("p2", "SKU-2", "Beta", Set.of(1L)),
                product("p3", "SKU-3", "Gamma", Set.of(1L))));
        when(orderRepository.productSales()).thenReturn(List.of(
                sales("p2", 5L, "50.00"),
                sales("p1", 10L, "100.00")));

        ProductPerformanceReport report = service.productPerformanceReport(null);

        assertThat(report.topSellers()).extracting(ProductPerformanceRow::sku)
                .containsExactly("SKU-1", "SKU-2", "SKU-3");
        assertThat(report.topSellers()).extracting(ProductPerformanceRow::unitsSold)
                .containsExactly(10L, 5L, 0L);
        assertThat(report.topByRevenue()).extracting(ProductPerformanceRow::sku)
                .containsExactly("SKU-1", "SKU-2", "SKU-3");
        assertThat(report.bottomPerformers()).extracting(ProductPerformanceRow::sku)
                .containsExactly("SKU-3", "SKU-2", "SKU-1");
        verify(orderRepository).productSales();
    }

    @Test
    void productPerformanceReport_ranksZeroSalesProductsBottom() {
        when(productRepository.findAll()).thenReturn(List.of(
                product("p1", "SKU-1", "Sold", Set.of(1L)),
                product("p2", "SKU-2", "NeverSold", Set.of(1L))));
        when(orderRepository.productSales()).thenReturn(List.of(sales("p1", 3L, "30.00")));

        ProductPerformanceReport report = service.productPerformanceReport(null);

        assertThat(report.bottomPerformers()).extracting(ProductPerformanceRow::sku)
                .containsExactly("SKU-2", "SKU-1");
        assertThat(report.bottomPerformers().get(0).unitsSold()).isZero();
        assertThat(report.bottomPerformers().get(0).revenue()).isEqualTo(Money.zero());
    }

    @Test
    void productPerformanceReport_withCategoryFilter_excludesOtherCategories() {
        when(productRepository.findAll()).thenReturn(List.of(
                product("p1", "SKU-1", "Electronics", Set.of(1L)),
                product("p2", "SKU-2", "Books", Set.of(2L))));
        when(orderRepository.productSales()).thenReturn(List.of(
                sales("p1", 4L, "40.00"),
                sales("p2", 9L, "90.00")));
        when(categoryRepository.findAll()).thenReturn(List.of(category(1L, "Electronics")));

        ProductPerformanceReport report = service.productPerformanceReport(1L);

        assertThat(report.topSellers()).extracting(ProductPerformanceRow::sku)
                .containsExactly("SKU-1");
        assertThat(report.unitsByCategory()).extracting(CategoryUnits::categoryName)
                .containsExactly("Electronics");
        assertThat(report.unitsByCategory().get(0).unitsSold()).isEqualTo(4L);
    }

    @Test
    void productPerformanceReport_unitsByCategory_countsMultiCategoryProductsInEach() {
        when(productRepository.findAll()).thenReturn(List.of(
                product("p1", "SKU-1", "Hybrid", Set.of(1L, 2L))));
        when(orderRepository.productSales()).thenReturn(List.of(sales("p1", 7L, "70.00")));
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "Electronics"),
                category(2L, "Home")));

        ProductPerformanceReport report = service.productPerformanceReport(null);

        assertThat(report.unitsByCategory()).extracting(CategoryUnits::categoryName)
                .containsExactlyInAnyOrder("Electronics", "Home");
        assertThat(report.unitsByCategory()).extracting(CategoryUnits::unitsSold)
                .containsExactlyInAnyOrder(7L, 7L);
    }

    @Test
    void productPerformanceReport_withNullCategory_andNoFilter_usesAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(
                product("p1", "SKU-1", "Alpha", Set.of(1L))));
        when(orderRepository.productSales()).thenReturn(List.of());

        ProductPerformanceReport report = service.productPerformanceReport(null);

        assertThat(report.topSellers()).hasSize(1);
        assertThat(report.topByRevenue()).hasSize(1);
        assertThat(report.bottomPerformers()).hasSize(1);
        assertThat(report.unitsByCategory()).isEmpty();
    }

    private static Product product(String id, String sku, String name, Set<Long> categoryIds) {
        return new Product(id, new Sku(sku), new Slug(sku.toLowerCase() + "-" + id),
                name, null, null, new Money(new BigDecimal("10.00")), null, 5,
                ProductStatus.ACTIVE, null, null, null, categoryIds, List.of());
    }

    private static Category category(Long id, String name) {
        return new Category(id, name, new Slug(name.toLowerCase() + "-" + id), null, 0, true);
    }

    private static ProductSalesAggregate sales(String productId, long units, String revenue) {
        return new ProductSalesAggregate(productId, units, new Money(new BigDecimal(revenue)));
    }
}
