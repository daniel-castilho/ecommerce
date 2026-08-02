package com.loja.productcatalog.domain.port.in;

import com.loja.productcatalog.domain.model.Product;

/** Input port: soft-deletes a product by transitioning it to {@code ARCHIVED}. */
public interface ArchiveProductUseCase {
    Product archive(String productId);
}
