package com.loja.productreviews.adapter.out.persistence;

import com.loja.productreviews.domain.model.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * JPA mapping for {@link com.loja.productreviews.domain.model.Review}.
 *
 * <p>Does not extend {@code product-catalog}'s {@code AuditableJpaEntity}:
 * the shared-kernel rule says a module must never reach into another
 * module's adapter package (AGENTS.md rule #2). The {@code createdAt}
 * field is owned by the domain (captured in {@code Review.submit}) so the
 * JPA entity mirrors it directly with no separate JPA-managed timestamp —
 * single source of truth.
 *
 * <p>{@code @Version} round-trips through {@link ReviewJpaMapper} following
 * {@code docs/lessons.md} #1.
 */
@Entity
@Table(name = "tb_product_review", uniqueConstraints = @UniqueConstraint(
        name = "uk_product_review_author_product",
        columnNames = {"author_id", "product_id"}))
public class ReviewJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Version
    private Long version;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "author_id", nullable = false, length = 36)
    private String authorId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "body", length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewStatus status;

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    protected ReviewJpaEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }

    public boolean isVerifiedPurchase() { return verifiedPurchase; }
    public void setVerifiedPurchase(boolean verifiedPurchase) { this.verifiedPurchase = verifiedPurchase; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getModeratedAt() { return moderatedAt; }
    public void setModeratedAt(Instant moderatedAt) { this.moderatedAt = moderatedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
