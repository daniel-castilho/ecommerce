package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import com.loja.ordercheckout.domain.port.out.NotificationDeliveryLogPort;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dispatches due outbox rows (see {@link NotificationDeliveryLogPort#findDue(int)})
 * over Jakarta Mail. Runs on a fixed schedule from {@link NotificationOutboxDispatcher};
 * a delivery failure is recorded as FAILED (bumped attempt) and never rethrown, so the
 * poller transaction always commits and exhausted attempts simply stop being due.
 */
@ApplicationScoped
public class NotificationOutboxProcessor {

    private static final Logger LOG = Logger.getLogger(NotificationOutboxProcessor.class.getName());
    private static final String FROM = "noreply@loja.com";
    private static final int BATCH_SIZE = 50;

    @Resource(name = "java:app/env/mail/Session")
    private Session mailSession;

    @Inject
    private NotificationDeliveryLogPort deliveryLog;

    private final MailSender mailSender;

    protected NotificationOutboxProcessor() {
        this.mailSender = null;
    }

    NotificationOutboxProcessor(Session mailSession, NotificationDeliveryLogPort deliveryLog) {
        this.mailSession = mailSession;
        this.deliveryLog = deliveryLog;
        this.mailSender = null;
    }

    /** Test seam: injects a fake sender so both SENT and FAILED paths are unit-testable. */
    NotificationOutboxProcessor(MailSender mailSender, NotificationDeliveryLogPort deliveryLog) {
        this.mailSender = mailSender;
        this.deliveryLog = deliveryLog;
    }

    /** Sends every due delivery, updating the log to SENT or FAILED per delivery. */
    @Transactional
    public void processPending() {
        for (NotificationDelivery due : deliveryLog.findDue(BATCH_SIZE)) {
            try {
                if (mailSender != null) {
                    mailSender.send(due);
                } else {
                    sendMime(due);
                }
                deliveryLog.updateStatus(due.getIdempotencyKey(), NotificationDeliveryStatus.SENT, null);
                LOG.info("Sent " + due.getEventType() + " email to " + due.getRecipientEmail()
                        + " for " + due.getAggregateId());
            } catch (MessagingException | RuntimeException e) {
                deliveryLog.updateStatus(due.getIdempotencyKey(), NotificationDeliveryStatus.FAILED, e.getMessage());
                LOG.log(Level.WARNING, "Failed to send " + due.getEventType() + " email to "
                        + due.getRecipientEmail() + " for " + due.getAggregateId(), e);
            }
        }
    }

    private void sendMime(NotificationDelivery delivery) throws MessagingException {
        MimeMessage msg = new MimeMessage(mailSession);
        msg.setFrom(new InternetAddress(FROM));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(delivery.getRecipientEmail()));
        msg.setSubject(delivery.getSubject());
        msg.setText(delivery.getBody());
        Transport.send(msg);
    }

    /** Sends a single delivery; implementers must throw on failure. */
    @FunctionalInterface
    interface MailSender {
        void send(NotificationDelivery delivery) throws MessagingException;
    }
}