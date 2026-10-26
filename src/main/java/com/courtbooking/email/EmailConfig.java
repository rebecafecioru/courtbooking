package com.courtbooking.email;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class EmailConfig {

    // Real email when a mail server is configured (spring.mail.host, i.e.
    // SPRING_MAIL_HOST); otherwise emails go to the log. A blank host counts
    // as none: docker-compose passes SMTP_HOST through even when it's empty,
    // and Spring would still create a mail sender for it.
    @Bean
    public EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender,
                                   @Value("${spring.mail.host:}") String host,
                                   @Value("${app.mail.from:Court Booking <no-reply@localhost>}") String from) {
        JavaMailSender smtp = mailSender.getIfAvailable();
        return smtp != null && !host.isBlank() ? new SmtpEmailSender(smtp, from) : new LoggingEmailSender();
    }
}
