package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.in.RefundManagementUseCase;
import com.loja.shared.domain.Money;

import java.math.BigDecimal;

class RefundManagementBeanTest {

    @Test
    void filterRefunds_usesSelectedStatusAndRefreshesPage() {
        RefundManagementUseCase useCase = mock(RefundManagementUseCase.class);
        RefundManagementBean bean = new RefundManagementBean();
        bean.setRefundManagementUseCase(useCase);

        PageResult<RefundRequest> expected = new PageResult<>(
                List.of(RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item")),
                1L, 0, 20);
        when(useCase.listRefundRequests(RefundStatus.PENDING, 0, 20)).thenReturn(expected);

        bean.setSelectedStatus(RefundStatus.PENDING);
        bean.filterRefunds();

        assertThat(bean.getRefunds()).hasSize(1);
        verify(useCase).listRefundRequests(RefundStatus.PENDING, 0, 20);
    }

    @Test
    void listRefunds_withoutStatus_delegatesToFindAll() {
        RefundManagementUseCase useCase = mock(RefundManagementUseCase.class);
        RefundManagementBean bean = new RefundManagementBean();
        bean.setRefundManagementUseCase(useCase);

        PageResult<RefundRequest> expected = new PageResult<>(List.of(), 0L, 0, 20);
        when(useCase.listRefundRequests(null, 0, 20)).thenReturn(expected);

        bean.reloadRefunds();

        assertThat(bean.getRefunds()).isEmpty();
        verify(useCase).listRefundRequests(null, 0, 20);
    }

    @Test
    void nextPage_andPreviousPage_navigateWithinBounds() {
        RefundManagementUseCase useCase = mock(RefundManagementUseCase.class);
        RefundManagementBean bean = new RefundManagementBean();
        bean.setRefundManagementUseCase(useCase);

        PageResult<RefundRequest> pageOne = new PageResult<>(List.of(), 45L, 0, 20);
        PageResult<RefundRequest> pageTwo = new PageResult<>(List.of(), 45L, 1, 20);
        when(useCase.listRefundRequests(null, 0, 20)).thenReturn(pageOne);
        when(useCase.listRefundRequests(null, 1, 20)).thenReturn(pageTwo);

        bean.reloadRefunds();
        assertThat(bean.isPreviousPageEnabled()).isFalse();
        assertThat(bean.isNextPageEnabled()).isTrue();

        bean.nextPage();
        assertThat(bean.getPage()).isEqualTo(1);
        assertThat(bean.isPreviousPageEnabled()).isTrue();

        bean.previousPage();
        assertThat(bean.getPage()).isEqualTo(0);
    }
}
