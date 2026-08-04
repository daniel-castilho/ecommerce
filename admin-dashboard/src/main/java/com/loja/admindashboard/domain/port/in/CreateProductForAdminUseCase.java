package com.loja.admindashboard.domain.port.in;

import com.loja.productcatalog.application.dto.CreateProductCommand;
import com.loja.productcatalog.domain.model.Product;

/**
 * Administrative use case for creating products from the back-office dashboard.
 */
public interface CreateProductForAdminUseCase {
    Product create(CreateProductCommand command);
}
