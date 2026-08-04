package com.loja.admindashboard.application.service;

import com.loja.admindashboard.domain.port.in.CreateProductForAdminUseCase;
import com.loja.productcatalog.application.dto.CreateProductCommand;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.in.CreateProductUseCase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application service that delegates product creation to the product-catalog module.
 */
@ApplicationScoped
public class CreateProductForAdminService implements CreateProductForAdminUseCase {

    private final CreateProductUseCase createProductUseCase;

    @Inject
    public CreateProductForAdminService(CreateProductUseCase createProductUseCase) {
        this.createProductUseCase = createProductUseCase;
    }

    @Override
    public Product create(CreateProductCommand command) {
        return createProductUseCase.create(command);
    }
}
