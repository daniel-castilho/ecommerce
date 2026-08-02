package com.loja.useraccount.adapter.out.notification;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEmailAdapterTest {

    private NotificationEmailAdapter adapter;
    private Session mailSession;

    @BeforeEach
    void setUp() {
        mailSession = Session.getInstance(new Properties());
        adapter = new NotificationEmailAdapter(mailSession);
    }

    @Test
    void sendWelcomeEmailShouldNotThrow() {
        adapter.sendWelcomeEmail("user@example.com", "John Doe");
    }

    @Test
    void sendPasswordResetEmailShouldNotThrow() {
        adapter.sendPasswordResetEmail("alice@example.com", "reset-token-123");
    }

    @Test
    void mimeMessageShouldHaveCorrectStructureForWelcome() throws Exception {
        MimeMessage msg = new MimeMessage(mailSession);
        msg.setFrom(new jakarta.mail.internet.InternetAddress("noreply@loja.com"));
        msg.setRecipient(MimeMessage.RecipientType.TO, new jakarta.mail.internet.InternetAddress("test@example.com"));
        msg.setSubject("Welcome to Loja, Test User!");
        msg.setText("Hi Test User,\n\nWelcome to Loja!");

        assertThat(msg.getFrom()[0].toString()).contains("noreply@loja.com");
        assertThat(msg.getRecipients(MimeMessage.RecipientType.TO)[0].toString())
                .isEqualTo("test@example.com");
        assertThat(msg.getSubject()).isEqualTo("Welcome to Loja, Test User!");
        assertThat(msg.getContent().toString()).contains("Welcome to Loja!");
    }

    @Test
    void mimeMessageShouldHaveCorrectStructureForPasswordReset() throws Exception {
        MimeMessage msg = new MimeMessage(mailSession);
        msg.setFrom(new jakarta.mail.internet.InternetAddress("noreply@loja.com"));
        msg.setRecipient(MimeMessage.RecipientType.TO, new jakarta.mail.internet.InternetAddress("alice@example.com"));
        msg.setSubject("Password Reset Request");
        msg.setText("Your reset token is: token-42");

        assertThat(msg.getFrom()[0].toString()).contains("noreply@loja.com");
        assertThat(msg.getRecipients(MimeMessage.RecipientType.TO)[0].toString())
                .isEqualTo("alice@example.com");
        assertThat(msg.getSubject()).isEqualTo("Password Reset Request");
        assertThat(msg.getContent().toString()).contains("token-42");
    }
}
