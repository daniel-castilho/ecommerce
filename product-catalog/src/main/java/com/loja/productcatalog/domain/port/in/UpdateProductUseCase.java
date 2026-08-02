package com.loja.productcatalog.domain.port.in;

import com.loja.productcatalog.application.dto.UpdateProductCommand;
import com.loja.productcatalog.domain.model.Product;

/** Input port: updates the editable fields of an existing product. */
public interface UpdateProductUseCase {
    Product update(String productId, UpdateProductCommand command);
}
