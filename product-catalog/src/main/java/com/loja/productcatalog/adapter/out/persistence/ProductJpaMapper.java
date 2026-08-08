package com.loja.productcatalog.adapter.out.persistence;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.shared.domain.Money;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Sole place where a {@link Product} domain object is converted to/from
 * {@link ProductJpaEntity}. Nothing outside the persistence adapter may reach
 * into a JPA entity directly.
 */
public final class ProductJpaMapper {

    private ProductJpaMapper() {}

    public static ProductJpaEntity toJpa(Product p) {
        ProductJpaEntity e = new ProductJpaEntity();
        e.setId(p.getId());
        e.setSku(p.getSkuValue());
        e.setSlug(p.getSlugValue());
        e.setName(p.getName());
        e.setShortDescription(p.getShortDescription());
        e.setDescription(p.getDescription());
        e.setPrice(p.getPrice().getAmount());
        if (p.getCompareAtPrice() != null) {
            e.setCompareAtPrice(p.getCompareAtPrice().getAmount());
        }
        if (p.getCostPrice() != null) {
            e.setCostPrice(p.getCostPrice().getAmount());
        }
        e.setStock(p.getStock());
        e.setStatus(p.getStatus());
        e.setVersion(p.getVersion());
        e.setWeightGrams(p.getWeightGrams());
        e.setMetaTitle(p.getMetaTitle());
        e.setMetaDescription(p.getMetaDescription());
        e.setCategoryIds(new HashSet<>(p.getCategoryIds()));
        for (ProductImage image : p.getImages()) {
            e.getImages().add(new ProductImageJpaEntity(
                    image.getId(), e, image.getObjectKey(), image.getAltText(),
                    image.getPosition(), image.isPrimary()));
        }
        return e;
    }

    public static Product toDomain(ProductJpaEntity e) {
        Money compareAtPrice = e.getCompareAtPrice() != null ? new Money(e.getCompareAtPrice()) : null;
        Money costPrice = e.getCostPrice() != null ? new Money(e.getCostPrice()) : null;
        List<ProductImage> images = e.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImageJpaEntity::getPosition))
                .map(image -> new ProductImage(
                        image.getId(), image.getObjectKey(), image.getAltText(),
                        image.getPosition(), image.isPrimary()))
                .toList();
        Product product = new Product(
                e.getId(),
                new Sku(e.getSku()),
                new Slug(e.getSlug()),
                e.getName(),
                e.getShortDescription(),
                e.getDescription(),
                new Money(e.getPrice()),
                compareAtPrice,
                costPrice,
                e.getStock(),
                e.getStatus(),
                e.getWeightGrams(),
                e.getMetaTitle(),
                e.getMetaDescription(),
                e.getCategoryIds(),
                images);
        product.setVersion(e.getVersion());
        return product;
    }
}
