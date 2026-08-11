package com.loja.productcatalog.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Output adapter: implements ProductRepositoryPort using JPA.
 * The DB unique constraints on sku/slug are the safety net behind the
 * application-layer uniqueness pre-checks; a sku violation surfaces here
 * as {@link DuplicateSkuException}.
 *
 * <p><b>Search (FTS epic).</b> Text searches run a native PostgreSQL query that
 * matches on a full-text vector ({@link #FTS_EXPRESSION}, backed by the GIN
 * expression index created in migration V25) and ranks the hits with
 * {@code ts_rank}. The term is also matched with {@code ILIKE} so nothing that a
 * {@code LIKE} search used to find ever disappears; {@code ts_rank} only
 * re-orders. The tsquery is a {@code token:* & token:*} prefix AND built from
 * the user's words (see {@link #buildPrefixTsQuery}), so "smart" matches
 * "smartphone". Queries without a text term keep the Criteria path unchanged.
 */
@ApplicationScoped
public class ProductRepositoryAdapter implements ProductRepositoryPort {

    /**
     * Full-text vector over the searchable text fields. {@code description} is
     * intentionally left out: it is a {@code @Lob} column (stored as {@code oid}
     * in the test schema, {@code text} in production) which the vector cast
     * cannot handle portably. Kept in sync with the expression GIN index in
     * migration V25 — they must produce the same expression tree (table aliases
     * are equivalent for index matching).
     */
    private static final String FTS_EXPRESSION =
            "to_tsvector('english', coalesce(p.name, '') || ' ' || coalesce(p.sku, '') || ' ' || "
                    + "coalesce(p.short_description, ''))";

    /** Words too common to rank by; also keeps the tsquery a valid AND of lexemes. */
    private static final Set<String> ENGLISH_STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from",
            "has", "have", "he", "her", "his", "i", "in", "into", "is", "it", "its",
            "me", "my", "of", "on", "or", "our", "she", "so", "than", "that", "the",
            "their", "them", "there", "these", "they", "this", "to", "was", "we",
            "were", "what", "when", "which", "who", "will", "with", "you", "your");

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
        if (criteria.nameOrSkuContains() != null && !criteria.nameOrSkuContains().isBlank()) {
            String tsquery = buildPrefixTsQuery(criteria.nameOrSkuContains());
            if (!tsquery.isBlank()) {
                return searchByText(criteria, tsquery);
            }
            // no usable FTS tokens (only stopwords/punctuation/digits) → LIKE path
        }
        return searchByCriteria(criteria);
    }

    /** Criteria path: LIKE name/sku + category/price/status filters + explicit sort. */
    private PageResult<Product> searchByCriteria(ProductSearchCriteria criteria) {
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

    // ------------------------------------------------------------------ FTS text search

    /**
     * Full-text path: native SQL so PostgreSQL can use the GIN expression index
     * (V25). The term must match {@code FTS_EXPRESSION @@ to_tsquery(...)} OR a
     * plain {@code ILIKE} (no result regressions), and hits are ordered by
     * {@code ts_rank} descending first — the requested sort is the tie-breaker.
     * Callers only reach this method with a non-blank, pre-validated tsquery
     * ({@link #buildPrefixTsQuery} drops stopwords and digit-only tokens), so
     * {@code to_tsquery} is always fed a valid prefix AND.
     */
    private PageResult<Product> searchByText(ProductSearchCriteria criteria, String tsquery) {
        String like = "%" + criteria.nameOrSkuContains().trim().toLowerCase(Locale.ROOT) + "%";
        List<Object> whereValues = ftsParamValues(criteria, tsquery, like);

        FtsBindSlots countSlots = new FtsBindSlots();
        String where = ftsWhere(criteria, countSlots);
        Query countQuery = em.createNativeQuery("SELECT COUNT(*) FROM tb_product p " + where);
        bindPositional(countQuery, whereValues);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        FtsBindSlots pageSlots = new FtsBindSlots();
        String pageSql = "SELECT p.id FROM tb_product p " + ftsWhere(criteria, pageSlots)
                + " " + ftsOrderBy(criteria, pageSlots);
        List<Object> pageValues = new ArrayList<>(whereValues);
        pageValues.add(tsquery); // last positional slot: the ORDER BY ts_rank tsquery
        Query pageQuery = em.createNativeQuery(pageSql)
                .setFirstResult(criteria.page() * criteria.pageSize())
                .setMaxResults(criteria.pageSize());
        bindPositional(pageQuery, pageValues);

        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) pageQuery.getResultList();
        return new PageResult<>(fetchByIdsInOrder(ids), totalElements,
                criteria.page(), criteria.pageSize());
    }

    /**
     * One mutable counter so the WHERE and ORDER BY fragments of a native query
     * share a single positional-parameter sequence. Positional (not named)
     * placeholders are used because EclipseLink (the WAR runtime provider) does
     * not reliably bind named parameters inside native SQL, unlike Hibernate
     * (the Testcontainers IT provider); {@code ?N} binds identically on both.
     */
    private static final class FtsBindSlots {
        private int next = 1;

        int place() {
            return next++;
        }
    }

    /** Parameter values for the WHERE fragment, in the order the slots are placed. */
    private List<Object> ftsParamValues(ProductSearchCriteria criteria, String tsquery, String like) {
        List<Object> values = new ArrayList<>();
        values.add(tsquery);
        values.add(like);
        values.add(like);
        if (criteria.categoryId() != null) {
            values.add(criteria.categoryId());
        }
        if (criteria.minPrice() != null) {
            values.add(criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            values.add(criteria.maxPrice());
        }
        if (criteria.status() != null) {
            values.add(criteria.status().name());
        }
        return values;
    }

    private void bindPositional(Query query, List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            query.setParameter(i + 1, values.get(i));
        }
    }

    /**
     * Turn a free-form term into a prefix AND tsquery, e.g. "smart phon" →
     * {@code smart:* & phon:*}. Strips punctuation, drops stopwords and
     * all-digit tokens, lowercases. Returns an empty string when nothing usable
     * remains — callers then fall back to the LIKE-only path.
     */
    static String buildPrefixTsQuery(String term) {
        String normalized = term.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ");
        return Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .filter(token -> !ENGLISH_STOPWORDS.contains(token))
                .filter(token -> !token.chars().allMatch(Character::isDigit))
                .map(token -> token + ":*")
                .collect(Collectors.joining(" & "));
    }

    private String ftsWhere(ProductSearchCriteria criteria, FtsBindSlots slots) {
        StringBuilder sql = new StringBuilder("WHERE (")
                .append(FTS_EXPRESSION)
                .append(" @@ to_tsquery('english', ?").append(slots.place()).append(')')
                .append(" OR p.name ILIKE ?").append(slots.place())
                .append(" OR p.sku ILIKE ?").append(slots.place()).append(')');
        if (criteria.categoryId() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM tb_product_category c")
                    .append(" WHERE c.product_id = p.id AND c.category_id = ?").append(slots.place()).append(')');
        }
        if (criteria.minPrice() != null) {
            sql.append(" AND p.price >= ?").append(slots.place());
        }
        if (criteria.maxPrice() != null) {
            sql.append(" AND p.price <= ?").append(slots.place());
        }
        if (criteria.status() != null) {
            sql.append(" AND p.status = ?").append(slots.place());
        } else if (!criteria.includeArchived()) {
            sql.append(" AND p.status <> 'ARCHIVED'");
        }
        return sql.toString();
    }

    private String ftsOrderBy(ProductSearchCriteria criteria, FtsBindSlots slots) {
        String attribute = switch (criteria.sortField()) {
            case PRICE -> "price";
            case CREATED_AT -> "created_at";
            default -> "name"; // RELEVANCE and NAME
        };
        String direction = criteria.sortDirection() == SortDirection.DESC ? "DESC" : "ASC";
        return "ORDER BY ts_rank(" + FTS_EXPRESSION
                + ", to_tsquery('english', ?" + slots.place() + ")) DESC, "
                + "p." + attribute + " " + direction + ", p.id ASC";
    }

    /**
     * Re-fetch entities by id so the eager-load graph and the domain mapper
     * apply, preserving the relevance order returned by the native query.
     */
    private List<Product> fetchByIdsInOrder(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ProductJpaEntity> entities = em.createQuery(
                        "SELECT p FROM ProductJpaEntity p WHERE p.id IN :ids",
                        ProductJpaEntity.class)
                .setParameter("ids", ids)
                .setHint("jakarta.persistence.loadgraph", detailsGraph())
                .getResultList();
        Map<String, ProductJpaEntity> byId = new LinkedHashMap<>();
        entities.forEach(entity -> byId.put(entity.getId(), entity));
        List<Product> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            ProductJpaEntity entity = byId.get(id);
            if (entity != null) {
                ordered.add(ProductJpaMapper.toDomain(entity));
            }
        }
        return ordered;
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
            if (!criteria.includeArchived()) {
                predicates.add(cb.notEqual(root.get("status"), ProductStatus.ARCHIVED));
            }
            // when includeArchived==true and status==null, do not filter by status at all
        }
        return predicates;
    }

    private List<Order> sortOrders(CriteriaBuilder cb, Root<ProductJpaEntity> root, ProductSearchCriteria criteria) {
        String attribute = switch (criteria.sortField()) {
            case PRICE -> "price";
            case CREATED_AT -> "createdAt";
            default -> "name"; // RELEVANCE (no text term → alphabetical) and NAME
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
