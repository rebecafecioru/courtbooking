package com.courtbooking.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Used when no mail server is configured (local dev, tests): writes the
// email to the log instead, so you can still see what would have been sent.
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("Email (not sent, no mail server configured) to {}: {}\n{}",
                message.to(), message.subject(), message.body());
    }
}
