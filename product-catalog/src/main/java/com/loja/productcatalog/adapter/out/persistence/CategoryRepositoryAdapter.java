package com.loja.productcatalog.adapter.out.persistence;

import com.loja.productcatalog.application.service.CategoryTreeCache;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Output adapter: implements CategoryRepositoryPort using JPA.
 * The full tree is served from the {@link CategoryTreeCache} and invalidated on any
 * save/delete so a mutation is immediately visible on the next read.
 */
@ApplicationScoped
public class CategoryRepositoryAdapter implements CategoryRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Inject
    CategoryTreeCache cache;

    @Override
    public Optional<Category> findById(Long id) {
        return em.createQuery(
                        "SELECT c FROM CategoryJpaEntity c LEFT JOIN FETCH c.parent WHERE c.id = :id",
                        CategoryJpaEntity.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst()
                .map(this::withChildren);
    }

    @Override
    public Optional<Category> findBySlug(Slug slug) {
        return em.createQuery(
                        "SELECT c FROM CategoryJpaEntity c LEFT JOIN FETCH c.parent WHERE c.slug = :slug",
                        CategoryJpaEntity.class)
                .setParameter("slug", slug.getValue())
                .getResultStream()
                .findFirst()
                .map(this::withChildren);
    }

    @Override
    public boolean existsById(Long id) {
        return em.createQuery("SELECT COUNT(c) FROM CategoryJpaEntity c WHERE c.id = :id", Long.class)
                .setParameter("id", id)
                .setMaxResults(1)
                .getSingleResult() > 0;
    }

    @Override
    public List<Category> findAll() {
        return cache.getOrLoad(this::loadTree);
    }

    @Override
    public List<Category> findAllActive() {
        return filterActive(findAll());
    }

    @Override
    public Category save(Category category) {
        CategoryJpaEntity entity = CategoryJpaMapper.toJpa(category);
        if (category.getParent() != null) {
            if (category.getParent().getId() == null) {
                throw new IllegalArgumentException("Parent category must be persisted before saving a child");
            }
            entity.setParent(em.getReference(CategoryJpaEntity.class, category.getParent().getId()));
        }
        CategoryJpaEntity merged = em.merge(entity);
        em.flush();
        cache.invalidate();
        return CategoryJpaMapper.toDomain(merged);
    }

    @Override
    public void delete(Long id) {
        CategoryJpaEntity entity = em.find(CategoryJpaEntity.class, id);
        if (entity != null) {
            em.remove(entity);
            em.flush();
            cache.invalidate();
        }
    }

    private List<Category> loadTree() {
        List<CategoryJpaEntity> entities = em.createQuery(
                        "SELECT c FROM CategoryJpaEntity c LEFT JOIN FETCH c.parent ORDER BY c.position, c.id",
                        CategoryJpaEntity.class)
                .getResultList();

        Map<Long, Category> byId = new LinkedHashMap<>();
        for (CategoryJpaEntity e : entities) {
            byId.put(e.getId(), CategoryJpaMapper.toDomain(e));
        }

        List<Category> roots = new ArrayList<>();
        for (CategoryJpaEntity e : entities) {
            Category node = byId.get(e.getId());
            if (e.getParent() != null) {
                Category parent = byId.get(e.getParent().getId());
                node.setParent(parent);
                parent.addChild(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    private Category withChildren(CategoryJpaEntity e) {
        Category category = CategoryJpaMapper.toDomain(e);
        List<CategoryJpaEntity> children = em.createQuery(
                        "SELECT c FROM CategoryJpaEntity c LEFT JOIN FETCH c.parent " +
                                "WHERE c.parent.id = :parentId ORDER BY c.position, c.id",
                        CategoryJpaEntity.class)
                .setParameter("parentId", e.getId())
                .getResultList();
        for (CategoryJpaEntity child : children) {
            Category childDomain = CategoryJpaMapper.toDomain(child);
            childDomain.setParent(category);
            category.addChild(childDomain);
        }
        return category;
    }

    private List<Category> filterActive(List<Category> nodes) {
        List<Category> active = new ArrayList<>();
        for (Category node : nodes) {
            if (node.isActive()) {
                active.add(node);
            }
        }
        return active;
    }
}
