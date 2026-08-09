package com.loja.promotions.domain.model;

import com.loja.promotions.domain.exception.CouponNotApplicableException;
import com.loja.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Promotional code that reduces the merchandise subtotal at checkout. Pure
 * domain: no framework imports.
 *
 * <p>Discount scope is the whole order (all line totals). Shipping is never
 * discounted in the MVP and the total can never go negative ({@code FIXED}
 * discounts are capped at the subtotal, {@code PERCENT} at 100%).
 */
public final class Coupon {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final String id;
    private final String code;
    private final CouponType type;
    private final BigDecimal value;
    private boolean active;
    private final Instant validFrom;
    private final Instant validTo;
    private final Integer maxTotalUses;
    private int usedCount;
    private final Instant createdAt;

    public static Coupon create(String code, CouponType type, BigDecimal value, boolean active,
                                Instant validFrom, Instant validTo, Integer maxTotalUses) {
        return new Coupon(UUID.randomUUID().toString(), normalizeCode(code), requireType(type),
                requireValue(type, value), active, validFrom, validTo, requireMaxUses(maxTotalUses),
                0, Instant.now());
    }

    /** Persistence round-trip factory; trusts the stored snapshot. */
    public static Coupon reconstitute(String id, String code, CouponType type, BigDecimal value,
                                      boolean active, Instant validFrom, Instant validTo,
                                      Integer maxTotalUses, int usedCount, Instant createdAt) {
        return new Coupon(id, code, type, value, active, validFrom, validTo, maxTotalUses,
                usedCount, createdAt);
    }

    private Coupon(String id, String code, CouponType type, BigDecimal value, boolean active,
                   Instant validFrom, Instant validTo, Integer maxTotalUses, int usedCount,
                   Instant createdAt) {
        this.id = id;
        this.code = code;
        this.type = type;
        this.value = value;
        this.active = active;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.maxTotalUses = maxTotalUses;
        this.usedCount = usedCount;
        this.createdAt = createdAt;
    }

    /** Whether the coupon is active and inside its validity window at {@code when}. */
    public boolean isApplicableAt(Instant when) {
        if (!active) {
            return false;
        }
        if (validFrom != null && when.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !when.isAfter(validTo);
    }

    /** Whether the coupon can be used now: applicable and under the usage cap. */
    public boolean canBeUsed(Instant when) {
        return isApplicableAt(when) && (maxTotalUses == null || usedCount < maxTotalUses);
    }

    /**
     * Pure discount calculation against the merchandise subtotal. {@code FIXED}
     * is capped at the subtotal; {@code PERCENT} is proportional up to 100%.
     * Does not check applicability.
     */
    public Money discountFor(Money merchandiseSubtotal) {
        BigDecimal discount;
        if (type == CouponType.PERCENT) {
            BigDecimal factor = value.divide(HUNDRED, 4, RoundingMode.HALF_UP);
            discount = merchandiseSubtotal.getAmount().multiply(factor);
        } else {
            discount = value.min(merchandiseSubtotal.getAmount());
        }
        return new Money(discount);
    }

    /** Records one redemption; rejects usage beyond the configured cap. */
    public void recordUsage() {
        if (maxTotalUses != null && usedCount >= maxTotalUses) {
            throw new CouponNotApplicableException(
                    "Coupon " + code + " has reached its maximum number of uses");
        }
        usedCount++;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public CouponType getType() { return type; }
    public BigDecimal getValue() { return value; }
    public boolean isActive() { return active; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public Integer getMaxTotalUses() { return maxTotalUses; }
    public int getUsedCount() { return usedCount; }
    public Instant getCreatedAt() { return createdAt; }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Coupon code is required");
        }
        return code.trim().toUpperCase();
    }

    private static CouponType requireType(CouponType type) {
        return Objects.requireNonNull(type, "Coupon type is required");
    }

    private static BigDecimal requireValue(CouponType type, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Coupon value must be greater than zero");
        }
        if (type == CouponType.PERCENT && value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Percent coupon value must not exceed 100");
        }
        return value;
    }

    private static Integer requireMaxUses(Integer maxTotalUses) {
        if (maxTotalUses != null && maxTotalUses < 1) {
            throw new IllegalArgumentException("Maximum uses must be at least 1");
        }
        return maxTotalUses;
    }
}
