package com.loja.wishlist.adapter.out.integration;

import java.util.Comparator;
import java.util.Optional;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.wishlist.application.dto.ProductSnapshot;
import com.loja.wishlist.domain.port.out.ProductLookupPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Thin adapter for {@link ProductLookupPort} that delegates to the public
 * ports of the product-catalog module. Only ACTIVE products are returned.
 */
@ApplicationScoped
public class ProductLookupAdapter implements ProductLookupPort {

    private final ProductRepositoryPort productRepository;
    private final ProductImageStoragePort imageStorage;

    @Inject
    public ProductLookupAdapter(ProductRepositoryPort productRepository,
                                ProductImageStoragePort imageStorage) {
        this.productRepository = productRepository;
        this.imageStorage = imageStorage;
    }

    @Override
    public Optional<ProductSnapshot> findActiveById(String productId) {
        return productRepository.findById(productId)
                .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                .map(this::toSnapshot);
    }

    private ProductSnapshot toSnapshot(Product product) {
        String imageUrl = primaryImageUrl(product);
        return new ProductSnapshot(
                product.getId(),
                product.getName(),
                product.getSlugValue(),
                product.getPrice().getAmount(),
                imageUrl);
    }

    private String primaryImageUrl(Product product) {
        Optional<ProductImage> primary = product.getImages().stream()
                .filter(ProductImage::isPrimary)
                .findFirst();
        ProductImage image = primary.orElseGet(() -> product.getImages().stream()
                .min(Comparator.comparingInt(ProductImage::getPosition))
                .orElse(null));
        if (image == null) {
            return null;
        }
        return imageStorage.publicUrlFor(image.getObjectKey());
    }
}
