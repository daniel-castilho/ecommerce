package com.loja.productcatalog.domain.port.in;

import com.loja.productcatalog.domain.model.Product;

/** Input port: transitions a product from ARCHIVED back to ACTIVE. */
public interface ActivateProductUseCase {
    Product activate(String productId);
}
