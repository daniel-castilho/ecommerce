package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.shared.domain.Money;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import jakarta.mail.Session;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderNotificationEmailAdapterTest {

    private static final String POSTAL_CODE = "01000-000";

    private static final Order ORDER = Order.create("user-1", "buyer@example.com",
            List.of(new OrderLine("SKU-001", "QA Test Widget", new Money(new BigDecimal("29.90")), 1, 0)),
            new ShippingAddress("Ana Souza", "Rua das Flores", "123", null, "Centro",
                    "São Paulo", "SP", POSTAL_CODE, "11999999999"));

    /** SMTP pointing at a port that refuses connections so send fails fast. */
    private Session deadSmtp() {
        Properties props = new Properties();
        props.put("mail.smtp.host", "localhost");
        props.put("mail.smtp.port", "1");
        props.put("mail.smtp.connectiontimeout", "1000");
        props.put("mail.smtp.timeout", "1500");
        return Session.getInstance(props);
    }

    private User user(boolean notificationsEnabled) {
        return User.create(new Email("buyer@example.com"), UserPassword.fromHash("argon2id-placeholder"),
                new UserProfile("Ana", "Souza", "11999999999", "pt", notificationsEnabled));
    }

    @Test
    void notifyOrderConfirmed_smtpDown_doesNotThrow() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById("user-1")).thenReturn(Optional.of(user(true)));
        OrderNotificationEmailAdapter adapter = new OrderNotificationEmailAdapter(deadSmtp(), users);

        assertThatCode(() -> adapter.notifyOrderConfirmed(ORDER)).doesNotThrowAnyException();
    }

    @Test
    void notifyOrderConfirmed_notificationsDisabled_skipsSendAndDoesNotThrow() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById("user-1")).thenReturn(Optional.of(user(false)));
        OrderNotificationEmailAdapter adapter = new OrderNotificationEmailAdapter(deadSmtp(), users);

        assertThatCode(() -> adapter.notifyOrderConfirmed(ORDER)).doesNotThrowAnyException();
    }

    @Test
    void notifyOrderConfirmed_userNotFound_defaultsToSendAndDoesNotThrow() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById("user-1")).thenReturn(Optional.empty());
        OrderNotificationEmailAdapter adapter = new OrderNotificationEmailAdapter(deadSmtp(), users);

        assertThatCode(() -> adapter.notifyOrderConfirmed(ORDER)).doesNotThrowAnyException();
    }

    @Test
    void notifyRefundRejected_smtpDown_doesNotThrow() {
        FindUserUseCase users = mock(FindUserUseCase.class);
        when(users.findById("user-1")).thenReturn(Optional.of(user(true)));
        OrderNotificationEmailAdapter adapter = new OrderNotificationEmailAdapter(deadSmtp(), users);
        RefundRequest request = RefundRequest.request(ORDER.getId(), new Money(new BigDecimal("29.90")), "damaged");
        request.reject("Camera not returned");

        assertThatCode(() -> adapter.notifyRefundRejected(ORDER, request)).doesNotThrowAnyException();
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