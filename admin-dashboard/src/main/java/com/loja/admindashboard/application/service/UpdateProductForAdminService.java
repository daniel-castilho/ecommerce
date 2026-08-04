package com.loja.admindashboard.application.service;

import com.loja.admindashboard.domain.port.in.UpdateProductForAdminUseCase;
import com.loja.productcatalog.application.dto.UpdateProductCommand;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.in.UpdateProductImageUseCase;
import com.loja.productcatalog.domain.port.in.UpdateProductUseCase;
import com.loja.productcatalog.domain.port.in.UploadProductImageUseCase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application service that delegates product updates to the product-catalog module.
 */
@ApplicationScoped
public class UpdateProductForAdminService implements UpdateProductForAdminUseCase {

    private final UpdateProductUseCase updateProductUseCase;
    private final UploadProductImageUseCase uploadProductImageUseCase;
    private final UpdateProductImageUseCase updateProductImageUseCase;

    @Inject
    public UpdateProductForAdminService(UpdateProductUseCase updateProductUseCase,
            UploadProductImageUseCase uploadProductImageUseCase,
            UpdateProductImageUseCase updateProductImageUseCase) {
        this.updateProductUseCase = updateProductUseCase;
        this.uploadProductImageUseCase = uploadProductImageUseCase;
        this.updateProductImageUseCase = updateProductImageUseCase;
    }

    @Override
    public Product update(String productId, UpdateProductCommand command) {
        return updateProductUseCase.update(productId, command);
    }

    @Override
    public Product uploadImage(String productId, com.loja.productcatalog.application.dto.UploadProductImageCommand command) {
        return uploadProductImageUseCase.uploadImage(productId, command);
    }

    @Override
    public Product updateImageMeta(String productId, Long primaryImageId, java.util.Map<Long, String> altTextByImageId) {
        return updateProductImageUseCase.updateImageMeta(productId, primaryImageId, altTextByImageId);
    }

    @Override
    public Product moveImage(String productId, Long imageId, int newPosition) {
        return updateProductImageUseCase.moveImage(productId, imageId, newPosition);
    }
}
