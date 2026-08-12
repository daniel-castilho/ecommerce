package com.loja.ordercheckout.adapter.out.integration;

import com.loja.ordercheckout.application.dto.ProductSnapshot;
import com.loja.ordercheckout.domain.port.out.ProductLookupPort;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.Optional;

/**
 * Thin adapter for {@link ProductLookupPort} that delegates to the public ports
 * of the product-catalog module. Only ACTIVE products are returned.
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
        return new ProductSnapshot(
                product.getId(),
                product.getName(),
                product.getSlugValue(),
                product.getPrice(),
                primaryImageUrl(product),
                product.getCategoryIds());
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
