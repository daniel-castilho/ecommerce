package com.loja.admindashboard.adapter.in.web;

import com.loja.promotions.application.dto.PageResult;
import com.loja.promotions.domain.model.Coupon;
import com.loja.promotions.domain.port.in.ListCouponsUseCase;
import com.loja.promotions.domain.port.in.SetCouponActiveUseCase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Admin coupon list: search/filter, pagination, activate/deactivate. */
@Named("couponManagementBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class CouponManagementBean implements Serializable {

    private static final int PAGE_SIZE = 20;

    @Inject
    private ListCouponsUseCase listCoupons;

    @Inject
    private SetCouponActiveUseCase setCouponActive;

    private List<Coupon> coupons = List.of();
    private long totalElements;
    private int page;
    private String codeFragment;
    private Boolean active;

    void setListCoupons(ListCouponsUseCase listCoupons) {
        this.listCoupons = listCoupons;
    }

    void setSetCouponActive(SetCouponActiveUseCase setCouponActive) {
        this.setCouponActive = setCouponActive;
    }

    @PostConstruct
    void load() {
        refresh();
    }

    public void refresh() {
        PageResult<Coupon> result = listCoupons.listCoupons(codeFragment, active, page, PAGE_SIZE);
        coupons = result.items();
        totalElements = result.totalElements();
    }

    public void search() {
        page = 0;
        refresh();
    }

    public void nextPage() {
        if ((page + 1L) * PAGE_SIZE < totalElements) {
            page++;
            refresh();
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            refresh();
        }
    }

    public boolean isHasPreviousPage() {
        return page > 0;
    }

    public boolean isHasNextPage() {
        return (page + 1L) * PAGE_SIZE < totalElements;
    }

    public String toggleActive(Coupon coupon) {
        setCouponActive.setActive(coupon.getId(), !coupon.isActive());
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_INFO, "Coupon updated",
                "Coupon " + coupon.getCode() + " is now "
                        + (coupon.isActive() ? "inactive" : "active")));
        refresh();
        return null;
    }

    public String newCoupon() {
        return "/admin-dashboard/coupons/create.xhtml?faces-redirect=true";
    }

    public String formatUtc(Instant instant) {
        return instant == null ? null
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC).format(instant);
    }

    public List<Coupon> getCoupons() { return coupons; }
    public long getTotalElements() { return totalElements; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public String getCodeFragment() { return codeFragment; }
    public void setCodeFragment(String codeFragment) { this.codeFragment = codeFragment; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
