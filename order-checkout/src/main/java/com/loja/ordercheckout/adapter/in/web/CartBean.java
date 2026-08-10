package com.loja.ordercheckout.adapter.in.web;

import com.loja.ordercheckout.application.dto.CartLineView;
import com.loja.ordercheckout.application.dto.CartView;
import com.loja.ordercheckout.domain.exception.CartConcurrentModificationException;
import com.loja.ordercheckout.domain.exception.CartProductNotAvailableException;
import com.loja.ordercheckout.domain.port.in.AddToCartUseCase;
import com.loja.ordercheckout.domain.port.in.ClearCartUseCase;
import com.loja.ordercheckout.domain.port.in.GetCartUseCase;
import com.loja.ordercheckout.domain.port.in.RemoveFromCartUseCase;
import com.loja.ordercheckout.domain.port.in.UpdateCartLineUseCase;
import com.loja.shared.domain.Money;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer cart UI: the {@code cart.xhtml} list page plus the product-detail
 * and catalog-card "Add to cart" actions.
 *
 * <p>Mutations always take the owner id from the session, never from the form:
 * the logged-in user's id when authenticated, otherwise the {@link GuestCartSession}
 * id (S12 guest cart). A guest cart survives until login, when
 * {@code GuestCartMergeObserver} folds it into the user's cart. Checkout itself
 * still requires an account — guests are routed to the login page. Prices come
 * from the persisted cart enriched live from the catalog (no frozen unit price),
 * so a catalog price change is always reflected.
 */
@Named("cartBean")
@ViewScoped
public class CartBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private transient AddToCartUseCase addToCart;

    @Inject
    private transient UpdateCartLineUseCase updateCartLine;

    @Inject
    private transient RemoveFromCartUseCase removeFromCart;

    @Inject
    private transient GetCartUseCase getCart;

    @Inject
    private transient ClearCartUseCase clearCart;

    @Inject
    private transient SessionPort session;

    @Inject
    private transient GuestCartSession guestCartSession;

    void setAddToCart(AddToCartUseCase addToCart) {
        this.addToCart = addToCart;
    }

    void setUpdateCartLine(UpdateCartLineUseCase updateCartLine) {
        this.updateCartLine = updateCartLine;
    }

    void setRemoveFromCart(RemoveFromCartUseCase removeFromCart) {
        this.removeFromCart = removeFromCart;
    }

    void setGetCart(GetCartUseCase getCart) {
        this.getCart = getCart;
    }

    void setClearCart(ClearCartUseCase clearCart) {
        this.clearCart = clearCart;
    }

    void setSession(SessionPort session) {
        this.session = session;
    }

    void setGuestCartSession(GuestCartSession guestCartSession) {
        this.guestCartSession = guestCartSession;
    }

    /** Product id of the "Add to cart" target (resolved on product-detail). */
    private String productId;

    private int quantity = 1;

    private CartView cart;

    /** Editable quantity per line, keyed by product id, for the cart form. */
    private final Map<String, Integer> quantityByProduct = new HashMap<>();

    @PostConstruct
    void init() {
        reload();
    }

    /** Testable entry: reload the cart page state. */
    void load() {
        reload();
    }

    /**
     * Add the resolved {@link #productId} (product-detail) to the cart.
     *
     * @return JSF navigation outcome (always null; messages carry the result)
     */
    public String add() {
        if (productId == null || productId.isBlank()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "No product selected.", "");
            return null;
        }
        return addProductInternal(productId);
    }

    /**
     * Add a specific product to the cart with the current {@link #quantity}.
     * Used by the product-detail "Add to cart" button.
     *
     * @param targetProductId product id from the page
     * @return JSF navigation outcome (always null; messages carry the result)
     */
    public String addProduct(String targetProductId) {
        if (targetProductId == null || targetProductId.isBlank()) {
            return null;
        }
        return addProductInternal(targetProductId);
    }

    private String addProductInternal(String targetProductId) {
        try {
            addToCart.add(currentOwnerId(), targetProductId, quantity);
            addMessage(FacesMessage.SEVERITY_INFO, "Product added to your cart.", "");
            reload();
        } catch (CartProductNotAvailableException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "This product is no longer available.", "");
        } catch (CartConcurrentModificationException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Cart was updated, please try again.", "");
            reload();
        }
        return null;
    }

    /**
     * Apply the edited quantity for one line (from {@link #quantityByProduct}).
     * A quantity of zero removes the line.
     *
     * @param targetProductId product id from the row action
     */
    public void updateQuantity(String targetProductId) {
        if (targetProductId == null || targetProductId.isBlank()) {
            return;
        }
        int newQuantity;
        Object raw = quantityByProduct.get(targetProductId);
        if (raw instanceof Integer integer) {
            newQuantity = integer;
        } else if (raw instanceof Number number) {
            newQuantity = number.intValue();
        } else {
            try {
                newQuantity = Integer.parseInt(String.valueOf(raw));
            } catch (NumberFormatException e) {
                newQuantity = -1;
            }
        }
        if (newQuantity < 0) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Invalid quantity.",
                    "Quantity must be a number of at least 0.");
            return;
        }
        try {
            updateCartLine.updateQuantity(currentOwnerId(), targetProductId, newQuantity);
            addMessage(FacesMessage.SEVERITY_INFO, "Cart updated.", "");
            reload();
        } catch (CartConcurrentModificationException e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Cart was updated, please try again.", "");
            reload();
        }
    }

    /**
     * Remove one line from the cart.
     *
     * @param targetProductId product id from the row action
     */
    public void removeLine(String targetProductId) {
        if (targetProductId == null || targetProductId.isBlank()) {
            return;
        }
        removeFromCart.remove(currentOwnerId(), targetProductId);
        addMessage(FacesMessage.SEVERITY_INFO, "Product removed from your cart.", "");
        reload();
    }

    /** Empty the whole cart. */
    public void clearCart() {
        clearCart.clear(currentOwnerId());
        addMessage(FacesMessage.SEVERITY_INFO, "Your cart has been cleared.", "");
        reload();
    }

    /**
     * Navigate to the checkout conversation. Checkout requires an account, so
     * guests are routed to the login page first (their cart survives the login).
     */
    public String proceedToCheckout() {
        if (cart == null || cart.lines().isEmpty()) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Your cart is empty.", "Add products before checking out.");
            return null;
        }
        if (session.getCurrentUser().isEmpty()) {
            return "/user-account/login.xhtml?faces-redirect=true";
        }
        return "/order-checkout/checkout.xhtml?faces-redirect=true";
    }

    // ---- accessors ----

    public boolean isLoggedIn() {
        return session.getCurrentUser().isPresent();
    }

    public boolean isHasLines() {
        return cart != null && !cart.lines().isEmpty();
    }

    public List<CartLineView> getLines() {
        return cart == null ? List.of() : cart.lines();
    }

    public Money getSubtotal() {
        return cart == null ? Money.zero() : cart.subtotal();
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Map<String, Integer> getQuantityByProduct() {
        return quantityByProduct;
    }

    private void reload() {
        String ownerId = currentOwnerId();
        cart = getCart.getCart(ownerId);
        quantityByProduct.clear();
        for (CartLineView line : cart.lines()) {
            quantityByProduct.put(line.productId(), line.quantity());
        }
    }

    /**
     * The id that keys the active cart: the authenticated user's id when logged
     * in, otherwise the anonymous session's guest id (S12).
     */
    private String currentOwnerId() {
        return session.getCurrentUser()
                .map(User::getId)
                .orElseGet(guestCartSession::getGuestId);
    }

    private static void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces != null) {
            faces.addMessage(null, new FacesMessage(severity, summary, detail));
        }
    }
}
