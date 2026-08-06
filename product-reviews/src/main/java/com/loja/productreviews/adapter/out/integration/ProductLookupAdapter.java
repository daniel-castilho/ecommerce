package com.loja.productreviews.adapter.out.integration;

import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.productreviews.domain.port.out.ProductLookupPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Thin adapter for {@link ProductLookupPort} that delegates to the public
 * {@code ProductRepositoryPort} of the product-catalog module.
 */
@ApplicationScoped
public class ProductLookupAdapter implements ProductLookupPort {

    private final ProductRepositoryPort productRepository;

    @Inject
    public ProductLookupAdapter(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public boolean existsById(String productId) {
        return productRepository.findById(productId).isPresent();
    }
}
