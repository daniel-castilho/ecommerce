package com.loja.productcatalog.domain.port.in;

import com.loja.productcatalog.application.dto.CreateProductCommand;
import com.loja.productcatalog.domain.model.Product;

/** Input port: creates a new product (SKU uniqueness and slug generation live in the service). */
public interface CreateProductUseCase {
    Product create(CreateProductCommand command);
}
