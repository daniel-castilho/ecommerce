package com.loja.productcatalog.domain.port.in;

import com.loja.productcatalog.domain.model.Product;

/** Input port: transitions a product to {@code ACTIVE}, enforcing the publish guard. */
public interface PublishProductUseCase {
    Product publish(String productId);
}
