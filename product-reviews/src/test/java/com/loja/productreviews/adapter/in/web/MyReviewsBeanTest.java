package com.loja.productreviews.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.productreviews.application.dto.ReviewDTO;
import com.loja.productreviews.application.dto.ReviewListPage;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.productreviews.domain.port.in.HideOwnReviewUseCase;
import com.loja.productreviews.domain.port.in.ListMyReviewsUseCase;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;

import jakarta.faces.context.FacesContext;

class MyReviewsBeanTest {

    private ListMyReviewsUseCase listMyReviews;
    private HideOwnReviewUseCase hideOwnReview;
    private SessionPort session;
    private ProductRepositoryPort productRepository;
    private MyReviewsBean bean;

    @BeforeEach
    void setUp() {
        listMyReviews = mock(ListMyReviewsUseCase.class);
        hideOwnReview = mock(HideOwnReviewUseCase.class);
        session = mock(SessionPort.class);
        productRepository = mock(ProductRepositoryPort.class);
        bean = new MyReviewsBean();
        bean.setListMyReviews(listMyReviews);
        bean.setHideOwnReview(hideOwnReview);
        bean.setSession(session);
        bean.setProductRepository(productRepository);
    }

    private User user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn("u-1");
        return user;
    }

    private static ReviewDTO reviewDTO(String id, ReviewStatus status) {
        return new ReviewDTO(id, "p-1", "u-1", 5, "Great", "Loved it", true,
                status, Instant.parse("2026-08-01T10:00:00Z"), null, null);
    }

    @Test
    void refresh_loggedIn_loadsReviewsWithProductInfo() {
        User currentUser = user();
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyReviews.listMine("u-1", 0, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-1", ReviewStatus.PENDING)), 1L, 0, 20));
        Product product = mock(Product.class);
        when(product.getName()).thenReturn("Amazing Widget");
        when(product.getSlugValue()).thenReturn("amazing-widget");
        when(productRepository.findById("p-1")).thenReturn(Optional.of(product));

        bean.refresh();

        assertThat(bean.isLoggedIn()).isTrue();
        assertThat(bean.getReviews()).hasSize(1);
        assertThat(bean.productName("p-1")).isEqualTo("Amazing Widget");
        assertThat(bean.productSlug("p-1")).isEqualTo("amazing-widget");
        assertThat(bean.getTotalPages()).isEqualTo(1);
    }

    @Test
    void refresh_notLoggedIn_returnsEmpty() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());

        bean.refresh();

        assertThat(bean.getReviews()).isEmpty();
        assertThat(bean.getTotalElements()).isZero();
        verify(listMyReviews, never()).listMine("u-1", 0, 20);
    }

    @Test
    void hide_approvedReview_delegatesAndReloads() {
        User currentUser = user();
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyReviews.listMine("u-1", 0, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-1", ReviewStatus.APPROVED)), 1L, 0, 20));

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));
            bean.refresh();
            bean.hide(bean.getReviews().get(0));
        }

        verify(hideOwnReview).hide("r-1", "u-1");
        verify(listMyReviews, org.mockito.Mockito.times(2)).listMine("u-1", 0, 20);
    }

    @Test
    void hide_otherUsersReviewNotMine_swallowsAndShowsError() {
        User currentUser = user();
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyReviews.listMine("u-1", 0, 20)).thenReturn(new ReviewListPage(List.of(), 0L, 0, 20));
        org.mockito.Mockito.doThrow(new IllegalStateException("already hidden"))
                .when(hideOwnReview).hide("r-1", "u-1");

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            FacesContext context = mock(FacesContext.class);
            faces.when(FacesContext::getCurrentInstance).thenReturn(context);
            bean.refresh();
            bean.hide(reviewDTO("r-1", ReviewStatus.APPROVED));

            verify(context).addMessage(org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.any());
        }

        verify(hideOwnReview).hide("r-1", "u-1");
    }

    @Test
    void canHide_trueOnlyForApprovedReviews() {
        assertThat(bean.canHide(reviewDTO("r-1", ReviewStatus.APPROVED))).isTrue();
        assertThat(bean.canHide(reviewDTO("r-2", ReviewStatus.PENDING))).isFalse();
        assertThat(bean.canHide(reviewDTO("r-3", ReviewStatus.REJECTED))).isFalse();
        assertThat(bean.canHide(reviewDTO("r-4", ReviewStatus.HIDDEN))).isFalse();
    }

    @Test
    void nextPage_advancesAndReloads() {
        User currentUser = user();
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyReviews.listMine("u-1", 0, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-1", ReviewStatus.PENDING)), 25L, 0, 20));
        when(listMyReviews.listMine("u-1", 1, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-2", ReviewStatus.APPROVED)), 25L, 1, 20));
        bean.refresh();

        assertThat(bean.hasNextPage()).isTrue();
        bean.nextPage();

        assertThat(bean.getPage()).isEqualTo(1);
        assertThat(bean.getReviews()).extracting(ReviewDTO::id).containsExactly("r-2");
        verify(listMyReviews).listMine("u-1", 1, 20);
    }

    @Test
    void previousPage_goesBack() {
        User currentUser = user();
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyReviews.listMine("u-1", 0, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-1", ReviewStatus.PENDING)), 25L, 0, 20));
        when(listMyReviews.listMine("u-1", 1, 20))
                .thenReturn(new ReviewListPage(List.of(reviewDTO("r-2", ReviewStatus.PENDING)), 25L, 1, 20));
        bean.refresh();
        bean.nextPage();

        assertThat(bean.hasPreviousPage()).isTrue();
        bean.previousPage();

        assertThat(bean.getPage()).isZero();
        verify(listMyReviews, org.mockito.Mockito.times(2)).listMine("u-1", 0, 20);
    }
}