package com.loja.admindashboard.adapter.in.web;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponManagementBeanTest {

    @Test
    void formatUtc_withInstant_formatsUtc() {
        CouponManagementBean bean = new CouponManagementBean();

        assertThat(bean.formatUtc(Instant.parse("2026-08-10T00:09:19Z")))
                .isEqualTo("2026-08-10 00:09");
    }

    @Test
    void formatUtc_withNull_returnsNull() {
        CouponManagementBean bean = new CouponManagementBean();

        assertThat(bean.formatUtc(null)).isNull();
    }
}