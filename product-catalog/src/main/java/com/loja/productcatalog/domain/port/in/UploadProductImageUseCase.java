package com.loja.productcatalog.domain.port.in;

import com.loja.productcatalog.application.dto.UploadProductImageCommand;
import com.loja.productcatalog.domain.model.Product;

/** Input port: uploads an image for a product and persists the resulting image set. */
public interface UploadProductImageUseCase {
    Product uploadImage(String productId, UploadProductImageCommand command);
}
