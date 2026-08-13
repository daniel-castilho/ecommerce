package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.model.NotificationChannel;
import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import com.loja.ordercheckout.domain.port.out.NotificationDeliveryLogPort;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationOutboxProcessorTest {

    private static NotificationDelivery due() {
        return NotificationDelivery.create("ORDER_CONFIRMED:ord-1", "ORDER_CONFIRMED", "ord-1",
                NotificationChannel.EMAIL, "buyer@example.com", "Order ord-1 confirmed", "Hi,\n\nbody",
                "<html><body><h1>Order ord-1 confirmed</h1></body></html>");
    }

    private static Session mailSession() {
        Properties props = new Properties();
        props.setProperty("mail.from", "noreply@loja.com");
        return Session.getInstance(props);
    }

    @Test
    void processPending_success_marksSent() throws MessagingException {
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        when(log.findDue(50)).thenReturn(List.of(due()));
        NotificationOutboxProcessor processor =
                new NotificationOutboxProcessor(delivery -> {
                }, log);

        processor.processPending();

        verify(log).updateStatus("ORDER_CONFIRMED:ord-1", NotificationDeliveryStatus.SENT, null);
    }

    @Test
    void processPending_sendFailure_marksFailedAndDoesNotThrow() throws MessagingException {
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        when(log.findDue(50)).thenReturn(List.of(due()));
        NotificationOutboxProcessor processor = new NotificationOutboxProcessor(
                delivery -> {
                    throw new MessagingException("connection refused");
                }, log);

        assertThatCode(processor::processPending).doesNotThrowAnyException();

        verify(log).updateStatus("ORDER_CONFIRMED:ord-1", NotificationDeliveryStatus.FAILED,
                "connection refused");
    }

    @Test
    void processPending_noDueRows_doesNothing() throws MessagingException {
        NotificationDeliveryLogPort log = mock(NotificationDeliveryLogPort.class);
        when(log.findDue(50)).thenReturn(List.of());
        NotificationOutboxProcessor processor =
                new NotificationOutboxProcessor(delivery -> {
                }, log);

        processor.processPending();

        verify(log, never()).updateStatus(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void compose_htmlSnapshot_buildsMultipartAlternative() throws Exception {
        MimeMessage msg = NotificationOutboxProcessor.compose(due(), mailSession());
        msg.saveChanges();

        MimeMultipart multipart = (MimeMultipart) msg.getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
        assertThat(multipart.getBodyPart(0).getContentType()).startsWith("text/plain");
        assertThat(multipart.getBodyPart(0).getContent().toString()).isEqualTo("Hi,\n\nbody");
        assertThat(multipart.getBodyPart(1).getContentType()).startsWith("text/html");
        assertThat(multipart.getBodyPart(1).getContent().toString())
                .contains("<h1>Order ord-1 confirmed</h1>");
    }

    @Test
    void compose_noHtmlSnapshot_fallsBackToTextOnly() throws Exception {
        NotificationDelivery textOnly = NotificationDelivery.create("ORDER_CONFIRMED:ord-1",
                "ORDER_CONFIRMED", "ord-1", NotificationChannel.EMAIL, "buyer@example.com",
                "Order ord-1 confirmed", "Hi,\n\nbody");

        MimeMessage msg = NotificationOutboxProcessor.compose(textOnly, mailSession());

        assertThat(msg.getContent()).isInstanceOf(String.class);
        assertThat(msg.getContentType()).startsWith("text/plain");
        assertThat(msg.getContent().toString()).isEqualTo("Hi,\n\nbody");
    }
}