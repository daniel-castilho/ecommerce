package com.loja.productreviews.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.loja.productreviews.application.dto.ReviewDTO;
import com.loja.productreviews.application.dto.ReviewListPage;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.productreviews.domain.port.in.ListPendingReviewsUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewModerationBeanTest {

    private ListPendingReviewsUseCase listPendingReviews;
    private ReviewModerationBean bean;

    @BeforeEach
    void setUp() {
        listPendingReviews = mock(ListPendingReviewsUseCase.class);
        bean = new ReviewModerationBean();
        bean.setListPendingReviews(listPendingReviews);
    }

    @Test
    void refresh_loadsFirstPageOfPendingReviews() {
        ReviewListPage expected = new ReviewListPage(
                List.of(reviewDTO("r-1"), reviewDTO("r-2")), 25L, 0, 20);
        when(listPendingReviews.list(0, 20)).thenReturn(expected);

        bean.refresh();

        assertThat(bean.getReviews()).hasSize(2);
        assertThat(bean.getTotalPages()).isEqualTo(2);
        verify(listPendingReviews).list(0, 20);
    }

    @Test
    void nextPage_advancesWhenMorePagesExist() {
        when(listPendingReviews.list(0, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-1")), 30L, 0, 20));
        when(listPendingReviews.list(1, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-2")), 30L, 1, 20));
        bean.refresh();

        bean.nextPage();

        assertThat(bean.getPageIndex()).isEqualTo(1);
        assertThat(bean.getReviews()).extracting(ReviewDTO::id).containsExactly("r-2");
        verify(listPendingReviews).list(1, 20);
    }

    @Test
    void previousPage_goesBack() {
        when(listPendingReviews.list(0, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-1")), 30L, 0, 20));
        when(listPendingReviews.list(1, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-2")), 30L, 1, 20));
        bean.refresh();
        bean.nextPage();

        bean.previousPage();

        assertThat(bean.getPageIndex()).isZero();
    }

    @Test
    void nextPage_staysPutOnLastPage() {
        when(listPendingReviews.list(0, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-1")), 5L, 0, 20));
        bean.refresh();

        bean.nextPage();

        assertThat(bean.getPageIndex()).isZero();
        verify(listPendingReviews).list(0, 20);
    }

    private static ReviewDTO reviewDTO(String id) {
        return new ReviewDTO(id, "p-1", "u-1", 5, "Great", "Loved it", true,
                ReviewStatus.PENDING, Instant.parse("2026-08-01T10:00:00Z"), null, null);
    }
}
