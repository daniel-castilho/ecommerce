package com.loja.productcatalog.domain.port.in;

import java.util.Optional;

import com.loja.productcatalog.domain.model.Product;

/**
 * Finds a product by its identifier.
 */
public interface FindProductByIdUseCase {
    Optional<Product> findById(String productId);
}
