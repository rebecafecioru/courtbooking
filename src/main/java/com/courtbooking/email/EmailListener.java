package com.courtbooking.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the emails BookingService publishes, but only after its transaction
 * has committed: a booking that gets rolled back (say, a weekly series that
 * turned out not to be free) never sends a "you're booked" email.
 * <p>
 * Sent on a background thread, so a slow mail server can't hold up the
 * booking, or the hall lock it held. If sending fails, it's logged; the
 * booking itself has already happened.
 */
@Component
public class EmailListener {

    private static final Logger log = LoggerFactory.getLogger(EmailListener.class);

    private final EmailSender emailSender;

    public EmailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmail(EmailMessage message) {
        try {
            emailSender.send(message);
        } catch (RuntimeException e) {
            log.warn("Could not send email '{}' to {}", message.subject(), message.to(), e);
        }
    }
}
