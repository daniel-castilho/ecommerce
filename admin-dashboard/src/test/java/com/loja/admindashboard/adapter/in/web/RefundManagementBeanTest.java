package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.application.dto.RefundSearchCriteria;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.in.RefundManagementUseCase;
import com.loja.shared.domain.Money;

import java.math.BigDecimal;

class RefundManagementBeanTest {

    @Test
    void filterRefunds_carriesSelectedStatusIntoCriteria() {
        RefundManagementUseCase useCase = mock(RefundManagementUseCase.class);
        RefundManagementBean bean = new RefundManagementBean();
        bean.setRefundManagementUseCase(useCase);

        PageResult<RefundRequest> expected = new PageResult<>(
                List.of(RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item")),
                1L, 0, 20);
        when(useCase.listRefundRequests(any(RefundSearchCriteria.class), eq(0), eq(20))).thenReturn(expected);

        bean.setSelectedStatus(RefundStatus.PENDING);
        bean.filterRefunds();

        ArgumentCaptor<RefundSearchCriteria> captor = ArgumentCaptor.forClass(RefundSearchCriteria.class);
        verify(useCase).listRefundRequests(captor.capture(), eq(0), eq(20));
        assertThat(captor.getValue().status()).isEqualTo(RefundStatus.PENDING);
        assertThat(bean.getRefunds()).hasSize(1);
    }

    @Test
    void reloadRefunds_buildsEmptyCriteriaByDefault() {
        RefundManagementUseCase useCase = mock(RefundManagementUseCase.class);
        RefundManagementBean bean = new RefundManagementBean();
        bean.setRefundManagementUseCase(useCase);

        PageResult<RefundRequest> expected = new PageResult<>(List.of(), 0L, 0, 20);
        when(useCase.listRefundRequests(any(RefundSearchCriteria.class), eq(0), eq(20))).thenReturn(expected);

        bean.reloadRefunds();

        ArgumentCaptor<RefundSearchCriteria> captor = ArgumentCaptor.forClass(RefundSearchCriteria.class);
        verify(useCase).listRefundRequests(captor.capture(), eq(0), eq(20));
        assertThat(captor.getValue().status()).isNull();
        assertThat(captor.getValue().customerQuery()).isNull();
        assertThat(bean.getRefunds()).isEmpty();
    }

    @Test
    void nextPage_andPreviousPage_navigateWithinBounds() {
        RefundManagementUseCase useCase = mock(RefundManagementUseCase.class);
        RefundManagementBean bean = new RefundManagementBean();
        bean.setRefundManagementUseCase(useCase);

        PageResult<RefundRequest> pageOne = new PageResult<>(List.of(), 45L, 0, 20);
        PageResult<RefundRequest> pageTwo = new PageResult<>(List.of(), 45L, 1, 20);
        when(useCase.listRefundRequests(any(RefundSearchCriteria.class), eq(0), eq(20))).thenReturn(pageOne);
        when(useCase.listRefundRequests(any(RefundSearchCriteria.class), eq(1), eq(20))).thenReturn(pageTwo);

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
