package com.loja.admindashboard.adapter.in.web;

import com.loja.promotions.application.dto.CouponCommand;
import com.loja.promotions.domain.model.Coupon;
import com.loja.promotions.domain.model.CouponScope;
import com.loja.promotions.domain.model.CouponType;
import com.loja.promotions.domain.port.in.CreateCouponUseCase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Admin coupon creation form. */
@Named("couponCreateBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class CouponCreateBean implements Serializable {

    @Inject
    private CreateCouponUseCase createCoupon;

    private String code;
    private CouponType type = CouponType.PERCENT;
    private BigDecimal value;
    private boolean active = true;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Integer maxTotalUses;
    private CouponScope scope = CouponScope.ALL;
    private String productIds;
    private String categoryIds;
    private Integer maxUsesPerUser;

    void setCreateCoupon(CreateCouponUseCase createCoupon) {
        this.createCoupon = createCoupon;
    }

    public String submit() {
        try {
            Coupon coupon = createCoupon.createCoupon(new CouponCommand(
                    code, type, value, active,
                    toInstant(validFrom), toInstant(validTo), maxTotalUses,
                    scope, parseIds(productIds), parseCategoryIds(categoryIds), maxUsesPerUser));
            FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_INFO, "Coupon created",
                    "Coupon " + coupon.getCode() + " is ready to use"));
            return "/admin-dashboard/coupons/list.xhtml?faces-redirect=true";
        } catch (IllegalArgumentException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Coupon not created", e.getMessage()));
            return null;
        }
    }

    private static Set<String> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static Set<Long> parseCategoryIds(String csv) {
        Set<String> ids = parseIds(csv);
        try {
            return ids.stream().map(Long::parseLong).collect(Collectors.toSet());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Category ids must be numeric, comma separated");
        }
    }

    private static Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.toInstant(ZoneOffset.UTC);
    }

    public CouponType[] getTypes() {
        return CouponType.values();
    }

    public CouponScope[] getScopes() {
        return CouponScope.values();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public CouponType getType() { return type; }
    public void setType(CouponType type) { this.type = type; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }
    public Integer getMaxTotalUses() { return maxTotalUses; }
    public void setMaxTotalUses(Integer maxTotalUses) { this.maxTotalUses = maxTotalUses; }
    public CouponScope getScope() { return scope; }
    public void setScope(CouponScope scope) { this.scope = scope; }
    public String getProductIds() { return productIds; }
    public void setProductIds(String productIds) { this.productIds = productIds; }
    public String getCategoryIds() { return categoryIds; }
    public void setCategoryIds(String categoryIds) { this.categoryIds = categoryIds; }
    public Integer getMaxUsesPerUser() { return maxUsesPerUser; }
    public void setMaxUsesPerUser(Integer maxUsesPerUser) { this.maxUsesPerUser = maxUsesPerUser; }
}
