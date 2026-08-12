package com.loja.promotions.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA persistence entity for one per-user redemption. One row per
 * (coupon, user) redemption, used to enforce the per-user cap.
 */
@Entity
@Table(name = "tb_coupon_redemption")
public class CouponRedemptionJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "coupon_id", nullable = false, length = 36)
    private String couponId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    protected CouponRedemptionJpaEntity() {}

    public String getId() { return id; }
    public String getCouponId() { return couponId; }
    public String getUserId() { return userId; }
    public Instant getRedeemedAt() { return redeemedAt; }

    public void setId(String id) { this.id = id; }
    public void setCouponId(String couponId) { this.couponId = couponId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setRedeemedAt(Instant redeemedAt) { this.redeemedAt = redeemedAt; }
}
