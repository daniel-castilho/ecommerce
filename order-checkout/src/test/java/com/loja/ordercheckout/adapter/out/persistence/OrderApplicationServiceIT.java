package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.adapter.out.notification.NotificationMockAdapter;
import com.loja.ordercheckout.adapter.out.payment.PaymentGatewayMockAdapter;
import com.loja.ordercheckout.adapter.out.shipping.ShippingRateMockAdapter;
import com.loja.ordercheckout.application.service.OrderApplicationService;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.PaymentMethod;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.ordercheckout.domain.port.in.CreateOrderFromCartUseCase.CheckoutCommand;
import com.loja.ordercheckout.domain.port.in.CreateOrderFromCartUseCase.ItemCheckoutRequest;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E2E workflow: OrderApplicationService with a real Order repository (Postgres via
 * Testcontainers) plus the mock payment/shipping/notification adapters. The catalog
 * port is mocked since product persistence belongs to product-catalog ITs.
 */
class OrderApplicationServiceIT extends AbstractIntegrationTest {

    private OrderRepositoryAdapter orderRepository;
    private ProductRepositoryPort productRepository;
    private PaymentGatewayMockAdapter paymentGateway;
    private ShippingRateMockAdapter shippingRate;
    private NotificationMockAdapter notification;
    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        orderRepository = new OrderRepositoryAdapter();
        orderRepository.em = em;
        productRepository = mock(ProductRepositoryPort.class);
        paymentGateway = new PaymentGatewayMockAdapter();
        shippingRate = new ShippingRateMockAdapter();
        notification = new NotificationMockAdapter();
        service = new OrderApplicationService(orderRepository, productRepository,
                paymentGateway, shippingRate, notification);

        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE tb_order_item, tb_order RESTART IDENTITY CASCADE")
                .executeUpdate();
        em.getTransaction().commit();
        em.clear();

        when(productRepository.findById("p1")).thenReturn(Optional.of(
                new Product("p1", new Sku("SKU-p1"), new Slug("slug-p1"), "Product A",
                        null, null, new Money(new BigDecimal("10.00")), null, 5,
                        ProductStatus.ACTIVE, null, null, null, Set.of(1L), List.of())));
        when(productRepository.decrementStock(anyString(), anyInt())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    private <T> T inTx(Supplier<T> operation) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            T result = operation.get();
            tx.commit();
            return result;
        } catch (RuntimeException | Error e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.clear();
        }
    }

    private CheckoutCommand command(String requestId) {
        ShippingAddress address = new ShippingAddress("Ana Souza", "Rua das Flores", "123", null,
                "Centro", "Sao Paulo", "SP", "01310-100", null);
        return new CheckoutCommand(requestId, "user-1", "ana@example.com",
                List.of(new ItemCheckoutRequest("p1", 2)), address, "pac",
                new PaymentMethod("card", "tok_test"));
    }

    @Test
    void checkout_persistsConfirmedOrderAndNotifies() {
        Order order = inTx(() -> service.checkout(command("e2e-1")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(productRepository).decrementStock("p1", 2);

        Optional<Order> restored = inTx(() -> orderRepository.findById("e2e-1"));
        assertThat(restored).isPresent();
        assertThat(restored.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(restored.get().getCustomerEmail()).isEqualTo("ana@example.com");
        assertThat(restored.get().getItems()).hasSize(1);
        assertThat(restored.get().getShippingCost().getAmount()).isEqualByComparingTo("15.00");
        assertThat(restored.get().getTotal().getAmount()).isEqualByComparingTo("35.00");

        assertThat(notification.getNotifications()).hasSize(1);
        assertThat(notification.getNotifications().get(0))
                .contains("ORDER_CONFIRMED", "order=e2e-1", "email=ana@example.com");
    }

    @Test
    void checkout_duplicateRequestId_createsSingleOrder() {
        Order first = inTx(() -> service.checkout(command("e2e-dup")));
        Order second = inTx(() -> service.checkout(command("e2e-dup")));

        assertThat(second.getId()).isEqualTo(first.getId());
        verify(productRepository, times(1)).decrementStock(anyString(), anyInt());

        Long rowCount = inTx(() -> (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM tb_order WHERE id = 'e2e-dup'").getSingleResult());
        assertThat(rowCount).isEqualTo(1L);
        assertThat(notification.getNotifications()).hasSize(1);
    }
}
