package com.aliwudi.marketplace.backend.notification.service;

import com.aliwudi.marketplace.backend.common.exception.NotificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * SMTP implementation of EmailSenderService using Spring's JavaMailSender.
 * Supports sending HTML emails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSenderService implements EmailSenderService {

    private final JavaMailSender mailSender;

    @Value("${notification.email.from}") // Inject 'from' email from application.properties
    private String fromEmail;

    /**
     * Sends an email asynchronously using JavaMailSender.
     *
     * @param to The recipient's email address.
     * @param subject The subject line of the email.
     * @param htmlBody The content of the email, assumed to be HTML.
     * @return Mono<Void> indicating success or error.
     */
    @Override
    public Mono<Void> sendEmail(String to, String subject, String htmlBody) {
        return Mono.fromCallable(() -> {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8"); // true for multipart, enables HTML

            try {
                helper.setFrom(fromEmail);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true); // true indicates HTML content
                mailSender.send(mimeMessage); // This is a blocking call
                log.info("Email sent successfully to: {}", to);
                return null; // For Mono<Void> completion
            } catch (MessagingException | MailException e) {
                log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
                throw new NotificationException("Failed to send email to " + to, e); // Wrap in NotificationException
            }
        })
        .onErrorResume(NotificationException.class, Mono::error) // Propagate custom exception
        .onErrorResume(e -> { // Catch any other unexpected errors
            log.error("An unexpected error occurred during email sending for {}: {}", to, e.getMessage(), e);
            return Mono.error(new NotificationException("An unexpected error occurred during email sending.", e));
        })
        .then() // Convert Mono<Object> to Mono<Void>
        .subscribeOn(Schedulers.boundedElastic()); // Run blocking mail send on a separate thread
    }
}