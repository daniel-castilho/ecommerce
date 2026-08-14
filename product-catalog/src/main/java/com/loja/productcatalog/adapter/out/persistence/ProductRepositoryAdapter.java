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
 * <p><b>Search (FTS benchmark evolution).</b> Text searches run a native PostgreSQL
 * query over the STORED weighted tsvector column {@code search_vector} (created by
 * migration V31, backed by a GIN index; weights A = name/sku, B = short_description).
 * The primary match uses {@code websearch_to_tsquery} — safe for raw input, supports
 * {@code "quoted phrases"}, {@code OR} and {@code -} exclusions — and hits are ranked
 * with {@code ts_rank_cd(search_vector, tsquery, 32)}. When the primary pass returns
 * no hits, a fallback pass runs a {@code token:* & token:*} prefix tsquery (see
 * {@link #buildPrefixTsQuery}) ORed with {@code ILIKE} on name/sku, so prefix and
 * interior-fragment matches a {@code LIKE} search used to find are not lost. Queries
 * without a text term keep the Criteria path unchanged. Long description is NOT in the
 * vector (same LOB-type variance reason as V25 — explicit debt).
 */
@ApplicationScoped
public class ProductRepositoryAdapter implements ProductRepositoryPort {

    /**
     * Stored weighted tsvector column (migration V31), backed by a GIN index.
     * Weights are applied by the generated column itself: A = name/sku, B =
     * short_description. {@code description} stays out: it is a {@code @Lob}
     * column whose type varies between environments (same reason as V25).
     */
    private static final String SEARCH_VECTOR = "p.search_vector";

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
            return searchByText(criteria, criteria.nameOrSkuContains().trim());
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
     * FTS text-search entry point. Primary pass: websearch over the STORED vector.
     * If it returns zero hits, a fallback pass re-runs with a prefix tsquery (+
     * ILIKE on name/sku) so prefix and interior-fragment matches are not lost —
     * see {@link #prefixFallbackWhere}. Page/count share the same WHERE and bind
     * order in both passes.
     */
    private PageResult<Product> searchByText(ProductSearchCriteria criteria, String term) {
        PageResult<Product> primary = ftsSearch(criteria, term, null);
        if (primary.totalElements() > 0) {
            return primary;
        }
        return ftsSearch(criteria, term, buildPrefixTsQuery(term));
    }

    /**
     * Runs the count + page queries for one FTS strategy. {@code fallbackPrefix} is
     * {@code null} for the primary websearch pass (single {@code @@ websearch_to_tsquery}
     * predicate); otherwise it is a prefix AND tsquery ({@code token:* & token:*}, may be
     * blank) ORed with ILIKE name/sku — the blank case degenerates to the LIKE fragment
     * path for terms with no usable lexemes (punctuation/stopword-only).
     */
    private PageResult<Product> ftsSearch(ProductSearchCriteria criteria, String term, String fallbackPrefix) {
        String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
        List<Object> whereValues = ftsWhereValues(criteria, term, fallbackPrefix, like);

        FtsBindSlots countSlots = new FtsBindSlots();
        String countSql = "SELECT COUNT(*) FROM tb_product p " + ftsWhere(criteria, countSlots, fallbackPrefix);
        Query countQuery = em.createNativeQuery(countSql);
        bindPositional(countQuery, whereValues);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        FtsBindSlots pageSlots = new FtsBindSlots();
        String pageSql = "SELECT p.id FROM tb_product p " + ftsWhere(criteria, pageSlots, fallbackPrefix)
                + " " + ftsOrderBy(criteria, pageSlots, fallbackPrefix);
        List<Object> pageValues = new ArrayList<>(whereValues);
        pageValues.add(rankBindValue(term, fallbackPrefix)); // ORDER BY rank slot
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

    /**
     * Rank normalization for {@code ts_rank_cd} (32 = rank/(rank+1); see PostgreSQL docs
     * for the normalization bitmask). Keeps the raw cover-density rank in a readable range.
     */
    private static final String RANK_NORMALIZATION = "32";

    /**
     * Parameter values for the WHERE fragment of one FTS pass, in the same order the
     * slots are placed by {@link #ftsWhere}. Primary pass ({@code fallbackPrefix == null})
     * binds the raw term once for {@code websearch_to_tsquery}; the fallback pass binds
     * the prefix tsquery + the two ILIKE patterns. Filter values then match
     * {@link #ftsFilterPredicates}.
     */
    private List<Object> ftsWhereValues(ProductSearchCriteria criteria, String term,
                                        String fallbackPrefix, String like) {
        List<Object> values = new ArrayList<>();
        if (fallbackPrefix == null) {
            values.add(term);
        } else {
            values.add(fallbackPrefix);
            values.add(like);
            values.add(like);
        }
        ftsFilterValues(criteria, values);
        return values;
    }

    private void ftsFilterValues(ProductSearchCriteria criteria, List<Object> values) {
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
    }

    private String rankBindValue(String term, String fallbackPrefix) {
        return fallbackPrefix == null ? term : fallbackPrefix;
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
     * remains — the fallback pass then degenerates to the ILIKE fragment path.
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

    /**
     * WHERE fragment of one FTS pass. Primary: single {@code search_vector @@
     * websearch_to_tsquery} predicate (safe for any raw input). Fallback
     * ({@code fallbackPrefix != null}): prefix tsquery ({@code token:* & token:*},
     * possibly blank → empty tsquery matches nothing) ORed with ILIKE name/sku so
     * interior fragments survive. Category/price/status predicates are appended in
     * the same order as their values in {@link #ftsWhereValues}.
     */
    private String ftsWhere(ProductSearchCriteria criteria, FtsBindSlots slots, String fallbackPrefix) {
        StringBuilder sql = new StringBuilder("WHERE ");
        if (fallbackPrefix == null) {
            sql.append(SEARCH_VECTOR)
                    .append(" @@ websearch_to_tsquery('english', ?").append(slots.place()).append(')');
        } else {
            sql.append('(').append(SEARCH_VECTOR)
                    .append(" @@ to_tsquery('english', ?").append(slots.place()).append(')')
                    .append(" OR p.name ILIKE ?").append(slots.place())
                    .append(" OR p.sku ILIKE ?").append(slots.place()).append(')');
        }
        ftsFilterPredicates(criteria, sql, slots);
        return sql.toString();
    }

    private void ftsFilterPredicates(ProductSearchCriteria criteria, StringBuilder sql, FtsBindSlots slots) {
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
    }

    /**
     * ORDER BY for one FTS pass: cover-density rank first (descending), then the
     * requested attribute as tie-breaker, then id for full stability. The rank
     * tsquery is a supplementary positional slot bound after the WHERE values.
     */
    private String ftsOrderBy(ProductSearchCriteria criteria, FtsBindSlots slots, String fallbackPrefix) {
        String attribute = switch (criteria.sortField()) {
            case PRICE -> "price";
            case CREATED_AT -> "created_at";
            default -> "name"; // RELEVANCE and NAME
        };
        String direction = criteria.sortDirection() == SortDirection.DESC ? "DESC" : "ASC";
        String tsquery = fallbackPrefix == null
                ? "websearch_to_tsquery('english', ?" + slots.place() + ')'
                : "to_tsquery('english', ?" + slots.place() + ')';
        return "ORDER BY ts_rank_cd(" + SEARCH_VECTOR + ", " + tsquery + ", " + RANK_NORMALIZATION + ") DESC, "
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
