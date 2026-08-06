package com.loja.productreviews.adapter.in.web;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.in.GetProductDetailUseCase;
import com.loja.productreviews.application.dto.RatingSummaryDTO;
import com.loja.productreviews.application.dto.ReviewDTO;
import com.loja.productreviews.application.dto.SubmitReviewCommand;
import com.loja.productreviews.domain.exception.DuplicateReviewException;
import com.loja.productreviews.domain.exception.ProductNotFoundException;
import com.loja.productreviews.domain.port.in.GetProductRatingSummaryUseCase;
import com.loja.productreviews.domain.port.in.ListApprovedReviewsByProductUseCase;
import com.loja.productreviews.domain.port.in.SubmitReviewUseCase;
import com.loja.useraccount.domain.port.out.SessionPort;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Public product review slice (spec §7): rating summary, approved review list
 * and the "write a review" form, rendered inside {@code product-detail.xhtml}.
 *
 * <p>Resolves the product through the public {@link GetProductDetailUseCase} so
 * the section only renders for visible products. The form requires a logged-in
 * user; anonymous visitors see a login prompt instead.
 */
@Named("productReviewBean")
@ViewScoped
public class ProductReviewBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    @Inject
    private transient GetProductDetailUseCase getProductDetail;

    @Inject
    private transient ListApprovedReviewsByProductUseCase listApproved;

    @Inject
    private transient GetProductRatingSummaryUseCase ratingSummaryUseCase;

    @Inject
    private transient SubmitReviewUseCase submitReview;

    @Inject
    private transient SessionPort session;

    void setGetProductDetail(GetProductDetailUseCase getProductDetail) {
        this.getProductDetail = getProductDetail;
    }

    void setListApproved(ListApprovedReviewsByProductUseCase listApproved) {
        this.listApproved = listApproved;
    }

    void setRatingSummaryUseCase(GetProductRatingSummaryUseCase ratingSummaryUseCase) {
        this.ratingSummaryUseCase = ratingSummaryUseCase;
    }

    void setSubmitReview(SubmitReviewUseCase submitReview) {
        this.submitReview = submitReview;
    }

    void setSession(SessionPort session) {
        this.session = session;
    }

    private String productId;
    private int page;
    private RatingSummaryDTO summary;
    private List<ReviewDTO> reviews;

    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private int rating;

    @Size(max = 120, message = "Title must be at most 120 characters")
    private String title;

    @Size(max = 2000, message = "Body must be at most 2000 characters")
    private String body;

    @PostConstruct
    void init() {
        FacesContext context = FacesContext.getCurrentInstance();
        String slug = context.getExternalContext().getRequestParameterMap().get("slug");
        int page = parsePage(context.getExternalContext().getRequestParameterMap().get("page"));
        load(slug, page);
    }

    /** Testable entry point: loads the review section for a product slug and page. */
    void load(String slug, int page) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        try {
            productId = getProductDetail.findActiveBySlug(new Slug(slug))
                    .map(Product::getId)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            productId = null;
        }
        if (productId == null) {
            return;
        }
        this.page = page;
        reload();
    }

    public void submit() {
        if (session.getCurrentUser().isEmpty()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Login required",
                    "You must be logged in to write a review.");
            return;
        }
        try {
            submitReview.submit(new SubmitReviewCommand(
                    productId, session.getCurrentUser().get().getId(), rating, title, body));
            addMessage(FacesMessage.SEVERITY_INFO, "Thanks for your review!",
                    "It will appear on the product page after moderation.");
            rating = 0;
            title = null;
            body = null;
        } catch (ProductNotFoundException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Review failed",
                    "The product is no longer available.");
        } catch (DuplicateReviewException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Review failed",
                    "You have already reviewed this product.");
        }
    }

    public RatingSummaryDTO getSummary() {
        return summary;
    }

    public List<ReviewDTO> getReviews() {
        return reviews == null ? List.of() : reviews;
    }

    public boolean isLoggedIn() {
        return session.getCurrentUser().isPresent();
    }

    public boolean isReviewableProduct() {
        return productId != null;
    }

    public boolean isHasReviews() {
        return summary != null && summary.count() > 0;
    }

    public String getAverageLabel() {
        if (summary == null || summary.average() == null) {
            return "No ratings yet";
        }
        return BigDecimal.valueOf(summary.average())
                .setScale(1, RoundingMode.HALF_UP)
                .toPlainString();
    }

    public String getReviewCountLabel() {
        if (summary == null) {
            return "";
        }
        long count = summary.count();
        return count + (count == 1 ? " review" : " reviews");
    }

    /** Rounded average for the summary star row; 0 when there are no reviews. */
    public int getAverageStars() {
        if (summary == null || summary.average() == null) {
            return 0;
        }
        return (int) Math.round(summary.average());
    }

    /** 1..5 values used to render a row of star glyphs (filled vs hollow). */
    public List<Integer> getStarValues() {
        return List.of(1, 2, 3, 4, 5);
    }

    public String starClass(int star, int value) {
        return value >= star ? "review-star filled" : "review-star";
    }

    /** Histogram bars for the {@code barChart} composite (label, tooltip, height). */
    public List<HistogramBar> getHistogramBars() {
        if (summary == null) {
            return List.of();
        }
        long[] histogram = summary.histogram();
        long max = 0;
        for (long value : histogram) {
            max = Math.max(max, value);
        }
        return List.of(
                bar(1, histogram, max),
                bar(2, histogram, max),
                bar(3, histogram, max),
                bar(4, histogram, max),
                bar(5, histogram, max));
    }

    public String formatDate(Instant instant) {
        return instant == null ? "" : DATE_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }

    public List<Integer> getRatingOptions() {
        return List.of(1, 2, 3, 4, 5);
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        if (summary == null) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil((double) summary.count() / PAGE_SIZE));
    }

    public boolean isHasPrevPage() {
        return page > 0;
    }

    public boolean isHasNextPage() {
        return page + 1 < getTotalPages();
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    private void reload() {
        summary = ratingSummaryUseCase.get(productId);
        reviews = listApproved.list(productId, page, PAGE_SIZE);
    }

    private static int parsePage(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static HistogramBar bar(int stars, long[] histogram, long max) {
        long count = stars >= 1 && stars <= 5 ? histogram[stars - 1] : 0;
        int height = max <= 0 ? 0 : (int) Math.round((double) count * 100 / max);
        return new HistogramBar(stars + " star", count + " review(s)", height);
    }

    private static void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(severity, summary, detail));
    }

    /** One histogram bar; property names are consumed by the {@code barChart} composite. */
    public record HistogramBar(String label, String title, int height) { }
}
