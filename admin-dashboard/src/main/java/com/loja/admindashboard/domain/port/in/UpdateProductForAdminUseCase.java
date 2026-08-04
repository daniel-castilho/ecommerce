package com.loja.admindashboard.domain.port.in;

import com.loja.productcatalog.application.dto.UpdateProductCommand;
import com.loja.productcatalog.domain.model.Product;

/**
 * Administrative use case for updating existing products from the back office.
 */
public interface UpdateProductForAdminUseCase {
    Product update(String productId, UpdateProductCommand command);
    Product uploadImage(String productId, com.loja.productcatalog.application.dto.UploadProductImageCommand command);
    Product updateImageMeta(String productId, Long primaryImageId, java.util.Map<Long, String> altTextByImageId);
    Product moveImage(String productId, Long imageId, int newPosition);
}
