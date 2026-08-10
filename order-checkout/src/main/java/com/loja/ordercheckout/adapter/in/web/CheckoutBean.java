package com.loja.ordercheckout.adapter.in.web;

import com.loja.ordercheckout.application.dto.CartLineView;
import com.loja.ordercheckout.application.dto.CartView;
import com.loja.ordercheckout.application.dto.CheckoutCommand;
import com.loja.ordercheckout.domain.exception.AccountSuspendedException;
import com.loja.ordercheckout.domain.exception.PaymentFailedException;
import com.loja.ordercheckout.domain.exception.ShippingException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.PaymentMethod;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.ordercheckout.domain.model.ShippingOption;
import com.loja.ordercheckout.domain.port.in.CreateOrderFromCartUseCase;
import com.loja.ordercheckout.domain.port.in.GetCartUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.ShippingRatePort;
import com.loja.productcatalog.domain.exception.InsufficientStockException;
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
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Thin JSF adapter for the multi-step checkout conversation (review → shipping →
 * payment → confirm). Holds the wizard state in the view scope; business rules
 * live in the domain and the {@link CreateOrderFromCartUseCase}. The confirmation
 * page reloads the persisted order by id (PRG), so it survives a browser refresh.
 *
 * <p>The review lines come from the customer's <b>persisted</b> cart
 * ({@link GetCartUseCase}) — the cart page is the single place to edit lines.
 * {@link CreateOrderFromCartUseCase} re-reads that same cart and clears it on a
 * confirmed order, so the review and the placed order can never diverge.
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
    private GetCartUseCase getCart;

    @Inject
    private ShippingRatePort shippingRate;

    @Inject
    private SessionPort session;

    @Inject
    private QuoteDiscountUseCase couponQuote;

    private int step = STEP_REVIEW;
    private final String requestId = UUID.randomUUID().toString();

    private CartView cartView;
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
        if (session.getCurrentUser().isPresent()) {
            cartView = getCart.getCart(session.getCurrentUser().get().getId());
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
            addGlobal(FacesMessage.SEVERITY_ERROR, "Empty cart", "Add products to your cart before checking out");
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
        try {
            confirmedOrder = createOrder.checkout(new CheckoutCommand(
                    requestId, user.get().getId(), email, buildAddress(),
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

    // ---- cart review helpers ----

    /** Review lines from the persisted cart, enriched with live catalog data. */
    public List<CartLineView> getReviewLines() {
        return cartView == null ? List.of() : cartView.lines();
    }

    public Money getSubtotal() {
        return cartView == null ? Money.zero() : cartView.subtotal();
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
}
