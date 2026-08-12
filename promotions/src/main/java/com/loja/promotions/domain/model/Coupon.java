package com.loja.promotions.domain.model;

import com.loja.promotions.domain.exception.CouponNotApplicableException;
import com.loja.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Promotional code that reduces the merchandise subtotal at checkout. Pure
 * domain: no framework imports.
 *
 * <p>Discount scope is configurable via {@link CouponScope}: the whole order
 * ({@code ALL}), only specific products ({@code PRODUCT}), or only products in
 * specific categories ({@code CATEGORY}). Shipping is never discounted and the
 * total can never go negative ({@code FIXED} discounts are capped at the
 * eligible subtotal, {@code PERCENT} at 100%).
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
    private final CouponScope scope;
    private final Set<String> productIds;
    private final Set<Long> categoryIds;
    private final Integer maxUsesPerUser;
    private int usedCount;
    private final Instant createdAt;

    /** Convenience factory: whole-order scope with no per-user cap. */
    public static Coupon create(String code, CouponType type, BigDecimal value, boolean active,
                                Instant validFrom, Instant validTo, Integer maxTotalUses) {
        return create(code, type, value, active, validFrom, validTo, maxTotalUses,
                CouponScope.ALL, Set.of(), Set.of(), null);
    }

    public static Coupon create(String code, CouponType type, BigDecimal value, boolean active,
                                Instant validFrom, Instant validTo, Integer maxTotalUses,
                                CouponScope scope, Set<String> productIds, Set<Long> categoryIds,
                                Integer maxUsesPerUser) {
        requireValidWindow(validFrom, validTo);
        return new Coupon(UUID.randomUUID().toString(), normalizeCode(code), requireType(type),
                requireValue(type, value), active, validFrom, validTo, requireMaxUses(maxTotalUses),
                requireScope(scope, productIds, categoryIds), productIds, categoryIds,
                requireMaxUsesPerUser(maxUsesPerUser), 0, Instant.now());
    }

    /** Persistence round-trip factory; trusts the stored snapshot. */
    public static Coupon reconstitute(String id, String code, CouponType type, BigDecimal value,
                                      boolean active, Instant validFrom, Instant validTo,
                                      Integer maxTotalUses, CouponScope scope,
                                      Set<String> productIds, Set<Long> categoryIds,
                                      Integer maxUsesPerUser, int usedCount, Instant createdAt) {
        return new Coupon(id, code, type, value, active, validFrom, validTo, maxTotalUses,
                scope, productIds, categoryIds, maxUsesPerUser, usedCount, createdAt);
    }

    private Coupon(String id, String code, CouponType type, BigDecimal value, boolean active,
                   Instant validFrom, Instant validTo, Integer maxTotalUses, CouponScope scope,
                   Set<String> productIds, Set<Long> categoryIds, Integer maxUsesPerUser,
                   int usedCount, Instant createdAt) {
        this.id = id;
        this.code = code;
        this.type = type;
        this.value = value;
        this.active = active;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.maxTotalUses = maxTotalUses;
        this.scope = scope == null ? CouponScope.ALL : scope;
        this.productIds = productIds == null ? Set.of() : Set.copyOf(productIds);
        this.categoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
        this.maxUsesPerUser = maxUsesPerUser;
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

    /** Whether the coupon is under the per-user redemption cap for this user. */
    public boolean canBeUsedByUser(int usedByUser) {
        return maxUsesPerUser == null || usedByUser < maxUsesPerUser;
    }

    /**
     * Whether a cart line with the given product/categories is eligible for the
     * discount. {@code ALL} accepts every line; {@code PRODUCT} only lines whose
     * product id is in scope; {@code CATEGORY} only lines that share at least one
     * category with the scope.
     */
    public boolean isLineEligible(String productId, Set<Long> lineCategoryIds) {
        return switch (scope) {
            case ALL -> true;
            case PRODUCT -> productIds.contains(productId);
            case CATEGORY -> intersects(categoryIds, lineCategoryIds);
        };
    }

    private static boolean intersects(Set<Long> scoped, Set<Long> line) {
        if (scoped.isEmpty() || line == null || line.isEmpty()) {
            return false;
        }
        Set<Long> smaller = scoped.size() <= line.size() ? scoped : line;
        Set<Long> larger = smaller == scoped ? line : scoped;
        for (Long id : smaller) {
            if (larger.contains(id)) {
                return true;
            }
        }
        return false;
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
    public CouponScope getScope() { return scope; }
    public Set<String> getProductIds() { return Collections.unmodifiableSet(productIds); }
    public Set<Long> getCategoryIds() { return Collections.unmodifiableSet(categoryIds); }
    public Integer getMaxUsesPerUser() { return maxUsesPerUser; }
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

    private static void requireValidWindow(Instant validFrom, Instant validTo) {
        if (validFrom != null && validTo != null && validFrom.isAfter(validTo)) {
            throw new IllegalArgumentException("validFrom must not be after validTo");
        }
    }

    private static CouponScope requireScope(CouponScope scope, Set<String> productIds,
                                            Set<Long> categoryIds) {
        CouponScope effective = scope == null ? CouponScope.ALL : scope;
        return switch (effective) {
            case ALL -> {
                if (nonEmpty(productIds) || nonEmpty(categoryIds)) {
                    throw new IllegalArgumentException(
                            "ALL scope coupons must not carry product or category targets");
                }
                yield CouponScope.ALL;
            }
            case PRODUCT -> {
                if (nonEmpty(categoryIds)) {
                    throw new IllegalArgumentException(
                            "PRODUCT scope coupons must not carry category targets");
                }
                if (!nonEmpty(productIds)) {
                    throw new IllegalArgumentException(
                            "PRODUCT scope coupons require at least one product id");
                }
                yield CouponScope.PRODUCT;
            }
            case CATEGORY -> {
                if (nonEmpty(productIds)) {
                    throw new IllegalArgumentException(
                            "CATEGORY scope coupons must not carry product targets");
                }
                if (!nonEmpty(categoryIds)) {
                    throw new IllegalArgumentException(
                            "CATEGORY scope coupons require at least one category id");
                }
                yield CouponScope.CATEGORY;
            }
        };
    }

    private static Integer requireMaxUsesPerUser(Integer maxUsesPerUser) {
        if (maxUsesPerUser != null && maxUsesPerUser < 1) {
            throw new IllegalArgumentException("Maximum uses per user must be at least 1");
        }
        return maxUsesPerUser;
    }

    private static boolean nonEmpty(Set<?> targets) {
        return targets != null && !targets.isEmpty();
    }
}
