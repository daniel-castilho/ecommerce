package com.loja.productreviews.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.productreviews.domain.exception.DuplicateReviewException;
import com.loja.productreviews.domain.model.Rating;
import com.loja.productreviews.domain.model.Review;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.productreviews.domain.model.RatingAggregate;

import jakarta.persistence.EntityTransaction;

class ReviewRepositoryJpaAdapterIT extends AbstractIntegrationTest {

    private ReviewRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new ReviewRepositoryAdapter();
        adapter.em = em;
        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE tb_product_review RESTART IDENTITY CASCADE").executeUpdate();
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    /**
     * Runs an operation inside an explicit transaction, mirroring the
     * container-managed transaction the adapter sees in production
     * (see docs/lessons.md #3).
     */
    private <T> T inTx(Supplier<T> operation) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            T result = operation.get();
            tx.commit();
            return result;
        } catch (RuntimeException | Error e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.clear();
        }
    }

    // ------------------------------------------------------------------ save / find

    @Test
    void shouldSaveAndFindById() {
        Review review = Review.submit("p-1", "u-1", Rating.of(5), "Great", "Loved it", true, false);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        Review saved = adapter.save(review);
        tx.commit();
        em.clear();

        assertThat(saved.getId()).isNotBlank();

        Optional<Review> found = inTx(() -> adapter.findById(saved.getId()));
        assertThat(found).isPresent();
        Review restored = found.get();
        assertThat(restored.getProductId()).isEqualTo("p-1");
        assertThat(restored.getAuthorId()).isEqualTo("u-1");
        assertThat(restored.getRating().getValue()).isEqualTo(5);
        assertThat(restored.getTitle()).isEqualTo("Great");
        assertThat(restored.getBody()).isEqualTo("Loved it");
        assertThat(restored.isVerifiedPurchase()).isTrue();
        assertThat(restored.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(restored.getCreatedAt()).isNotNull();
        assertThat(restored.getVersion()).isNotNull();
    }

    @Test
    void shouldUpdateStatusOnResave() {
        Review review = Review.submit("p-1", "u-1", Rating.of(4), null, null, false, false);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        Review saved = adapter.save(review);
        tx.commit();
        em.clear();

        Long versionBefore = saved.getVersion();

        Review loaded = inTx(() -> adapter.findById(saved.getId()).orElseThrow());
        loaded.approve();
        EntityTransaction tx2 = em.getTransaction();
        tx2.begin();
        Review saved2 = adapter.save(loaded);
        tx2.commit();
        em.clear();

        assertThat(saved2.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(saved2.getVersion()).isGreaterThan(versionBefore);
        assertThat(saved2.getModeratedAt()).isNotNull();

        Optional<Review> found = inTx(() -> adapter.findById(saved.getId()));
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    // ------------------------------------------------------------------ uniqueness

    @Test
    void shouldRejectSecondReviewForSameAuthorAndProduct() {
        Review first = Review.submit("p-1", "u-1", Rating.of(4), null, null, false, false);
        Review duplicate = Review.submit("p-1", "u-1", Rating.of(2), null, null, false, false);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(first);
        tx.commit();
        em.clear();

        EntityTransaction tx2 = em.getTransaction();
        tx2.begin();
        try {
            assertThatThrownBy(() -> adapter.save(duplicate))
                    .isInstanceOf(DuplicateReviewException.class);
        } finally {
            tx2.rollback();
        }
    }

    @Test
    void shouldAllowSameAuthorReviewingDifferentProduct() {
        Review a = Review.submit("p-1", "u-1", Rating.of(4), null, null, false, false);
        Review b = Review.submit("p-2", "u-1", Rating.of(5), null, null, false, false);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(a);
        adapter.save(b);
        tx.commit();
        em.clear();

        assertThat(inTx(() -> adapter.existsByUserAndProduct("u-1", "p-2"))).isTrue();
        assertThat(inTx(() -> adapter.existsByUserAndProduct("u-1", "p-1"))).isTrue();
    }

    @Test
    void existsByUserAndProduct_shouldBeFalseWhenAbsent() {
        assertThat(inTx(() -> adapter.existsByUserAndProduct("u-99", "p-99"))).isFalse();
    }

    // ------------------------------------------------------------------ findApproved / count

    @Test
    void shouldReturnApprovedNewestFirstAndPaginate() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        Review pending = Review.reconstitute("r-pending", "p-1", "u-1", Rating.of(5),
                null, null, true, base.plusSeconds(10), ReviewStatus.PENDING, null, null);
        Review approvedOld = Review.reconstitute("r-old", "p-1", "u-2", Rating.of(4),
                null, null, true, base.plusSeconds(20), ReviewStatus.APPROVED, base.plusSeconds(21), null);
        Review approvedNew = Review.reconstitute("r-new", "p-1", "u-3", Rating.of(5),
                null, null, true, base.plusSeconds(30), ReviewStatus.APPROVED, base.plusSeconds(31), null);
        Review rejected = Review.reconstitute("r-rejected", "p-1", "u-4", Rating.of(1),
                null, null, false, base.plusSeconds(40), ReviewStatus.PENDING, null, null);
        Review hidden = Review.reconstitute("r-hidden", "p-1", "u-5", Rating.of(3),
                null, null, false, base.plusSeconds(50), ReviewStatus.APPROVED, base.plusSeconds(51), null);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(pending);
        adapter.save(approvedOld);
        adapter.save(approvedNew);
        adapter.save(rejected);
        adapter.save(hidden);
        tx.commit();
        em.clear();

        inTx(() -> {
            Review rejectedReview = adapter.findById("r-rejected").orElseThrow();
            rejectedReview.reject("spam");
            adapter.save(rejectedReview);

            Review hiddenReview = adapter.findById("r-hidden").orElseThrow();
            hiddenReview.hide();
            adapter.save(hiddenReview);
            return null;
        });

        // approved: r-new (30s) and r-old (20s); newest first
        List<Review> firstPage = inTx(() -> adapter.findApprovedByProduct("p-1", 0, 10));
        assertThat(firstPage).extracting(Review::getId)
                .containsExactly("r-new", "r-old");
        assertThat(firstPage).extracting(Review::getStatus)
                .containsOnly(ReviewStatus.APPROVED);

        // pagination: page 0 size 1 -> newest (r-new); page 1 -> next (r-old)
        List<Review> page0 = inTx(() -> adapter.findApprovedByProduct("p-1", 0, 1));
        assertThat(page0).extracting(Review::getId).containsExactly("r-new");
        List<Review> page1 = inTx(() -> adapter.findApprovedByProduct("p-1", 1, 1));
        assertThat(page1).extracting(Review::getId).containsExactly("r-old");
    }

    @Test
    void countApproved_shouldExcludeNonApproved() {
        Review approved = Review.submit("p-1", "u-1", Rating.of(5), null, null, true, false);
        Review pending = Review.submit("p-1", "u-2", Rating.of(4), null, null, false, false);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(approved);
        adapter.save(pending);
        tx.commit();
        em.clear();

        inTx(() -> {
            Review approvedReview = adapter.findById(approved.getId()).orElseThrow();
            approvedReview.approve();
            adapter.save(approvedReview);
            return null;
        });

        assertThat(inTx(() -> adapter.countApprovedByProduct("p-1"))).isEqualTo(1L);
    }

    // ------------------------------------------------------------------ findByStatus / count

    @Test
    void findByStatus_shouldReturnOnlyGivenStatusNewestFirst() {
        Review r1 = Review.submit("p-1", "u-1", Rating.of(5), null, null, false, false);
        Review r2 = Review.submit("p-1", "u-2", Rating.of(4), null, null, false, false);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(r1);
        adapter.save(r2);
        tx.commit();
        em.clear();

        List<Review> pending = inTx(() -> adapter.findByStatus(ReviewStatus.PENDING, 0, 10));
        assertThat(pending).extracting(Review::getId).containsExactly(r2.getId(), r1.getId());

        assertThat(inTx(() -> adapter.countByStatus(ReviewStatus.PENDING))).isEqualTo(2L);
        assertThat(inTx(() -> adapter.countByStatus(ReviewStatus.APPROVED))).isZero();
    }

    // ------------------------------------------------------------------ aggregate

    @Test
    void aggregateApproved_shouldReturnCountAverageAndHistogram() {
        Review a1 = Review.submit("p-1", "u-1", Rating.of(5), null, null, true, false);
        Review a2 = Review.submit("p-1", "u-2", Rating.of(5), null, null, true, false);
        Review a3 = Review.submit("p-1", "u-3", Rating.of(3), null, null, true, false);
        Review pending = Review.submit("p-1", "u-4", Rating.of(1), null, null, false, false);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(a1);
        adapter.save(a2);
        adapter.save(a3);
        adapter.save(pending);
        tx.commit();
        em.clear();

        inTx(() -> {
            Review a1Approved = adapter.findById(a1.getId()).orElseThrow();
            a1Approved.approve();
            adapter.save(a1Approved);
            Review a2Approved = adapter.findById(a2.getId()).orElseThrow();
            a2Approved.approve();
            adapter.save(a2Approved);
            Review a3Approved = adapter.findById(a3.getId()).orElseThrow();
            a3Approved.approve();
            adapter.save(a3Approved);
            return null;
        });

        RatingAggregate agg = inTx(() -> adapter.aggregateApprovedByProduct("p-1"));
        assertThat(agg.count()).isEqualTo(3L);
        assertThat(agg.average()).isEqualTo(13.0 / 3.0);
        assertThat(agg.histogram()).containsExactly(0L, 0L, 1L, 0L, 2L);
    }

    @Test
    void aggregateApproved_withNoReviews_returnsZeroCountAndNullAverage() {
        RatingAggregate agg = inTx(() -> adapter.aggregateApprovedByProduct("p-99"));
        assertThat(agg.count()).isZero();
        assertThat(agg.average()).isNull();
        assertThat(agg.histogram()).containsExactly(0L, 0L, 0L, 0L, 0L);
    }
}
