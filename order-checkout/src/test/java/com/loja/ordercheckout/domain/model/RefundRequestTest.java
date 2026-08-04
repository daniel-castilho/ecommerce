package com.loja.ordercheckout.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loja.shared.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RefundRequestTest {

    @Test
    void request_createsPendingRefundRequest() {
        RefundRequest request = RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item");

        assertThat(request.getId()).isNotBlank();
        assertThat(request.getOrderId()).isEqualTo("o-1");
        assertThat(request.getAmount()).isEqualTo(new Money(new BigDecimal("50.00")));
        assertThat(request.getReason()).isEqualTo("Damaged item");
        assertThat(request.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(request.getRejectionReason()).isNull();
        assertThat(request.getProcessedAt()).isNull();
    }

    @Test
    void request_withNonPositiveAmount_throws() {
        assertThatThrownBy(() -> RefundRequest.request("o-1", new Money(BigDecimal.ZERO), "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void request_withBlankReason_throws() {
        assertThatThrownBy(() -> RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approve_thenMarkAsProcessed_movesThroughWorkflow() {
        RefundRequest request = RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item");

        request.approve();
        assertThat(request.getStatus()).isEqualTo(RefundStatus.APPROVED);

        request.markAsProcessed();
        assertThat(request.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(request.getProcessedAt()).isNotNull();
    }

    @Test
    void approve_whenNotPending_throws() {
        RefundRequest request = RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item");
        request.reject("Policy violation");

        assertThatThrownBy(request::approve).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markAsProcessed_whenNotApproved_throws() {
        RefundRequest request = RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item");

        assertThatThrownBy(request::markAsProcessed).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reject_setsRejectedWithReason() {
        RefundRequest request = RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item");

        request.reject("Policy violation");

        assertThat(request.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("Policy violation");
        assertThat(request.getProcessedAt()).isNotNull();
    }

    @Test
    void reject_withBlankReason_throws() {
        RefundRequest request = RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item");

        assertThatThrownBy(() -> request.reject(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
