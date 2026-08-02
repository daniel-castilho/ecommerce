package com.loja.productcatalog.adapter.out.persistence;

import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.application.dto.SortDirection;
import com.loja.productcatalog.domain.exception.DuplicateSkuException;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Output adapter: implements ProductRepositoryPort using JPA.
 * The DB unique constraints on sku/slug are the safety net behind the
 * application-layer uniqueness pre-checks; a sku violation surfaces here
 * as {@link DuplicateSkuException}.
 */
@ApplicationScoped
public class ProductRepositoryAdapter implements ProductRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Override
    public List<Product> findByName(String name) {
        return em.createQuery(
                        "SELECT p FROM ProductJpaEntity p WHERE lower(p.name) LIKE :name",
                        ProductJpaEntity.class)
                .setHint("jakarta.persistence.loadgraph", detailsGraph())
                .setParameter("name", "%" + name.toLowerCase() + "%")
                .getResultList()
                .stream().map(ProductJpaMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Product> findAll() {
        return em.createQuery("SELECT p FROM ProductJpaEntity p", ProductJpaEntity.class)
                .setHint("jakarta.persistence.loadgraph", detailsGraph())
                .getResultList()
                .stream().map(ProductJpaMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(em.find(ProductJpaEntity.class, id))
                .map(ProductJpaMapper::toDomain);
    }

    @Override
    public Optional<Product> findBySku(Sku sku) {
        return em.createQuery("SELECT p FROM ProductJpaEntity p WHERE p.sku = :sku", ProductJpaEntity.class)
                .setParameter("sku", sku.getValue())
                .getResultStream().map(ProductJpaMapper::toDomain).findFirst();
    }

    @Override
    public Optional<Product> findBySlug(Slug slug) {
        return em.createQuery("SELECT p FROM ProductJpaEntity p WHERE p.slug = :slug", ProductJpaEntity.class)
                .setParameter("slug", slug.getValue())
                .getResultStream().map(ProductJpaMapper::toDomain).findFirst();
    }

    @Override
    public boolean existsBySku(Sku sku) {
        return em.createQuery("SELECT COUNT(p) FROM ProductJpaEntity p WHERE p.sku = :sku", Long.class)
                .setParameter("sku", sku.getValue())
                .setMaxResults(1)
                .getSingleResult() > 0;
    }

    @Override
    public boolean existsBySlug(Slug slug) {
        return em.createQuery("SELECT COUNT(p) FROM ProductJpaEntity p WHERE p.slug = :slug", Long.class)
                .setParameter("slug", slug.getValue())
                .setMaxResults(1)
                .getSingleResult() > 0;
    }

    @Override
    public PageResult<Product> search(ProductSearchCriteria criteria) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<ProductJpaEntity> query = cb.createQuery(ProductJpaEntity.class);
        Root<ProductJpaEntity> root = query.from(ProductJpaEntity.class);
        query.select(root)
                .where(filters(cb, root, criteria).toArray(Predicate[]::new))
                .orderBy(sortOrders(cb, root, criteria));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<ProductJpaEntity> countRoot = count.from(ProductJpaEntity.class);
        count.select(cb.count(countRoot))
                .where(filters(cb, countRoot, criteria).toArray(Predicate[]::new));

        long totalElements = em.createQuery(count).getSingleResult();

        List<Product> items = em.createQuery(query)
                .setHint("jakarta.persistence.loadgraph", detailsGraph())
                .setFirstResult(criteria.page() * criteria.pageSize())
                .setMaxResults(criteria.pageSize())
                .getResultList().stream().map(ProductJpaMapper::toDomain).collect(Collectors.toList());

        return new PageResult<>(items, totalElements, criteria.page(), criteria.pageSize());
    }

    @Override
    public Product save(Product product) {
        try {
            ProductJpaEntity merged = em.merge(ProductJpaMapper.toJpa(product));
            em.flush();
            return ProductJpaMapper.toDomain(merged);
        } catch (PersistenceException e) {
            if (isSkuConstraintViolation(e)) {
                throw new DuplicateSkuException("Product SKU already exists: " + product.getSkuValue());
            }
            throw e;
        }
    }

    @Override
    public int decrementStock(String productId, int quantity) {
        return em.createQuery(
                        "UPDATE ProductJpaEntity p SET p.stock = p.stock - :qty " +
                                "WHERE p.id = :id AND p.stock >= :qty")
                .setParameter("qty", quantity)
                .setParameter("id", productId)
                .executeUpdate();
    }

    private List<Predicate> filters(CriteriaBuilder cb, Root<ProductJpaEntity> root, ProductSearchCriteria criteria) {
        List<Predicate> predicates = new ArrayList<>();
        if (criteria.nameOrSkuContains() != null && !criteria.nameOrSkuContains().isBlank()) {
            String like = "%" + criteria.nameOrSkuContains().trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.<String>get("name")), like),
                    cb.like(cb.lower(root.<String>get("sku")), like)));
        }
        if (criteria.categoryId() != null) {
            predicates.add(cb.isMember(criteria.categoryId(), root.<Set<Long>>get("categoryIds")));
        }
        if (criteria.minPrice() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), criteria.minPrice()));
        }
        if (criteria.maxPrice() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), criteria.maxPrice()));
        }
        if (criteria.status() != null) {
            predicates.add(cb.equal(root.get("status"), criteria.status()));
        } else {
            predicates.add(cb.notEqual(root.get("status"), ProductStatus.ARCHIVED));
        }
        return predicates;
    }

    private List<Order> sortOrders(CriteriaBuilder cb, Root<ProductJpaEntity> root, ProductSearchCriteria criteria) {
        String attribute = switch (criteria.sortField()) {
            case NAME -> "name";
            case PRICE -> "price";
            case CREATED_AT -> "createdAt";
        };
        Path<Object> path = root.get(attribute);
        Order order = criteria.sortDirection() == SortDirection.DESC ? cb.desc(path) : cb.asc(path);
        return List.of(order, cb.asc(root.get("id")));
    }

    private EntityGraph<ProductJpaEntity> detailsGraph() {
        EntityGraph<ProductJpaEntity> graph = em.createEntityGraph(ProductJpaEntity.class);
        graph.addAttributeNodes("categoryIds", "images");
        return graph;
    }

    private boolean isSkuConstraintViolation(PersistenceException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getClass().getSimpleName().endsWith("ConstraintViolationException")
                    && cause.getMessage() != null
                    && cause.getMessage().toLowerCase().contains("sku")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
