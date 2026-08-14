package com.loja.productreviews.adapter.in.web;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.productreviews.application.dto.ReviewDTO;
import com.loja.productreviews.application.dto.ReviewListPage;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.productreviews.domain.port.in.HideOwnReviewUseCase;
import com.loja.productreviews.domain.port.in.ListMyReviewsUseCase;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Customer "my reviews" screen (spec §5 debt): the caller's own reviews across all
 * moderation states with pagination, plus the hide-own-review action for approved
 * reviews. All business rules live in the use cases; this bean only maps view
 * events and resolves product names/slugs for display.
 */
@Named("myReviewsBean")
@ViewScoped
public class MyReviewsBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int PAGE_SIZE = ReviewListPage.DEFAULT_PAGE_SIZE;
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Inject
    private transient ListMyReviewsUseCase listMyReviews;

    @Inject
    private transient HideOwnReviewUseCase hideOwnReview;

    @Inject
    private transient SessionPort session;

    @Inject
    private transient ProductRepositoryPort productRepository;

    void setListMyReviews(ListMyReviewsUseCase listMyReviews) {
        this.listMyReviews = listMyReviews;
    }

    void setHideOwnReview(HideOwnReviewUseCase hideOwnReview) {
        this.hideOwnReview = hideOwnReview;
    }

    void setSession(SessionPort session) {
        this.session = session;
    }

    void setProductRepository(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    private int page;
    private ReviewListPage result;
    private Map<String, ProductView> products = new HashMap<>();

    @PostConstruct
    void init() {
        refresh();
    }

    /** Testable entry point: reloads the current user's reviews. */
    void refresh() {
        String userId = currentUserId();
        if (userId == null) {
            result = null;
            products = Map.of();
            return;
        }
        result = listMyReviews.listMine(userId, page, PAGE_SIZE);
        products = resolveProducts(result.items());
    }

    public void hide(ReviewDTO review) {
        String userId = currentUserId();
        if (userId == null) {
            return;
        }
        try {
            hideOwnReview.hide(review.id(), userId);
            addMessage(FacesMessage.SEVERITY_INFO, "Review hidden",
                    "Your review is no longer public on the product page.");
        } catch (IllegalStateException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Hide failed", e.getMessage());
        }
        refresh();
    }

    public boolean canHide(ReviewDTO review) {
        return review.status() == ReviewStatus.APPROVED;
    }

    public boolean isLoggedIn() {
        return session.getCurrentUser().isPresent();
    }

    public String productName(String productId) {
        ProductView view = products.get(productId);
        return view == null ? null : view.name();
    }

    public String productSlug(String productId) {
        ProductView view = products.get(productId);
        return view == null ? null : view.slug();
    }

    // ---- pagination ----

    public void nextPage() {
        if (hasNextPage()) {
            page++;
            refresh();
        }
    }

    public void previousPage() {
        if (hasPreviousPage()) {
            page--;
            refresh();
        }
    }

    public boolean hasNextPage() {
        return result != null && page + 1 < result.totalPages();
    }

    public boolean hasPreviousPage() {
        return result != null && page > 0;
    }

    public int getTotalPages() {
        return result != null ? Math.max(1, result.totalPages()) : 1;
    }

    public long getTotalElements() {
        return result != null ? result.totalElements() : 0;
    }

    // ---- accessors ----

    public List<ReviewDTO> getReviews() {
        return result != null ? result.items() : List.of();
    }

    public int getPage() {
        return page;
    }

    public String formatDate(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    private String currentUserId() {
        return currentUser().map(User::getId).orElse(null);
    }

    private Optional<User> currentUser() {
        return session.getCurrentUser();
    }

    private Map<String, ProductView> resolveProducts(List<ReviewDTO> reviews) {
        Map<String, ProductView> resolved = new HashMap<>();
        for (ReviewDTO review : reviews) {
            if (resolved.containsKey(review.productId())) {
                continue;
            }
            productRepository.findById(review.productId())
                    .map(MyReviewsBean::toView)
                    .ifPresent(view -> resolved.put(review.productId(), view));
        }
        return resolved;
    }

    private static ProductView toView(Product product) {
        return new ProductView(product.getName(), product.getSlugValue());
    }

    private static void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(severity, summary, detail));
    }

    /** Display data for the reviewed product (name + storefront slug). */
    public record ProductView(String name, String slug) { }
}