package com.loja.productreviews.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import com.loja.productreviews.domain.model.RatingAggregate;
import com.loja.productreviews.domain.model.Review;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.productreviews.domain.port.out.ReviewRepositoryPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;

/**
 * JPA-backed implementation of {@link ReviewRepositoryPort}.
 *
 * <p>The unique {@code (author_id, product_id)} constraint is enforced by the
 * domain; the database constraint is the safety net that surfaces as
 * {@link com.loja.productreviews.domain.exception.DuplicateReviewException}.
 *
 * <p>Pagination uses {@code getResultList()} (never
 * {@code getResultStream()}) — see {@code docs/lessons.md} #3.
 */
@ApplicationScoped
public class ReviewRepositoryAdapter implements ReviewRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Override
    public Review save(Review review) {
        try {
            ReviewJpaEntity merged = em.merge(ReviewJpaMapper.toJpa(review));
            em.flush();
            return ReviewJpaMapper.toDomain(merged);
        } catch (PersistenceException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new com.loja.productreviews.domain.exception.DuplicateReviewException(
                        review.getAuthorId(), review.getProductId());
            }
            throw e;
        }
    }

    @Override
    public Optional<Review> findById(String reviewId) {
        return Optional.ofNullable(em.find(ReviewJpaEntity.class, reviewId))
                .map(ReviewJpaMapper::toDomain);
    }

    @Override
    public List<Review> findApprovedByProduct(String productId, int page, int pageSize) {
        TypedQuery<ReviewJpaEntity> query = em.createQuery(
                "SELECT r FROM ReviewJpaEntity r " +
                        "WHERE r.productId = :productId AND r.status = :status " +
                        "ORDER BY r.createdAt DESC",
                ReviewJpaEntity.class);
        query.setParameter("productId", productId);
        query.setParameter("status", ReviewStatus.APPROVED);
        query.setFirstResult(page * pageSize);
        query.setMaxResults(pageSize);
        return query.getResultList().stream().map(ReviewJpaMapper::toDomain).toList();
    }

    @Override
    public long countApprovedByProduct(String productId) {
        Long count = em.createQuery(
                "SELECT COUNT(r) FROM ReviewJpaEntity r " +
                        "WHERE r.productId = :productId AND r.status = :status",
                Long.class)
                .setParameter("productId", productId)
                .setParameter("status", ReviewStatus.APPROVED)
                .getSingleResult();
        return count == null ? 0L : count;
    }

    @Override
    public List<Review> findByStatus(ReviewStatus status, int page, int pageSize) {
        TypedQuery<ReviewJpaEntity> query = em.createQuery(
                "SELECT r FROM ReviewJpaEntity r WHERE r.status = :status ORDER BY r.createdAt DESC",
                ReviewJpaEntity.class);
        query.setParameter("status", status);
        query.setFirstResult(page * pageSize);
        query.setMaxResults(pageSize);
        return query.getResultList().stream().map(ReviewJpaMapper::toDomain).toList();
    }

    @Override
    public long countByStatus(ReviewStatus status) {
        Long count = em.createQuery(
                "SELECT COUNT(r) FROM ReviewJpaEntity r WHERE r.status = :status",
                Long.class)
                .setParameter("status", status)
                .getSingleResult();
        return count == null ? 0L : count;
    }

    @Override
    public List<Review> findByAuthor(String authorId, int page, int pageSize) {
        TypedQuery<ReviewJpaEntity> query = em.createQuery(
                "SELECT r FROM ReviewJpaEntity r WHERE r.authorId = :authorId "
                        + "ORDER BY r.createdAt DESC",
                ReviewJpaEntity.class);
        query.setParameter("authorId", authorId);
        query.setFirstResult(page * pageSize);
        query.setMaxResults(pageSize);
        return query.getResultList().stream().map(ReviewJpaMapper::toDomain).toList();
    }

    @Override
    public long countByAuthor(String authorId) {
        Long count = em.createQuery(
                "SELECT COUNT(r) FROM ReviewJpaEntity r WHERE r.authorId = :authorId",
                Long.class)
                .setParameter("authorId", authorId)
                .getSingleResult();
        return count == null ? 0L : count;
    }

    @Override
    public boolean existsByUserAndProduct(String userId, String productId) {
        Long count = em.createQuery(
                "SELECT COUNT(r) FROM ReviewJpaEntity r " +
                        "WHERE r.authorId = :userId AND r.productId = :productId",
                Long.class)
                .setParameter("userId", userId)
                .setParameter("productId", productId)
                .getSingleResult();
        return count != null && count > 0;
    }

    @Override
    public RatingAggregate aggregateApprovedByProduct(String productId) {
        long[] histogram = new long[5];
        Object[] row;
        try {
            row = (Object[]) em.createQuery(
                    "SELECT COUNT(r), AVG(r.rating) FROM ReviewJpaEntity r " +
                            "WHERE r.productId = :productId AND r.status = :status")
                    .setParameter("productId", productId)
                    .setParameter("status", ReviewStatus.APPROVED)
                    .getSingleResult();
        } catch (NoResultException e) {
            return new RatingAggregate(0L, null, histogram);
        }

        long count = row[0] == null ? 0L : ((Number) row[0]).longValue();
        Double average = row[1] == null ? null : ((Number) row[1]).doubleValue();

        if (count == 0) {
            return new RatingAggregate(0L, null, histogram);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> histogramRows = em.createQuery(
                "SELECT r.rating, COUNT(r) FROM ReviewJpaEntity r " +
                        "WHERE r.productId = :productId AND r.status = :status " +
                        "GROUP BY r.rating")
                .setParameter("productId", productId)
                .setParameter("status", ReviewStatus.APPROVED)
                .getResultList();
        for (Object[] histogramRow : histogramRows) {
            int stars = ((Number) histogramRow[0]).intValue();
            long bucketCount = ((Number) histogramRow[1]).longValue();
            if (stars >= 1 && stars <= 5) {
                histogram[stars - 1] = bucketCount;
            }
        }

        return new RatingAggregate(count, average, histogram);
    }

    private static boolean isUniqueConstraintViolation(PersistenceException e) {
        Throwable cause = e;
        while (cause != null) {
            String name = cause.getClass().getSimpleName();
            if ((name.endsWith("ConstraintViolationException") || name.endsWith("IntegrityConstraintViolation"))
                    && cause.getMessage() != null) {
                String msg = cause.getMessage().toLowerCase();
                if (msg.contains("uk_product_review_author_product")
                        || msg.contains("(author_id, product_id)")
                        || msg.contains("author_id, product_id")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
