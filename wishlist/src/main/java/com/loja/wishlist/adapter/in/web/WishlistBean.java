package com.loja.wishlist.adapter.in.web;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.in.GetProductDetailUseCase;
import com.loja.useraccount.domain.port.out.SessionPort;
import com.loja.wishlist.application.dto.WishlistItemDTO;
import com.loja.wishlist.domain.exception.ProductNotAvailableException;
import com.loja.wishlist.domain.port.in.AddToWishlistUseCase;
import com.loja.wishlist.domain.port.in.ListMyWishlistUseCase;
import com.loja.wishlist.domain.port.in.RemoveFromWishlistUseCase;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Customer wishlist UI: product-detail toggle and the {@code wishlist.xhtml}
 * list page.
 *
 * <p>Mutations always take the user id from {@link SessionPort}, never from
 * the form. Guests see a login prompt instead of mutation controls.
 */
@Named("wishlistBean")
@ViewScoped
public class WishlistBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    @Inject
    private transient AddToWishlistUseCase addToWishlist;

    @Inject
    private transient RemoveFromWishlistUseCase removeFromWishlist;

    @Inject
    private transient ListMyWishlistUseCase listMyWishlist;

    @Inject
    private transient GetProductDetailUseCase getProductDetail;

    @Inject
    private transient SessionPort session;

    void setAddToWishlist(AddToWishlistUseCase addToWishlist) {
        this.addToWishlist = addToWishlist;
    }

    void setRemoveFromWishlist(RemoveFromWishlistUseCase removeFromWishlist) {
        this.removeFromWishlist = removeFromWishlist;
    }

    void setListMyWishlist(ListMyWishlistUseCase listMyWishlist) {
        this.listMyWishlist = listMyWishlist;
    }

    void setGetProductDetail(GetProductDetailUseCase getProductDetail) {
        this.getProductDetail = getProductDetail;
    }

    void setSession(SessionPort session) {
        this.session = session;
    }

    /** Product id resolved on the product-detail page (for the toggle). */
    private String productId;

    private List<WishlistItemDTO> items;
    private Set<String> wishlistedProductIds = Set.of();
    private boolean inWishlist;

    @PostConstruct
    void init() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (context == null) {
            return;
        }
        String slug = context.getExternalContext().getRequestParameterMap().get("slug");
        if (slug != null && !slug.isBlank()) {
            resolveProductFromSlug(slug);
        }
        reload();
    }

    /** Testable entry: resolve product from slug and refresh state. */
    void loadForProductSlug(String slug) {
        resolveProductFromSlug(slug);
        reload();
    }

    /** Testable entry: load the wishlist list page state. */
    void loadList() {
        productId = null;
        reload();
    }

    public void add() {
        if (!requireLogin("You must be logged in to add products to your wishlist.")) {
            return;
        }
        if (productId == null || productId.isBlank()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "No product selected.", "");
            return;
        }
        try {
            String userId = session.getCurrentUser().orElseThrow().getId();
            addToWishlist.add(userId, productId);
            inWishlist = true;
            addMessage(FacesMessage.SEVERITY_INFO, "Product added to your wishlist.", "");
            reloadItems();
        } catch (ProductNotAvailableException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "This product is no longer available.", "");
        }
    }

    public void remove() {
        if (!requireLogin("You must be logged in to manage your wishlist.")) {
            return;
        }
        if (productId == null || productId.isBlank()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "No product selected.", "");
            return;
        }
        String userId = session.getCurrentUser().orElseThrow().getId();
        removeFromWishlist.remove(userId, productId);
        inWishlist = false;
        addMessage(FacesMessage.SEVERITY_INFO, "Product removed from your wishlist.", "");
        reloadItems();
    }

    /**
     * Remove a specific product from the wishlist page list.
     *
     * @param targetProductId product id from the row action
     */
    public void removeProduct(String targetProductId) {
        if (!requireLogin("You must be logged in to manage your wishlist.")) {
            return;
        }
        if (targetProductId == null || targetProductId.isBlank()) {
            return;
        }
        String userId = session.getCurrentUser().orElseThrow().getId();
        removeFromWishlist.remove(userId, targetProductId);
        if (targetProductId.equals(productId)) {
            inWishlist = false;
        }
        addMessage(FacesMessage.SEVERITY_INFO, "Product removed from your wishlist.", "");
        reloadItems();
    }

    public void toggle() {
        if (inWishlist) {
            remove();
        } else {
            add();
        }
    }

    /**
     * Catalog-card heart toggle for a specific product (wishlist S10).
     *
     * @param targetProductId product id from the card action
     */
    public void toggleFor(String targetProductId) {
        if (!requireLogin("You must be logged in to add products to your wishlist.")) {
            return;
        }
        if (targetProductId == null || targetProductId.isBlank()) {
            return;
        }
        String userId = session.getCurrentUser().orElseThrow().getId();
        if (wishlistedProductIds.contains(targetProductId)) {
            removeFromWishlist.remove(userId, targetProductId);
            addMessage(FacesMessage.SEVERITY_INFO, "Product removed from your wishlist.", "");
        } else {
            try {
                addToWishlist.add(userId, targetProductId);
                addMessage(FacesMessage.SEVERITY_INFO, "Product added to your wishlist.", "");
            } catch (ProductNotAvailableException e) {
                addMessage(FacesMessage.SEVERITY_ERROR, "This product is no longer available.", "");
                return;
            }
        }
        reload();
    }

    /** True when the given product id is already on the current user's wishlist. */
    public boolean inWishlistFor(String targetProductId) {
        return wishlistedProductIds.contains(targetProductId);
    }

    public boolean isLoggedIn() {
        return session.getCurrentUser().isPresent();
    }

    public boolean isInWishlist() {
        return inWishlist;
    }

    public boolean isHasItems() {
        return items != null && !items.isEmpty();
    }

    public boolean isProductResolved() {
        return productId != null && !productId.isBlank();
    }

    public List<WishlistItemDTO> getItems() {
        return items == null ? List.of() : items;
    }

    public String getProductId() {
        return productId;
    }

    public String formatDate(Instant instant) {
        return instant == null ? "" : DATE_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }

    public String formatPrice(BigDecimal price) {
        return price == null ? "" : price.toPlainString();
    }

    private void resolveProductFromSlug(String slug) {
        try {
            productId = getProductDetail.findActiveBySlug(new Slug(slug))
                    .map(Product::getId)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            productId = null;
        }
    }

    private void reload() {
        reloadItems();
        refreshInWishlistFlag();
    }

    private void reloadItems() {
        if (session.getCurrentUser().isEmpty()) {
            items = List.of();
            wishlistedProductIds = Set.of();
            return;
        }
        List<WishlistItemDTO> loaded = listMyWishlist.list(session.getCurrentUser().get().getId());
        items = loaded;
        wishlistedProductIds = loaded.stream()
                .map(WishlistItemDTO::productId)
                .collect(Collectors.toSet());
    }

    private void refreshInWishlistFlag() {
        if (productId == null || productId.isBlank() || session.getCurrentUser().isEmpty()) {
            inWishlist = false;
            return;
        }
        inWishlist = listMyWishlist.contains(
                session.getCurrentUser().get().getId(), productId);
    }

    private boolean requireLogin(String detail) {
        if (session.getCurrentUser().isEmpty()) {
            addMessage(FacesMessage.SEVERITY_ERROR, detail, "");
            return false;
        }
        return true;
    }

    private static void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces != null) {
            faces.addMessage(null, new FacesMessage(severity, summary, detail));
        }
    }
}
