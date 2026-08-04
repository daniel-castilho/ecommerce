package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.loja.admindashboard.domain.port.in.CreateProductForAdminUseCase;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.shared.domain.Money;

class ProductCreateBeanTest {

    @Test
    void submit_createsProductAndRedirectsToList() {
        CreateProductForAdminUseCase createProductForAdminUseCase = mock(CreateProductForAdminUseCase.class);
        ProductCreateBean bean = new ProductCreateBean();
        bean.setCreateProductForAdminUseCase(createProductForAdminUseCase);

        bean.setSku("SKU-NEW");
        bean.setName("Mechanical Keyboard");
        bean.setSlug("mechanical-keyboard");
        bean.setShortDescription("Hot-swappable");
        bean.setDescription("RGB and compact");
        bean.setPrice(new BigDecimal("149.90"));
        bean.setCompareAtPrice(new BigDecimal("199.90"));
        bean.setStock(12);
        bean.setWeightGrams(900);
        bean.setMetaTitle("Keyboard sale");
        bean.setMetaDescription("Compact keyboard");
        bean.setSelectedCategoryIds(Set.of(4L, 8L));

        Product created = new Product("p-100", new Sku("SKU-NEW"), new Slug("mechanical-keyboard"),
                "Mechanical Keyboard", "Hot-swappable", "RGB and compact",
                new Money(new BigDecimal("149.90")), new Money(new BigDecimal("199.90")), 12,
                ProductStatus.DRAFT, 900, "Keyboard sale", "Compact keyboard", Set.of(4L, 8L), List.of());
        when(createProductForAdminUseCase.create(argThat(command ->
                command.sku().equals("SKU-NEW")
                        && command.name().equals("Mechanical Keyboard")
                        && command.slug().equals("mechanical-keyboard")
                        && command.price().getAmount().compareTo(new BigDecimal("149.90")) == 0
                        && command.categoryIds().equals(Set.of(4L, 8L)))))
                .thenReturn(created);

        String outcome = bean.submit();

        assertThat(outcome).isEqualTo("/admin-dashboard/products/list.xhtml?faces-redirect=true");
        verify(createProductForAdminUseCase).create(argThat(command ->
                command.sku().equals("SKU-NEW")
                        && command.name().equals("Mechanical Keyboard")
                        && command.slug().equals("mechanical-keyboard")
                        && command.price().getAmount().compareTo(new BigDecimal("149.90")) == 0
                        && command.categoryIds().equals(Set.of(4L, 8L))));
    }
}
