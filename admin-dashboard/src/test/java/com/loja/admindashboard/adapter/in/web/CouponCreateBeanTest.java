package com.loja.admindashboard.adapter.in.web;

import com.loja.promotions.application.dto.CouponCommand;
import com.loja.promotions.domain.model.Coupon;
import com.loja.promotions.domain.model.CouponScope;
import com.loja.promotions.domain.model.CouponType;
import com.loja.promotions.domain.port.in.CreateCouponUseCase;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponCreateBeanTest {

    @Test
    void submit_withCategoryScope_passesScopedCommand() {
        CreateCouponUseCase createCoupon = mock(CreateCouponUseCase.class);
        CouponCreateBean bean = new CouponCreateBean();
        bean.setCreateCoupon(createCoupon);

        bean.setCode("CAT10");
        bean.setType(CouponType.FIXED);
        bean.setValue(new BigDecimal("5"));
        bean.setCategoryIds(" 3, 7 ");
        bean.setMaxUsesPerUser(2);
        bean.setScope(CouponScope.CATEGORY);

        Coupon created = Coupon.create("CAT10", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null,
                CouponScope.CATEGORY, Set.of(), Set.of(3L, 7L), 2);
        when(createCoupon.createCoupon(any(CouponCommand.class))).thenReturn(created);

        try (MockedStatic<FacesContext> staticFaces = assertFacesContext()) {
            String outcome = bean.submit();

            assertThat(outcome).isEqualTo("/admin-dashboard/coupons/list.xhtml?faces-redirect=true");
            verify(createCoupon).createCoupon(argThat(command -> {
                assertThat(command.scope()).isEqualTo(CouponScope.CATEGORY);
                assertThat(command.categoryIds()).containsExactlyInAnyOrder(3L, 7L);
                assertThat(command.productIds()).isEmpty();
                assertThat(command.maxUsesPerUser()).isEqualTo(2);
                assertThat(command.maxTotalUses()).isNull();
                return true;
            }));
        }
    }

    @Test
    void submit_withProductScope_passesProductIds() {
        CreateCouponUseCase createCoupon = mock(CreateCouponUseCase.class);
        CouponCreateBean bean = new CouponCreateBean();
        bean.setCreateCoupon(createCoupon);

        bean.setCode("P1");
        bean.setType(CouponType.FIXED);
        bean.setValue(new BigDecimal("5"));
        bean.setProductIds("prod-123,prod-456");
        bean.setScope(CouponScope.PRODUCT);

        Coupon created = Coupon.create("P1", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null,
                CouponScope.PRODUCT, Set.of("prod-123", "prod-456"), Set.of(), null);
        when(createCoupon.createCoupon(any(CouponCommand.class))).thenReturn(created);

        try (MockedStatic<FacesContext> staticFaces = assertFacesContext()) {
            bean.submit();

            verify(createCoupon).createCoupon(argThat(command -> {
                assertThat(command.scope()).isEqualTo(CouponScope.PRODUCT);
                assertThat(command.productIds()).containsExactlyInAnyOrder("prod-123", "prod-456");
                assertThat(command.categoryIds()).isEmpty();
                return true;
            }));
        }
    }

    @Test
    void submit_withInvalidCategoryId_returnsNullAndDoesNotCreate() {
        CreateCouponUseCase createCoupon = mock(CreateCouponUseCase.class);
        CouponCreateBean bean = new CouponCreateBean();
        bean.setCreateCoupon(createCoupon);

        bean.setCode("BAD");
        bean.setType(CouponType.FIXED);
        bean.setValue(new BigDecimal("5"));
        bean.setCategoryIds("3,not-a-number");
        bean.setScope(CouponScope.CATEGORY);

        try (MockedStatic<FacesContext> staticFaces = assertFacesContext()) {
            String outcome = bean.submit();

            assertThat(outcome).isNull();
            verify(createCoupon, org.mockito.Mockito.never()).createCoupon(any());
        }
    }

    @Test
    void submit_withNoTargets_defaultsToAllScope() {
        CreateCouponUseCase createCoupon = mock(CreateCouponUseCase.class);
        CouponCreateBean bean = new CouponCreateBean();
        bean.setCreateCoupon(createCoupon);

        bean.setCode("SAVE");
        bean.setType(CouponType.FIXED);
        bean.setValue(new BigDecimal("5"));

        Coupon created = Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null);
        when(createCoupon.createCoupon(any(CouponCommand.class))).thenReturn(created);

        try (MockedStatic<FacesContext> staticFaces = assertFacesContext()) {
            bean.submit();

            verify(createCoupon).createCoupon(argThat(command -> {
                assertThat(command.scope()).isEqualTo(CouponScope.ALL);
                assertThat(command.productIds()).isEmpty();
                assertThat(command.categoryIds()).isEmpty();
                return true;
            }));
        }
    }

    private static MockedStatic<FacesContext> assertFacesContext() {
        ExternalContext external = mock(ExternalContext.class);
        when(external.getFlash()).thenReturn(mock(jakarta.faces.context.Flash.class));
        FacesContext faces = mock(FacesContext.class);
        MockedStatic<FacesContext> staticFaces = mockStatic(FacesContext.class);
        staticFaces.when(FacesContext::getCurrentInstance).thenReturn(faces);
        when(faces.getExternalContext()).thenReturn(external);
        return staticFaces;
    }
}