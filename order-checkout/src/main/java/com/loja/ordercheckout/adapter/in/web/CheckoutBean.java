package com.loja.ordercheckout.adapter.in.web;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.port.in.CheckoutUseCase;
import com.loja.ordercheckout.domain.port.in.CheckoutUseCase.ItemCheckoutRequest;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.productcatalog.domain.exception.InsufficientStockException;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin JSF adapter for checkout: cart-line form binding + exception-to-message
 * translation only. The authenticated user comes from the session
 * ({@link SessionPort}), matching the user-account login flow. The confirmation
 * page reloads the persisted order by id (PRG), so it survives a browser refresh.
 */
@Named("checkoutBean")
@RequestScoped
public class CheckoutBean {

    @Inject
    private CheckoutUseCase checkoutUseCase;

    @Inject
    private OrderRepositoryPort orderRepository;

    @Inject
    private SessionPort session;

    private List<CartLine> cartLines = new ArrayList<>();
    private Order confirmedOrder;

    @PostConstruct
    void init() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        String orderId = ctx.getExternalContext().getRequestParameterMap().get("orderId");
        if (orderId != null && !orderId.isBlank()) {
            confirmedOrder = orderRepository.findById(orderId).orElse(null);
            return;
        }
        String productId = ctx.getExternalContext().getRequestParameterMap().get("productId");
        if (productId != null && !productId.isBlank()) {
            cartLines.add(new CartLine(productId, 1));
        } else {
            cartLines.add(new CartLine());
        }
    }

    public String checkout() {
        List<ItemCheckoutRequest> requests = cartLines.stream()
                .filter(line -> line.getProductId() != null && !line.getProductId().isBlank())
                .map(line -> new ItemCheckoutRequest(line.getProductId().trim(), line.getQuantity()))
                .toList();
        if (requests.isEmpty()) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Empty cart", "Add at least one product with a quantity");
            return null;
        }
        String userId = session.getCurrentUser().map(User::getId).orElse(null);
        if (userId == null) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Login required", "Log in before placing an order");
            return null;
        }
        try {
            confirmedOrder = checkoutUseCase.checkout(userId, requests);
            FacesContext ctx = FacesContext.getCurrentInstance();
            ctx.getExternalContext().getFlash().setKeepMessages(true);
            return "/order-checkout/order-confirmed.xhtml?faces-redirect=true&orderId=" + confirmedOrder.getId();
        } catch (IllegalArgumentException | InsufficientStockException e) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Checkout failed", e.getMessage());
            return null;
        }
    }

    public String addLine() {
        cartLines.add(new CartLine());
        return null;
    }

    public String removeLine(CartLine line) {
        cartLines.remove(line);
        return null;
    }

    private static void addGlobal(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(severity, summary, detail));
    }

    public List<CartLine> getCartLines() { return cartLines; }
    public void setCartLines(List<CartLine> cartLines) { this.cartLines = cartLines; }
    public Order getConfirmedOrder() { return confirmedOrder; }

    /** Mutable row model for the checkout form (JSF needs settable properties). */
    public static class CartLine {
        @NotBlank(message = "Product is required")
        private String productId;

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;

        public CartLine() { }

        public CartLine(String productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
