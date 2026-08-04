package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.loja.admindashboard.domain.port.in.ListProductsForAdminUseCase;
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
        ProductManagementBean bean = new ProductManagementBean();
        bean.setListProductsForAdminUseCase(listProductsForAdminUseCase);
        bean.setCategoryRepository(categoryRepository);

        Product product = new Product("p-1", new Sku("SKU-1"), new Slug("sku-1"), "Keyboard",
                null, null, new Money(new BigDecimal("49.90")), null, 10, ProductStatus.ACTIVE,
                null, null, null, null, List.of());
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, null, null, null, null, 0, 20, false, null, null);
        when(listProductsForAdminUseCase.listProducts(criteria)).thenReturn(new PageResult<>(List.of(product), 1L, 0, 20));

        bean.refresh();

        assertThat(bean.getProducts()).containsExactly(product);
        assertThat(bean.getTotalElements()).isEqualTo(1L);
        verify(listProductsForAdminUseCase).listProducts(criteria);
    }
}
