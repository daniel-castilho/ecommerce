package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.domain.exception.AccountSuspendedException;
import com.loja.ordercheckout.domain.exception.PaymentFailedException;
import com.loja.ordercheckout.domain.exception.ShippingException;
import com.loja.ordercheckout.domain.model.Cart;
import com.loja.ordercheckout.domain.model.CartLine;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.PaymentAuthorization;
import com.loja.ordercheckout.domain.model.PaymentCapture;
import com.loja.ordercheckout.domain.model.PaymentMethod;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.ordercheckout.domain.model.ShippingOption;
import com.loja.ordercheckout.application.dto.CheckoutCommand;
import com.loja.ordercheckout.domain.port.out.CartRepositoryPort;
import com.loja.ordercheckout.domain.port.out.NotificationPort;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.PaymentGatewayPort;
import com.loja.ordercheckout.domain.port.out.ShippingRatePort;
import com.loja.productcatalog.application.dto.ReservationRequest;
import com.loja.productcatalog.domain.exception.InsufficientStockException;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.InventoryReservationPort;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.promotions.application.dto.DiscountQuote;
import com.loja.promotions.domain.exception.CouponNotApplicableException;
import com.loja.promotions.domain.exception.CouponNotFoundException;
import com.loja.promotions.domain.port.in.QuoteDiscountUseCase;
import com.loja.promotions.domain.port.in.RecordCouponRedemptionUseCase;
import com.loja.shared.domain.Money;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import com.loja.useraccount.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderApplicationServiceTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final CartRepositoryPort cartRepository = mock(CartRepositoryPort.class);
    private final ProductRepositoryPort productRepository = mock(ProductRepositoryPort.class);
    private final PaymentGatewayPort paymentGateway = mock(PaymentGatewayPort.class);
    private final ShippingRatePort shippingRate = mock(ShippingRatePort.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final InventoryReservationPort inventoryReservation = mock(InventoryReservationPort.class);
    private final UserRepositoryPort userRepository = mock(UserRepositoryPort.class);
    private final QuoteDiscountUseCase couponQuote = mock(QuoteDiscountUseCase.class);
    private final RecordCouponRedemptionUseCase couponRedemption = mock(RecordCouponRedemptionUseCase.class);

    private OrderApplicationService service;
    private final Map<String, Order> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new OrderApplicationService(orderRepository, cartRepository,
                productRepository, paymentGateway, shippingRate, notification,
                inventoryReservation, userRepository, couponQuote, couponRedemption);
        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(user("ana@example.com", true)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            store.put(saved.getId(), saved);
            return saved;
        });
        when(orderRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(shippingRate.getQuotes(any())).thenReturn(List.of(
                new ShippingOption("pac", new Money(new BigDecimal("15.00")), 15, "PAC"),
                new ShippingOption("sedex", new Money(new BigDecimal("30.00")), 2, "SEDEX")));
    }

    private Product product(String id, Money price, int stock) {
        return new Product(id, new Sku("SKU-" + id), new Slug("slug-" + id), "Product " + id,
                null, null, price, null, stock, ProductStatus.ACTIVE,
                null, null, null, Set.of(1L), List.of());
    }

    private static User user(String email, boolean active) {
        User user = User.create(new Email(email),
                UserPassword.hash("password1234", new PasswordHasherPort() {
                    @Override
                    public String hash(String plainPassword) {
                        return "hash:" + plainPassword;
                    }

                    @Override
                    public boolean verify(String plainPassword, String hash) {
                        return ("hash:" + plainPassword).equals(hash);
                    }
                }),
                UserProfile.fromFullName("Test User"));
        if (!active) {
            user.deactivate();
        }
        return user;
    }

    private ShippingAddress address() {
        return new ShippingAddress("Ana Souza", "Rua das Flores", "123", null,
                "Centro", "Sao Paulo", "SP", "01310-100", null);
    }

    private CheckoutCommand command(String requestId) {
        return new CheckoutCommand(requestId, "user-1", "ana@example.com",
                address(), "pac", new PaymentMethod("card", "tok_test"), null);
    }

    private CheckoutCommand commandWithShipping(String requestId, String shippingMethod) {
        return new CheckoutCommand(requestId, "user-1", "ana@example.com",
                address(), shippingMethod, new PaymentMethod("card", "tok_test"), null);
    }

    private CheckoutCommand commandWithCoupon(String requestId, String couponCode) {
        return new CheckoutCommand(requestId, "user-1", "ana@example.com",
                address(), "pac", new PaymentMethod("card", "tok_test"), couponCode);
    }

    /** Seed the persisted cart (p1 x2, p2 x3) as the single source of truth. */
    private void stubCart() {
        stubCart(List.of(new CartLine("p1", 2), new CartLine("p2", 3)));
    }

    private void stubCart(List<CartLine> lines) {
        Cart cart = Cart.create("user-1");
        lines.forEach(line -> cart.add(line.productId(), line.quantity()));
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(cart));
    }

    private void stubProductsAndStock() {
        stubCart();
        when(productRepository.findById("p1")).thenReturn(Optional.of(
                product("p1", new Money(new BigDecimal("10.00")), 5)));
        when(productRepository.findById("p2")).thenReturn(Optional.of(
                product("p2", new Money(new BigDecimal("5.50")), 3)));
    }

    @Test
    void checkout_withValidCommand_createsConfirmedOrderSavesAndNotifies() {
        stubProductsAndStock();
        PaymentAuthorization auth = new PaymentAuthorization("card", "auth-1",
                new Money(new BigDecimal("51.50")), "tx-1", Instant.now());
        PaymentCapture capture = new PaymentCapture("auth-1", "capture-1",
                new Money(new BigDecimal("51.50")), "tx-1", Instant.now());
        when(paymentGateway.authorize(any(), any())).thenReturn(auth);
        when(paymentGateway.capture("auth-1")).thenReturn(capture);

        Order order = service.checkout(command("req-1"));

        assertThat(order.getId()).isEqualTo("req-1");
        assertThat(order.getUserId()).isEqualTo("user-1");
        assertThat(order.getCustomerEmail()).isEqualTo("ana@example.com");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getShippingCost().getAmount()).isEqualByComparingTo("15.00");
        assertThat(order.getTotal().getAmount()).isEqualByComparingTo("51.50");
        verify(inventoryReservation).reserve("req-1", List.of(
                new ReservationRequest("p1", 2),
                new ReservationRequest("p2", 3)));
        verify(inventoryReservation).confirm("req-1");
        verify(orderRepository).save(order);
        verify(cartRepository).deleteByUserId("user-1");
        verify(notification).notifyOrderConfirmed(order);
    }

    @Test
    void checkout_whenCaptureFails_returnsPendingOrderWithoutNotification() {
        stubProductsAndStock();
        when(paymentGateway.authorize(any(), any())).thenReturn(new PaymentAuthorization(
                "card", "auth-1", new Money(new BigDecimal("51.50")), "tx-1", Instant.now()));
        when(paymentGateway.capture("auth-1"))
                .thenThrow(new PaymentFailedException("Payment capture failed."));

        Order order = service.checkout(command("req-2"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentInfo().getStatus().name()).isEqualTo("AUTHORIZED");
        verify(inventoryReservation).release("req-2");
        verify(inventoryReservation, never()).confirm(anyString());
        verify(orderRepository).save(order);
        verify(cartRepository, never()).deleteByUserId(anyString());
        verify(notification, never()).notifyOrderConfirmed(any());
    }

    @Test
    void checkout_whenStockInsufficient_throwsBeforePaymentIsAttempted() {
        stubCart(List.of(new CartLine("p1", 2)));
        when(productRepository.findById("p1")).thenReturn(Optional.of(
                product("p1", new Money(new BigDecimal("10.00")), 0)));
        doThrow(new InsufficientStockException("Insufficient stock for product: p1"))
                .when(inventoryReservation).reserve(anyString(), anyList());

        assertThatThrownBy(() -> service.checkout(command("req-3")))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("p1");
        verify(paymentGateway, never()).authorize(any(), any());
        verify(paymentGateway, never()).capture(anyString());
        verify(inventoryReservation, never()).confirm(anyString());
        verify(inventoryReservation, never()).release(anyString());
        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).deleteByUserId(anyString());
        verify(notification, never()).notifyOrderConfirmed(any());
    }

    @Test
    void checkout_duplicateRequestId_returnsExistingOrderWithoutReprocessing() {
        stubProductsAndStock();
        when(paymentGateway.authorize(any(), any())).thenReturn(new PaymentAuthorization(
                "card", "auth-1", new Money(new BigDecimal("51.50")), "tx-1", Instant.now()));
        when(paymentGateway.capture("auth-1")).thenReturn(new PaymentCapture(
                "auth-1", "capture-1", new Money(new BigDecimal("51.50")), "tx-1", Instant.now()));

        Order first = service.checkout(command("req-dup"));
        Order second = service.checkout(command("req-dup"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second).isEqualTo(first);
        verify(inventoryReservation, times(1)).reserve(anyString(), anyList());
        verify(inventoryReservation, times(1)).confirm(anyString());
        verify(cartRepository, times(1)).deleteByUserId("user-1");
        verify(notification, times(1)).notifyOrderConfirmed(any());
    }

    @Test
    void checkout_unknownShippingMethod_throwsShippingException() {
        stubProductsAndStock();

        assertThatThrownBy(() -> service.checkout(commandWithShipping("req-4", "overnight")))
                .isInstanceOf(ShippingException.class)
                .hasMessageContaining("overnight");
    }

    @Test
    void checkout_withNoCart_throwsBeforePaymentIsAttempted() {
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(command("req-5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        verify(paymentGateway, never()).authorize(any(), any());
        verify(paymentGateway, never()).capture(anyString());
        verify(inventoryReservation, never()).reserve(anyString(), anyList());
        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).deleteByUserId(anyString());
    }

    @Test
    void checkout_withEmptyPresentCart_throwsBeforePaymentIsAttempted() {
        Cart empty = Cart.create("user-1");
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(empty));

        assertThatThrownBy(() -> service.checkout(command("req-5b")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        verify(paymentGateway, never()).authorize(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_withUnknownProduct_throwsIllegalArgumentException() {
        stubCart(List.of(new CartLine("missing", 1)));
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(command("req-6")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).deleteByUserId(anyString());
    }

    @Test
    void checkout_blockedCustomer_throwsBeforePaymentIsAttempted() {
        stubProductsAndStock();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user("ana@example.com", false)));

        assertThatThrownBy(() -> service.checkout(command("req-7")))
                .isInstanceOf(AccountSuspendedException.class)
                .hasMessageContaining("blocked");
        verify(paymentGateway, never()).authorize(any(), any());
        verify(paymentGateway, never()).capture(anyString());
        verify(inventoryReservation, never()).reserve(anyString(), anyList());
        verify(inventoryReservation, never()).confirm(anyString());
        verify(inventoryReservation, never()).release(anyString());
        verify(orderRepository, never()).save(any());
        verify(notification, never()).notifyOrderConfirmed(any());
    }

    @Test
    void checkout_unknownCustomer_throwsBeforePaymentIsAttempted() {
        stubProductsAndStock();
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(command("req-8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        verify(paymentGateway, never()).authorize(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_withCoupon_quotesAppliesDiscountAndRedeemsAfterSave() {
        stubProductsAndStock();
        when(couponQuote.quote("save10", new Money(new BigDecimal("36.50"))))
                .thenReturn(new DiscountQuote("SAVE10", new Money(new BigDecimal("3.65"))));
        when(paymentGateway.authorize(any(), any())).thenReturn(new PaymentAuthorization(
                "card", "auth-1", new Money(new BigDecimal("47.85")), "tx-1", Instant.now()));
        when(paymentGateway.capture("auth-1")).thenReturn(new PaymentCapture(
                "auth-1", "capture-1", new Money(new BigDecimal("47.85")), "tx-1", Instant.now()));

        Order order = service.checkout(commandWithCoupon("req-9", "save10"));

        assertThat(order.getCouponCode()).isEqualTo("SAVE10");
        assertThat(order.getDiscountAmount().getAmount()).isEqualByComparingTo("3.65");
        assertThat(order.getMerchandiseSubtotal().getAmount()).isEqualByComparingTo("36.50");
        assertThat(order.getTotal().getAmount()).isEqualByComparingTo("47.85");
        verify(couponQuote).quote("save10", new Money(new BigDecimal("36.50")));
        verify(couponRedemption).redeem("SAVE10");
        verify(cartRepository).deleteByUserId("user-1");
    }

    @Test
    void checkout_withInvalidCoupon_failsBeforePaymentOrRedemption() {
        stubProductsAndStock();
        when(couponQuote.quote(anyString(), any()))
                .thenThrow(new CouponNotFoundException("Coupon not found: SAVE10"));

        assertThatThrownBy(() -> service.checkout(commandWithCoupon("req-10", "SAVE10")))
                .isInstanceOf(CouponNotFoundException.class)
                .hasMessageContaining("not found");
        verify(paymentGateway, never()).authorize(any(), any());
        verify(paymentGateway, never()).capture(anyString());
        verify(inventoryReservation, never()).reserve(anyString(), anyList());
        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).deleteByUserId(anyString());
        verify(couponRedemption, never()).redeem(anyString());
    }

    @Test
    void checkout_withExhaustedCoupon_failsBeforePaymentOrRedemption() {
        stubProductsAndStock();
        when(couponQuote.quote(anyString(), any()))
                .thenThrow(new CouponNotApplicableException("Coupon exhausted"));

        assertThatThrownBy(() -> service.checkout(commandWithCoupon("req-11", "FULLY")))
                .isInstanceOf(CouponNotApplicableException.class)
                .hasMessageContaining("exhausted");
        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).deleteByUserId(anyString());
        verify(couponRedemption, never()).redeem(anyString());
    }

    @Test
    void checkout_whenCaptureFailsWithCoupon_doesNotRedeem() {
        stubProductsAndStock();
        when(couponQuote.quote("SAVE10", new Money(new BigDecimal("36.50"))))
                .thenReturn(new DiscountQuote("SAVE10", new Money(new BigDecimal("3.65"))));
        when(paymentGateway.authorize(any(), any())).thenReturn(new PaymentAuthorization(
                "card", "auth-1", new Money(new BigDecimal("47.85")), "tx-1", Instant.now()));
        when(paymentGateway.capture("auth-1"))
                .thenThrow(new PaymentFailedException("Payment capture failed."));

        Order order = service.checkout(commandWithCoupon("req-12", "SAVE10"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCouponCode()).isEqualTo("SAVE10");
        verify(cartRepository, never()).deleteByUserId(anyString());
        verify(couponRedemption, never()).redeem(anyString());
        verify(notification, never()).notifyOrderConfirmed(any());
    }
}
