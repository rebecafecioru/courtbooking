package com.courtbooking.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

// Which sender is used: real SMTP only when a mail host is actually set
class EmailConfigTest {

    private static final String FROM = "Court Booking <no-reply@example.com>";

    private ObjectProvider<JavaMailSender> withMailSender() {
        return new StaticListableBeanFactory(Map.of("mailSender", new JavaMailSenderImpl()))
                .getBeanProvider(JavaMailSender.class);
    }

    private ObjectProvider<JavaMailSender> withoutMailSender() {
        return new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class);
    }

    @Test
    void smtpWhenAMailHostIsSet() {
        assertInstanceOf(SmtpEmailSender.class,
                new EmailConfig().emailSender(withMailSender(), "smtp.example.com", FROM));
    }

    @Test
    void logOnlyWhenTheMailHostIsBlank() {
        // docker-compose passes SPRING_MAIL_HOST through even when SMTP_HOST is empty
        assertInstanceOf(LoggingEmailSender.class,
                new EmailConfig().emailSender(withMailSender(), "", FROM));
    }

    @Test
    void logOnlyWithNoMailSenderAtAll() {
        assertInstanceOf(LoggingEmailSender.class,
                new EmailConfig().emailSender(withoutMailSender(), "", FROM));
    }
}
