package com.loja.promotions.adapter.out.persistence;

import com.loja.promotions.domain.model.Coupon;
import com.loja.promotions.domain.model.CouponScope;
import com.loja.promotions.domain.model.CouponType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA persistence entity for Coupon. Isolated in the adapter layer; the domain
 * object never carries framework annotations (see CouponJpaMapper).
 */
@Entity
@Table(name = "tb_coupon")
public class CouponJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal value;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "max_total_uses")
    private Integer maxTotalUses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponScope scope = CouponScope.ALL;

    @Column(name = "product_ids", length = 2000)
    private String productIds;

    @Column(name = "category_ids", length = 2000)
    private String categoryIds;

    @Column(name = "max_uses_per_user")
    private Integer maxUsesPerUser;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CouponJpaEntity() {}

    public String getId() { return id; }
    public String getCode() { return code; }
    public CouponType getType() { return type; }
    public BigDecimal getValue() { return value; }
    public boolean isActive() { return active; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public Integer getMaxTotalUses() { return maxTotalUses; }
    public CouponScope getScope() { return scope; }
    public String getProductIds() { return productIds; }
    public String getCategoryIds() { return categoryIds; }
    public Integer getMaxUsesPerUser() { return maxUsesPerUser; }
    public int getUsedCount() { return usedCount; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(String id) { this.id = id; }
    public void setCode(String code) { this.code = code; }
    public void setType(CouponType type) { this.type = type; }
    public void setValue(BigDecimal value) { this.value = value; }
    public void setActive(boolean active) { this.active = active; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }
    public void setMaxTotalUses(Integer maxTotalUses) { this.maxTotalUses = maxTotalUses; }
    public void setScope(CouponScope scope) { this.scope = scope; }
    public void setProductIds(String productIds) { this.productIds = productIds; }
    public void setCategoryIds(String categoryIds) { this.categoryIds = categoryIds; }
    public void setMaxUsesPerUser(Integer maxUsesPerUser) { this.maxUsesPerUser = maxUsesPerUser; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
