package com.aliwudi.marketplace.backend.user.auth.service;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import com.aliwudi.marketplace.backend.common.exception.EmailSendingException;

/**
 * Realistic implementation of EmailSenderService using Spring's JavaMailSender.
 * Requires SMTP configuration in application.properties.
 */
@Service
@Slf4j
public class SmtpEmailSenderService implements EmailSenderService {

    private final JavaMailSender mailSender;

    public SmtpEmailSenderService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public Mono<Void> sendEmail(String to, String subject, String body) {
        return Mono.fromCallable(() -> { // Changed from Mono.fromRunnable to Mono.fromCallable
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            // message.setFrom("your-verified-sender@example.com"); // OPTIONAL: Set a 'from' address if your SMTP server requires/overrides it

            try {
                mailSender.send(message); // Blocking operation
                log.info("Email sent successfully to: {}", to);
                return null; // Return null to signal successful completion for Mono<Void>
            } catch (MailException e) {
                log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
                // Throwing the exception here will cause Mono.fromCallable to emit an error signal
                throw new EmailSendingException("Failed to send verification email to " + to, e);
            }
        })
        .onErrorResume(EmailSendingException.class, Mono::error) // Ensure your specific exception is propagated as an error signal
        .onErrorResume(MailException.class, e -> { // Catch any general MailException and wrap it
            log.error("An unexpected MailException occurred when sending email to {}: {}", to, e.getMessage(), e);
            return Mono.error(new EmailSendingException("An unexpected email sending error occurred for " + to, e));
        })
        .then() // Crucial: Converts Mono<Object> (from fromCallable returning null) to Mono<Void>
                // This ensures the pipeline correctly signals completion for a Void type.
        .subscribeOn(Schedulers.boundedElastic()); // Offload the blocking operation to a suitable scheduler
    }
}