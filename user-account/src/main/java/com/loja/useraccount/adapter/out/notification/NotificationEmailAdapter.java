package com.loja.useraccount.adapter.out.notification;

import com.loja.useraccount.domain.port.out.NotificationPort;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class NotificationEmailAdapter implements NotificationPort {

    private static final Logger LOG = Logger.getLogger(NotificationEmailAdapter.class.getName());
    private static final String FROM = "noreply@loja.com";

    @Resource(lookup = "mail/Session")
    private Session mailSession;

    protected NotificationEmailAdapter() {}

    NotificationEmailAdapter(Session mailSession) {
        this.mailSession = mailSession;
    }

    @Override
    public void sendWelcomeEmail(String email, String fullName) {
        try {
            MimeMessage msg = new MimeMessage(mailSession);
            msg.setFrom(new InternetAddress(FROM));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
            msg.setSubject("Welcome to Loja, " + fullName + "!");
            msg.setText("Hi " + fullName + ",\n\nWelcome to Loja! Your account has been created successfully.\n\n"
                    + "You can now browse our catalog and place orders.\n\nBest regards,\nThe Loja Team");
            Transport.send(msg);
            LOG.info("Welcome email sent to " + email);
        } catch (MessagingException e) {
            LOG.log(Level.WARNING, "Failed to send welcome email to " + email, e);
        }
    }

    @Override
    public void sendPasswordResetEmail(String email, String token) {
        try {
            MimeMessage msg = new MimeMessage(mailSession);
            msg.setFrom(new InternetAddress(FROM));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
            msg.setSubject("Password Reset Request");
            msg.setText("Hi,\n\nYou have requested a password reset.\n\n"
                    + "Your reset token is: " + token + "\n\n"
                    + "If you did not request this, please ignore this email.\n\nBest regards,\nThe Loja Team");
            Transport.send(msg);
            LOG.info("Password reset email sent to " + email);
        } catch (MessagingException e) {
            LOG.log(Level.WARNING, "Failed to send password reset email to " + email, e);
        }
    }
}
