package com.loja.productcatalog.domain.port.in;

import java.util.List;

import com.loja.productcatalog.domain.model.Category;

/**
 * Lists categories available in the product catalog.
 */
public interface ListCategoriesUseCase {
    List<Category> listCategories();
}
