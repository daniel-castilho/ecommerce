package com.loja.admindashboard.domain.port.in;

import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.domain.model.Product;

/**
 * Read-only use case for the admin product list and filtering flow.
 */
public interface ListProductsForAdminUseCase {
    PageResult<Product> listProducts(ProductSearchCriteria criteria);
}
