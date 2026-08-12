package com.loja.promotions.application.dto;

import com.loja.promotions.domain.model.CouponScope;
import com.loja.promotions.domain.model.CouponType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Input for creating a coupon. The code is normalized to uppercase by the
 * domain factory; validity and usage-cap fields are optional.
 *
 * @param code           unique promotional code
 * @param type           PERCENT (value 1-100) or FIXED (value &gt; 0)
 * @param value          discount percentage or fixed amount
 * @param active         whether the coupon can be used immediately
 * @param validFrom      optional window start (null = open-ended)
 * @param validTo        optional window end (null = open-ended)
 * @param maxTotalUses   optional global usage cap (null = unlimited)
 * @param scope          ALL (whole order), PRODUCT or CATEGORY
 * @param productIds     products eligible when scope = PRODUCT (empty otherwise)
 * @param categoryIds    categories eligible when scope = CATEGORY (empty otherwise)
 * @param maxUsesPerUser optional per-user redemption cap (null = unlimited)
 */
public record CouponCommand(String code, CouponType type, BigDecimal value, boolean active,
                            Instant validFrom, Instant validTo, Integer maxTotalUses,
                            CouponScope scope, Set<String> productIds, Set<Long> categoryIds,
                            Integer maxUsesPerUser) {

    /** Whole-order coupon (ALL scope, no targets, no per-user cap). */
    public CouponCommand(String code, CouponType type, BigDecimal value, boolean active,
                         Instant validFrom, Instant validTo, Integer maxTotalUses) {
        this(code, type, value, active, validFrom, validTo, maxTotalUses,
                CouponScope.ALL, Set.of(), Set.of(), null);
    }
}
