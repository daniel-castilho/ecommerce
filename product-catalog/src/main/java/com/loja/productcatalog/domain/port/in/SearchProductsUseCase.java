package com.loja.productcatalog.domain.port.in;

import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.application.dto.ProductSearchHit;
import com.loja.productcatalog.domain.model.Product;
import java.util.List;

public interface SearchProductsUseCase {
    List<Product> findByName(String name);
    List<Product> findAll();
    PageResult<Product> search(ProductSearchCriteria criteria);
    PageResult<ProductSearchHit> searchWithSnippets(ProductSearchCriteria criteria);
}
