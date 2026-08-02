package com.loja.productcatalog.domain.port.out;

import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Slug;
import java.util.List;
import java.util.Optional;

/** Output port (driven port): persistence of the product category tree. */
public interface CategoryRepositoryPort {
    Optional<Category> findById(Long id);
    Optional<Category> findBySlug(Slug slug);
    boolean existsById(Long id);
    List<Category> findAll();
    List<Category> findAllActive();
    Category save(Category category);
    void delete(Long id);
}
