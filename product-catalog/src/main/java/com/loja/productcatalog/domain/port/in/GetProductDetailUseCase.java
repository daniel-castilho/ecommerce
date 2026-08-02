package com.loja.productcatalog.domain.port.in;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.Slug;
import java.util.Optional;

/** Public detail lookup: returns a product only when it is visible in the storefront. */
public interface GetProductDetailUseCase {
    Optional<Product> findActiveBySlug(Slug slug);
}
