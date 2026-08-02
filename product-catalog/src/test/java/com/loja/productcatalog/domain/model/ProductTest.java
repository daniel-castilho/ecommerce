package com.loja.productcatalog.domain.model;

import com.loja.productcatalog.domain.exception.ProductValidationException;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    void shouldConstructValidProduct() {
        Product product = product();

        assertThat(product.getId()).isEqualTo("p1");
        assertThat(product.getSku().getValue()).isEqualTo("ABC-123");
        assertThat(product.getSlug().getValue()).isEqualTo("abc-123");
        assertThat(product.getName()).isEqualTo("Smartphone");
        assertThat(product.getPrice().getAmount()).isEqualByComparingTo("1000.00");
        assertThat(product.getStock()).isEqualTo(5);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.getCategoryIds()).containsExactly(1L);
        assertThat(product.getImages()).isEmpty();
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "  ",
                null, null, money("1000.00"), null, 5, ProductStatus.DRAFT, null, null, null,
                Set.of(1L), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNameOverMaxLength() {
        assertThatThrownBy(() -> new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "x".repeat(201),
                null, null, money("1000.00"), null, 5, ProductStatus.DRAFT, null, null, null,
                Set.of(1L), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullSku() {
        assertThatThrownBy(() -> new Product("p1", null, new Slug("abc-123"), "Name",
                null, null, money("1000.00"), null, 5, ProductStatus.DRAFT, null, null, null,
                Set.of(1L), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectCompareAtPriceBelowOrEqualPrice() {
        assertThatThrownBy(() -> new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Name",
                null, null, money("1000.00"), money("1000.00"), 5, ProductStatus.DRAFT, null, null, null,
                Set.of(1L), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Name",
                null, null, money("1000.00"), money("999.99"), 5, ProductStatus.DRAFT, null, null, null,
                Set.of(1L), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptCompareAtPriceAbovePrice() {
        Product product = new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Name",
                null, null, money("1000.00"), money("1200.00"), 5, ProductStatus.DRAFT, null, null, null,
                Set.of(1L), List.of());
        assertThat(product.getCompareAtPrice().getAmount()).isEqualByComparingTo("1200.00");
    }

    @Test
    void shouldRejectNegativeMoney() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectZeroPriceOnProduct() {
        assertThatThrownBy(() -> new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Name",
                null, null, new Money(BigDecimal.ZERO), null, 5, ProductStatus.DRAFT, null, null, null,
                Set.of(1L), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDefaultStatusToDraftWhenNull() {
        Product product = new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Name",
                null, null, money("1000.00"), null, 5, null, null, null, null,
                Set.of(1L), List.of());
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    void shouldMarkExactlyOnePrimaryImage() {
        Product product = product(image(1L, 0, true), image(2L, 1, true));

        product.markImageAsPrimary(1L);

        assertThat(product.getImages()).filteredOn(ProductImage::isPrimary).hasSize(1);
        assertThat(product.getImages().get(0).isPrimary()).isTrue();
    }

    @Test
    void shouldRejectMarkingUnknownImageAsPrimary() {
        Product product = product(image(1L, 0, true));

        assertThatThrownBy(() -> product.markImageAsPrimary(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPromoteNextImageWhenRemovingPrimary() {
        Product product = product(image(1L, 0, true), image(2L, 1, false));

        product.removeImage(1L);

        assertThat(product.getImages()).hasSize(1);
        assertThat(product.getImages().get(0).getId()).isEqualTo(2L);
        assertThat(product.getImages().get(0).isPrimary()).isTrue();
    }

    @Test
    void shouldPromoteLowestPositionWhenRemovingPrimary() {
        Product product = product(image(1L, 2, true), image(2L, 0, false), image(3L, 1, false));

        product.removeImage(1L);

        ProductImage promoted = product.getImages().stream().filter(ProductImage::isPrimary).findFirst().orElseThrow();
        assertThat(promoted.getId()).isEqualTo(2L);
    }

    @Test
    void shouldRemoveNonPrimaryImageWithoutPromotion() {
        Product product = product(image(1L, 0, true), image(2L, 1, false));

        product.removeImage(2L);

        assertThat(product.getImages()).hasSize(1);
        assertThat(product.getImages().get(0).isPrimary()).isTrue();
    }

    @Test
    void shouldRejectRemovingUnknownImage() {
        Product product = product(image(1L, 0, true));

        assertThatThrownBy(() -> product.removeImage(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowTransitionFromDraftToActive() {
        assertThat(product().canTransitionTo(ProductStatus.ACTIVE)).isTrue();
    }

    @Test
    void shouldNotTransitionArchivedToDraft() {
        Product archived = new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Name",
                null, null, money("1000.00"), null, 5, ProductStatus.ARCHIVED, null, null, null,
                Set.of(1L), List.of());
        assertThat(archived.canTransitionTo(ProductStatus.DRAFT)).isFalse();
        assertThat(archived.canTransitionTo(ProductStatus.ACTIVE)).isFalse();
        assertThat(archived.canTransitionTo(ProductStatus.INACTIVE)).isFalse();
    }

    @Test
    void shouldNotTransitionToSameStatus() {
        Product product = product();
        assertThat(product.canTransitionTo(ProductStatus.DRAFT)).isFalse();
    }

    @Test
    void shouldNotTransitionToNull() {
        assertThat(product().canTransitionTo(null)).isFalse();
    }

    @Test
    void shouldAllowActiveToInactiveAndDraft() {
        Product active = new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Name",
                null, null, money("1000.00"), null, 5, ProductStatus.ACTIVE, null, null, null,
                Set.of(1L), List.of());
        assertThat(active.canTransitionTo(ProductStatus.INACTIVE)).isTrue();
        assertThat(active.canTransitionTo(ProductStatus.DRAFT)).isTrue();
        assertThat(active.canTransitionTo(ProductStatus.ARCHIVED)).isTrue();
    }

    @Test
    void shouldRejectPublishingWithoutImages() {
        assertThatThrownBy(() -> product().validateForPublishing())
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("at least one image is required");
    }

    @Test
    void shouldRejectPublishingWithImageMissingAltText() {
        Product product = product(image(1L, 0, true));

        assertThatThrownBy(product::validateForPublishing)
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("alt text");
    }

    @Test
    void shouldRejectPublishingWithoutCategories() {
        Product product = new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Name",
                null, null, money("1000.00"), null, 5, ProductStatus.DRAFT, null, null, null,
                Set.of(), List.of(image(1L, 0, true)));
        product.getImages().forEach(image -> image.setAltText("alt"));

        assertThatThrownBy(product::validateForPublishing)
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("at least one category is required");
    }

    @Test
    void shouldPassPublishingValidationWhenComplete() {
        Product product = product(image(1L, 0, true));
        product.getImages().forEach(image -> image.setAltText("A smartphone"));

        product.validateForPublishing();
    }

    @Test
    void shouldListEveryUnmetPublishCondition() {
        Product empty = new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Smartphone",
                null, null, money("1000.00"), null, 5, ProductStatus.DRAFT, null, null, null,
                Set.of(), List.of());

        assertThatThrownBy(empty::validateForPublishing)
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("at least one image is required")
                .hasMessageContaining("at least one category is required");
    }

    @Test
    void shouldReserveStock() {
        Product product = product();
        product.reserveStock(2);
        assertThat(product.getStock()).isEqualTo(3);
    }

    @Test
    void shouldRejectReservingMoreThanStock() {
        Product product = product();
        assertThatThrownBy(() -> product.reserveStock(6))
                .isInstanceOf(IllegalStateException.class);
        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    void shouldRejectNonPositiveReservation() {
        assertThatThrownBy(() -> product().reserveStock(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product().reserveStock(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Product product(ProductImage... images) {
        return new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Smartphone",
                null, null, money("1000.00"), null, 5, ProductStatus.DRAFT, null, null, null,
                new HashSet<>(Set.of(1L)), new ArrayList<>(List.of(images)));
    }

    private static ProductImage image(Long id, int position, boolean primary) {
        return new ProductImage(id, "products/ABC-123/img.webp", null, position, primary);
    }

    private static Money money(String amount) {
        return new Money(new BigDecimal(amount));
    }
}
