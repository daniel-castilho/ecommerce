package com.loja.productcatalog.adapter.out.persistence;

import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Slug;

/**
 * Sole place where a {@link Category} domain object is converted to/from
 * {@link CategoryJpaEntity}. The self-referencing {@code parent} is resolved by the
 * adapter (it needs the EntityManager for a {@code getReference}); this mapper only
 * handles scalar fields, so the parent is left unset for the tree assembly.
 */
public final class CategoryJpaMapper {

    private CategoryJpaMapper() {}

    public static CategoryJpaEntity toJpa(Category c) {
        CategoryJpaEntity e = new CategoryJpaEntity();
        e.setId(c.getId());
        e.setName(c.getName());
        e.setSlug(c.getSlug().getValue());
        e.setPosition(c.getPosition());
        e.setActive(c.isActive());
        e.setVersion(c.getVersion());
        return e;
    }

    public static Category toDomain(CategoryJpaEntity e) {
        Category c = new Category(
                e.getId(),
                e.getName(),
                new Slug(e.getSlug()),
                null,
                e.getPosition(),
                e.isActive());
        c.setVersion(e.getVersion());
        return c;
    }
}
