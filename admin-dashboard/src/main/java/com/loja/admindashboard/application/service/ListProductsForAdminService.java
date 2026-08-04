package com.loja.admindashboard.application.service;

import com.loja.admindashboard.domain.port.in.ListProductsForAdminUseCase;
import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.in.SearchProductsUseCase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application service for the admin product list.
 */
@ApplicationScoped
public class ListProductsForAdminService implements ListProductsForAdminUseCase {

    private final SearchProductsUseCase searchProductsUseCase;

    @Inject
    public ListProductsForAdminService(SearchProductsUseCase searchProductsUseCase) {
        this.searchProductsUseCase = searchProductsUseCase;
    }

    @Override
    public PageResult<Product> listProducts(ProductSearchCriteria criteria) {
        return searchProductsUseCase.search(criteria);
    }
}
