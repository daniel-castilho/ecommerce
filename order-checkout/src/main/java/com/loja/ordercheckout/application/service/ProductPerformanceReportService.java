package com.loja.ordercheckout.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.loja.ordercheckout.domain.model.CategoryUnits;
import com.loja.ordercheckout.domain.model.ProductPerformanceReport;
import com.loja.ordercheckout.domain.model.ProductPerformanceRow;
import com.loja.ordercheckout.domain.model.ProductSalesAggregate;
import com.loja.ordercheckout.domain.port.in.ProductPerformanceReportUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.shared.domain.Money;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Product performance report over all-time sales. Depends only on repository
 * ports (DIP): order lines come from {@link OrderRepositoryPort}, catalog
 * identity (SKU/name/categories) from the product-catalog ports; used by the
 * admin-dashboard module (backlog S21). The report is right-sized to the current
 * domain — there is no cost price on the product, so no profit margin column.
 */
@ApplicationScoped
public class ProductPerformanceReportService implements ProductPerformanceReportUseCase {

    private static final int TOP_N = 10;

    private final OrderRepositoryPort orderRepository;
    private final ProductRepositoryPort productRepository;
    private final CategoryRepositoryPort categoryRepository;

    @Inject
    public ProductPerformanceReportService(OrderRepositoryPort orderRepository,
                                           ProductRepositoryPort productRepository,
                                           CategoryRepositoryPort categoryRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public ProductPerformanceReport productPerformanceReport(Long categoryId) {
        List<Product> products = productRepository.findAll();
        Map<String, ProductSalesAggregate> salesByProduct = orderRepository.productSales().stream()
                .collect(Collectors.toMap(ProductSalesAggregate::productId, Function.identity()));

        List<ProductPerformanceRow> rows = new ArrayList<>();
        for (Product product : products) {
            if (categoryId != null && !product.getCategoryIds().contains(categoryId)) {
                continue;
            }
            ProductSalesAggregate sales = salesByProduct.get(product.getId());
            rows.add(new ProductPerformanceRow(product.getSkuValue(), product.getName(),
                    sales == null ? 0L : sales.unitsSold(),
                    sales == null ? Money.zero() : sales.revenue()));
        }

        Comparator<ProductPerformanceRow> byUnitsDesc = Comparator
                .comparingLong(ProductPerformanceRow::unitsSold).reversed()
                .thenComparing(ProductPerformanceRow::name);
        Comparator<ProductPerformanceRow> byRevenueDesc = Comparator
                .comparing((ProductPerformanceRow row) -> row.revenue().getAmount()).reversed()
                .thenComparing(ProductPerformanceRow::name);
        Comparator<ProductPerformanceRow> byUnitsAsc = Comparator
                .comparingLong(ProductPerformanceRow::unitsSold)
                .thenComparing(Comparator
                        .comparing((ProductPerformanceRow row) -> row.revenue().getAmount()).reversed())
                .thenComparing(ProductPerformanceRow::name);

        List<ProductPerformanceRow> topSellers = rows.stream()
                .sorted(byUnitsDesc).limit(TOP_N).toList();
        List<ProductPerformanceRow> topByRevenue = rows.stream()
                .sorted(byRevenueDesc).limit(TOP_N).toList();
        List<ProductPerformanceRow> bottomPerformers = rows.stream()
                .sorted(byUnitsAsc).limit(TOP_N).toList();

        return new ProductPerformanceReport(topSellers, topByRevenue, bottomPerformers,
                unitsByCategory(products, salesByProduct, categoryId));
    }

    private List<CategoryUnits> unitsByCategory(List<Product> products,
                                                Map<String, ProductSalesAggregate> salesByProduct,
                                                Long categoryId) {
        Map<Long, Long> unitsByCategoryId = new LinkedHashMap<>();
        for (Product product : products) {
            if (categoryId != null && !product.getCategoryIds().contains(categoryId)) {
                continue;
            }
            ProductSalesAggregate sales = salesByProduct.get(product.getId());
            if (sales == null || sales.unitsSold() == 0L) {
                continue;
            }
            for (Long category : product.getCategoryIds()) {
                unitsByCategoryId.merge(category, sales.unitsSold(), Long::sum);
            }
        }
        Map<Long, String> namesById = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        return unitsByCategoryId.entrySet().stream()
                .map(entry -> new CategoryUnits(
                        namesById.getOrDefault(entry.getKey(), "Category #" + entry.getKey()),
                        entry.getValue()))
                .sorted(Comparator.comparingLong(CategoryUnits::unitsSold).reversed()
                        .thenComparing(CategoryUnits::categoryName))
                .toList();
    }
}
