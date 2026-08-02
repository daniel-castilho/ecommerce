package com.loja.productcatalog.adapter.out.persistence;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductJpaMapperTest {

    @Test
    void shouldRoundTripAllFields() {
        Product original = fullProduct();

        ProductJpaEntity entity = ProductJpaMapper.toJpa(original);
        Product restored = ProductJpaMapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getSku()).isEqualTo(new Sku("ABC-123"));
        assertThat(restored.getSlug()).isEqualTo(new Slug("camiseta-basica"));
        assertThat(restored.getName()).isEqualTo("Camiseta Básica");
        assertThat(restored.getShortDescription()).isEqualTo("Camiseta 100% algodão");
        assertThat(restored.getDescription()).isEqualTo("<p>Uma camiseta confortável</p>");
        assertThat(restored.getPrice()).isEqualTo(new Money(new BigDecimal("49.90")));
        assertThat(restored.getCompareAtPrice()).isEqualTo(new Money(new BigDecimal("69.90")));
        assertThat(restored.getStock()).isEqualTo(10);
        assertThat(restored.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(restored.getWeightGrams()).isEqualTo(180);
        assertThat(restored.getMetaTitle()).isEqualTo("Camiseta Básica | Loja");
        assertThat(restored.getMetaDescription()).isEqualTo("Camiseta básica premium");
        assertThat(restored.getCategoryIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(restored.getImages()).hasSize(2);
        assertThat(restored.getImages().get(0).getId()).isEqualTo(11L);
        assertThat(restored.getImages().get(0).getObjectKey()).isEqualTo("products/ABC-123/img1.webp");
        assertThat(restored.getImages().get(0).getAltText()).isEqualTo("Camiseta vista de frente");
        assertThat(restored.getImages().get(0).getPosition()).isEqualTo(0);
        assertThat(restored.getImages().get(0).isPrimary()).isTrue();
        assertThat(restored.getImages().get(1).isPrimary()).isFalse();
    }

    @Test
    void shouldRoundTripVersion() {
        Product original = new Product(
                "p-ver-1", new Sku("V-1"), new Slug("v-1"), "V", null, null,
                new Money(new BigDecimal("1.00")), null, 1, ProductStatus.DRAFT,
                null, null, null, Set.of(), List.of());
        original.setVersion(7L);

        Product restored = ProductJpaMapper.toDomain(ProductJpaMapper.toJpa(original));

        assertThat(restored.getVersion()).isEqualTo(7L);
    }

    @Test
    void shouldRoundTripNullOptionals() {
        Product original = new Product(
                "p2", new Sku("B-1"), new Slug("b-1"), "B", null, null,
                new Money(new BigDecimal("10.00")), null, 0, ProductStatus.DRAFT,
                null, null, null, Set.of(), List.of());

        Product restored = ProductJpaMapper.toDomain(ProductJpaMapper.toJpa(original));

        assertThat(restored.getShortDescription()).isNull();
        assertThat(restored.getDescription()).isNull();
        assertThat(restored.getCompareAtPrice()).isNull();
        assertThat(restored.getWeightGrams()).isNull();
        assertThat(restored.getMetaTitle()).isNull();
        assertThat(restored.getMetaDescription()).isNull();
        assertThat(restored.getCategoryIds()).isEmpty();
        assertThat(restored.getImages()).isEmpty();
        assertThat(restored.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    void shouldKeepImagesOrderedByPosition() {
        Product original = new Product(
                "p3", new Sku("C-1"), new Slug("c-1"), "C", null, null,
                new Money(new BigDecimal("5.00")), null, 1, ProductStatus.DRAFT,
                null, null, null, Set.of(),
                List.of(
                        new ProductImage(1L, "products/C-1/pos2.webp", "second", 1, false),
                        new ProductImage(2L, "products/C-1/pos0.webp", "first", 0, true)));

        Product restored = ProductJpaMapper.toDomain(ProductJpaMapper.toJpa(original));

        assertThat(restored.getImages()).hasSize(2);
        assertThat(restored.getImages().get(0).getPosition()).isZero();
        assertThat(restored.getImages().get(0).isPrimary()).isTrue();
        assertThat(restored.getImages().get(1).getPosition()).isEqualTo(1);
    }

    private Product fullProduct() {
        return new Product(
                "p1",
                new Sku("abc-123"),
                new Slug("camiseta-basica"),
                "Camiseta Básica",
                "Camiseta 100% algodão",
                "<p>Uma camiseta confortável</p>",
                new Money(new BigDecimal("49.90")),
                new Money(new BigDecimal("69.90")),
                10,
                ProductStatus.ACTIVE,
                180,
                "Camiseta Básica | Loja",
                "Camiseta básica premium",
                Set.of(1L, 2L),
                List.of(
                        new ProductImage(11L, "products/ABC-123/img1.webp", "Camiseta vista de frente", 0, true),
                        new ProductImage(12L, "products/ABC-123/img2.webp", "Camiseta vista de costas", 1, false)));
    }
}
