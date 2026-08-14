package com.loja.productcatalog.domain.port.out;

import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.application.dto.ProductSearchHit;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort {
    List<Product> findByName(String name);
    List<Product> findAll();
    Optional<Product> findById(String id);
    Optional<Product> findBySku(Sku sku);
    Optional<Product> findBySlug(Slug slug);
    boolean existsBySku(Sku sku);
    boolean existsBySlug(Slug slug);
    PageResult<Product> search(ProductSearchCriteria criteria);
    PageResult<ProductSearchHit> searchWithSnippets(ProductSearchCriteria criteria);
    Product save(Product product);
    int decrementStock(String productId, int quantity);
}
