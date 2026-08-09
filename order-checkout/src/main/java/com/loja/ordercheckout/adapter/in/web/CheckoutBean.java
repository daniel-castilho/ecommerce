package com.loja.ordercheckout.adapter.in.web;

import com.loja.ordercheckout.application.dto.CheckoutCommand;
import com.loja.ordercheckout.application.dto.ItemCheckoutRequest;
import com.loja.ordercheckout.domain.exception.AccountSuspendedException;
import com.loja.ordercheckout.domain.exception.PaymentFailedException;
import com.loja.ordercheckout.domain.exception.ShippingException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.PaymentMethod;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.ordercheckout.domain.model.ShippingOption;
import com.loja.ordercheckout.domain.port.in.CreateOrderFromCartUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.ShippingRatePort;
import com.loja.productcatalog.domain.exception.InsufficientStockException;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.promotions.domain.exception.CouponNotApplicableException;
import com.loja.promotions.domain.exception.CouponNotFoundException;
import com.loja.promotions.domain.port.in.QuoteDiscountUseCase;
import com.loja.shared.domain.Money;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Thin JSF adapter for the multi-step checkout conversation (review → shipping →
 * payment → confirm). Holds the wizard state in the view scope; business rules
 * live in the domain and the {@link CreateOrderFromCartUseCase}. The confirmation
 * page reloads the persisted order by id (PRG), so it survives a browser refresh.
 *
 * <p>A {@code requestId} is generated once per view and reused for every
 * "Place Order" submit, making double-clicks idempotent at the service level.
 */
@Named("checkoutBean")
@ViewScoped
public class CheckoutBean implements Serializable {

    private static final int STEP_REVIEW = 1;
    private static final int STEP_SHIPPING = 2;
    private static final int STEP_PAYMENT = 3;
    private static final int STEP_CONFIRM = 4;

    @Inject
    private CreateOrderFromCartUseCase createOrder;

    @Inject
    private OrderRepositoryPort orderRepository;

    @Inject
    private ProductRepositoryPort productRepository;

    @Inject
    private ShippingRatePort shippingRate;

    @Inject
    private SessionPort session;

    @Inject
    private QuoteDiscountUseCase couponQuote;

    private int step = STEP_REVIEW;
    private final String requestId = UUID.randomUUID().toString();

    private final List<CartLine> cartLines = new ArrayList<>();
    private Order confirmedOrder;

    private String recipientName;
    private String street;
    private String number;
    private String complement;
    private String neighborhood;
    private String city;
    private String state;
    private String postalCode;
    private String phoneNumber;
    private String shippingMethod;
    private List<ShippingOption> shippingOptions = List.of();

    private String customerEmail;
    private String paymentToken = "tok_test";
    private String couponCode;

    @PostConstruct
    void init() {
        customerEmail = session.getCurrentUser()
                .map(User::getEmail)
                .map(email -> email == null ? "" : email.getValue())
                .orElse("");

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

    // ---- step navigation ----

    public String getStepLabel() {
        return switch (step) {
            case STEP_REVIEW -> "Review";
            case STEP_SHIPPING -> "Shipping";
            case STEP_PAYMENT -> "Payment";
            case STEP_CONFIRM -> "Confirm";
            default -> "";
        };
    }

    public String goToShipping() {
        if (getReviewLines().isEmpty()) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Empty cart", "Add at least one product with a quantity");
            return null;
        }
        step = STEP_SHIPPING;
        return null;
    }

    public String loadShippingOptions() {
        try {
            shippingOptions = shippingRate.getQuotes(buildAddress());
            shippingMethod = shippingOptions.isEmpty() ? null : shippingOptions.get(0).method();
        } catch (IllegalArgumentException | ShippingException e) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Invalid shipping address", e.getMessage());
            return null;
        }
        return null;
    }

    public String nextFromShipping() {
        if (shippingMethod == null || shippingMethod.isBlank()) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Shipping method required", "Select a shipping method");
            return null;
        }
        step = STEP_PAYMENT;
        return null;
    }

    public String backFromShipping() {
        step = STEP_REVIEW;
        return null;
    }

    public String nextFromPayment() {
        if (paymentToken == null || paymentToken.isBlank()) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Payment token required",
                    "Enter the tokenized card reference from your provider");
            return null;
        }
        step = STEP_CONFIRM;
        return null;
    }

    public String backFromPayment() {
        step = STEP_SHIPPING;
        return null;
    }

    public String backFromConfirm() {
        step = STEP_PAYMENT;
        return null;
    }

    // ---- order placement ----

    public String placeOrder() {
        Optional<User> user = session.getCurrentUser();
        if (user.isEmpty()) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Login required", "Log in before placing an order");
            return null;
        }
        String email = customerEmail == null || customerEmail.isBlank()
                ? (user.get().getEmail() == null ? null : user.get().getEmail().getValue())
                : customerEmail.trim();
        List<ItemCheckoutRequest> items = cartLines.stream()
                .filter(line -> line.getProductId() != null && !line.getProductId().isBlank())
                .map(line -> new ItemCheckoutRequest(line.getProductId().trim(), line.getQuantity()))
                .toList();
        try {
            confirmedOrder = createOrder.checkout(new CheckoutCommand(
                    requestId, user.get().getId(), email, items, buildAddress(),
                    shippingMethod, new PaymentMethod("card", paymentToken.trim()),
                    couponCode == null ? null : couponCode.trim()));
            FacesContext ctx = FacesContext.getCurrentInstance();
            ctx.getExternalContext().getFlash().setKeepMessages(true);
            return "/order-checkout/order-confirmed.xhtml?faces-redirect=true&orderId=" + confirmedOrder.getId();
        } catch (PaymentFailedException | ShippingException | InsufficientStockException
                 | AccountSuspendedException | CouponNotFoundException | CouponNotApplicableException
                 | IllegalArgumentException e) {
            step = STEP_PAYMENT;
            addGlobal(FacesMessage.SEVERITY_ERROR, "Checkout failed", e.getMessage());
            return null;
        }
    }

    // ---- cart helpers ----

    public String addLine() {
        cartLines.add(new CartLine());
        return null;
    }

    public String removeLine(CartLine line) {
        cartLines.remove(line);
        return null;
    }

    /** Cart lines resolved against the catalog, for review steps and totals. */
    public List<CartLineView> getReviewLines() {
        List<CartLineView> views = new ArrayList<>();
        for (CartLine line : cartLines) {
            String productId = line.getProductId();
            if (productId == null || productId.isBlank()) {
                continue;
            }
            productId = productId.trim();
            Product product = productRepository.findById(productId).orElse(null);
            String name = product == null ? "Unknown product" : product.getName();
            Money unitPrice = product == null ? Money.zero() : product.getPrice();
            views.add(new CartLineView(productId, name, line.getQuantity(),
                    unitPrice, unitPrice.multiply(line.getQuantity())));
        }
        return views;
    }

    public Money getSubtotal() {
        Money total = Money.zero();
        for (CartLineView view : getReviewLines()) {
            total = total.add(view.getLineTotal());
        }
        return total;
    }

    public Money getShippingCost() {
        if (shippingMethod == null) {
            return Money.zero();
        }
        return shippingOptions.stream()
                .filter(option -> option.method().equals(shippingMethod))
                .findFirst()
                .map(ShippingOption::cost)
                .orElse(Money.zero());
    }

    /** Live discount for the entered coupon code; zero when none or invalid. */
    public Money getDiscount() {
        if (couponCode == null || couponCode.isBlank()) {
            return Money.zero();
        }
        try {
            return couponQuote.quote(couponCode.trim(), getSubtotal()).discountAmount();
        } catch (CouponNotFoundException | CouponNotApplicableException e) {
            return Money.zero();
        }
    }

    public Money getOrderTotal() {
        return getSubtotal().subtract(getDiscount()).add(getShippingCost());
    }

    private ShippingAddress buildAddress() {
        return new ShippingAddress(recipientName, street, number, complement, neighborhood,
                city, state, postalCode, phoneNumber);
    }

    private static void addGlobal(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(severity, summary, detail));
    }

    // ---- accessors ----

    public int getStep() { return step; }
    public Order getConfirmedOrder() { return confirmedOrder; }
    public List<CartLine> getCartLines() { return cartLines; }
    public List<ShippingOption> getShippingOptions() { return shippingOptions; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getComplement() { return complement; }
    public void setComplement(String complement) { this.complement = complement; }
    public String getNeighborhood() { return neighborhood; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getShippingMethod() { return shippingMethod; }
    public void setShippingMethod(String shippingMethod) { this.shippingMethod = shippingMethod; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getPaymentToken() { return paymentToken; }
    public void setPaymentToken(String paymentToken) { this.paymentToken = paymentToken; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    /** Read-only row model for the review tables (resolved against the catalog). */
    public static class CartLineView {
        private final String productId;
        private final String name;
        private final int quantity;
        private final Money unitPrice;
        private final Money lineTotal;

        CartLineView(String productId, String name, int quantity, Money unitPrice, Money lineTotal) {
            this.productId = productId;
            this.name = name;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.lineTotal = lineTotal;
        }

        public String getProductId() { return productId; }
        public String getName() { return name; }
        public int getQuantity() { return quantity; }
        public Money getUnitPrice() { return unitPrice; }
        public Money getLineTotal() { return lineTotal; }
    }

    /** Mutable row model for the cart form (JSF needs settable properties). */
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
