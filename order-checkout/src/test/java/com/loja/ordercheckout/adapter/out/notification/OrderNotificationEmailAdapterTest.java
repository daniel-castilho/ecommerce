package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.ordercheckout.domain.port.out.NotificationDeliveryLogPort;
import com.loja.shared.domain.Money;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderNotificationEmailAdapterTest {

    private static final String POSTAL_CODE = "01000-000";

    private static final Order ORDER = Order.create("user-1", "buyer@example.com",
            List.of(new OrderLine("SKU-001", "QA Test Widget", new Money(new BigDecimal("29.90")), 1, 0)),
            new ShippingAddress("Ana Souza", "Rua das Flores", "123", null, "Centro",
                    "São Paulo", "SP", POSTAL_CODE, "11999999999"));

    private User user(boolean notificationsEnabled) {
        return User.create(new Email("buyer@example.com"), UserPassword.fromHash("argon2id-placeholder"),
                new UserProfile("Ana", "Souza", "11999999999", "pt", notificationsEnabled));
    }

    @Test
    void notifyOrderConfirmed_claimsPendingRowWithEmailSnapshot() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById("user-1")).thenReturn(Optional.of(user(true)));
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        when(log.claim(any(NotificationDelivery.class))).thenReturn(true);
        OrderNotificationEmailAdapter adapter = new OrderNotificationEmailAdapter(users, log);

        assertThatCode(() -> adapter.notifyOrderConfirmed(ORDER)).doesNotThrowAnyException();

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(log).claim(captor.capture());
        NotificationDelivery claimed = captor.getValue();
        assertThat(claimed.getIdempotencyKey()).isEqualTo("ORDER_CONFIRMED:" + ORDER.getId());
        assertThat(claimed.getEventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(claimed.getAggregateId()).isEqualTo(ORDER.getId());
        assertThat(claimed.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(claimed.getRecipientEmail()).isEqualTo("buyer@example.com");
        assertThat(claimed.getSubject()).isEqualTo("Order " + ORDER.getId() + " confirmed");
        assertThat(claimed.getBody())
                .contains("- QA Test Widget x 1 ($29.90)")
                .contains("Total: $29.90");
    }

    @Test
    void notifyOrderConfirmed_notificationsDisabled_skipsWithoutClaiming() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById("user-1")).thenReturn(Optional.of(user(false)));
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        OrderNotificationEmailAdapter adapter = new OrderNotificationEmailAdapter(users, log);

        assertThatCode(() -> adapter.notifyOrderConfirmed(ORDER)).doesNotThrowAnyException();

        verify(log, never()).claim(any(NotificationDelivery.class));
        verify(log, never()).updateStatus(any(), any(), any());
    }

    @Test
    void notifyOrderConfirmed_duplicateEvent_skipsEnqueue() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById("user-1")).thenReturn(Optional.of(user(true)));
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        when(log.claim(any(NotificationDelivery.class))).thenReturn(false);
        OrderNotificationEmailAdapter adapter = new OrderNotificationEmailAdapter(users, log);

        assertThatCode(() -> adapter.notifyOrderConfirmed(ORDER)).doesNotThrowAnyException();

        verify(log).claim(any(NotificationDelivery.class));
        verify(log, never()).updateStatus(any(), any(), any());
    }

    @Test
    void notifyOrderConfirmed_userNotFound_defaultsToEnqueue() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById("user-1")).thenReturn(Optional.empty());
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        when(log.claim(any(NotificationDelivery.class))).thenReturn(true);
        OrderNotificationEmailAdapter adapter = new OrderNotificationEmailAdapter(users, log);

        assertThatCode(() -> adapter.notifyOrderConfirmed(ORDER)).doesNotThrowAnyException();

        verify(log).claim(any(NotificationDelivery.class));
    }

    @Test
    void notifyRefundRejected_claimsPendingRowWithRejectionSnapshot() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById("user-1")).thenReturn(Optional.of(user(true)));
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        when(log.claim(any(NotificationDelivery.class))).thenReturn(true);
        OrderNotificationEmailAdapter adapter = new OrderNotificationEmailAdapter(users, log);
        RefundRequest request = RefundRequest.request(ORDER.getId(), new Money(new BigDecimal("29.90")), "damaged");
        request.reject("Camera not returned");

        adapter.notifyRefundRejected(ORDER, request);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(log).claim(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("REFUND_REJECTED:" + ORDER.getId());
        assertThat(captor.getValue().getBody()).contains("Rejection detail: Camera not returned");
    }

    @Test
    void notifyOrderConfirmed_inMemoryMockAdapterStillRecords() {
        NotificationMockAdapter mock = new NotificationMockAdapter();

        mock.notifyOrderConfirmed(ORDER);

        assertThat(mock.getNotifications()).singleElement().satisfies(entry -> {
            assertThat(entry).contains("ORDER_CONFIRMED");
            assertThat(entry).contains(ORDER.getId());
            assertThat(entry).contains("buyer@example.com");
        });
    }
}