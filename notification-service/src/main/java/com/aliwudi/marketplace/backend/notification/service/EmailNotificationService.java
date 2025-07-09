package com.aliwudi.marketplace.backend.notification.service;

import com.aliwudi.marketplace.backend.common.exception.NotificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource; // For i18n in templates
import org.springframework.context.i18n.LocaleContextHolder; // For locale resolution
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;

/**
 * Service responsible for orchestrating the email notification process:
 * rendering templates and dispatching emails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final EmailSenderService emailSenderService;
    private final TemplateEngine templateEngine;
    private final MessageSource messageSource; // Inject MessageSource for i18n in templates

    // Define OTP validity for template variable
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(5);
    private static final Duration PASSWORD_RESET_TOKEN_VALIDITY = Duration.ofHours(1);


    /**
     * Renders a Thymeleaf template with provided variables and sends the resulting HTML email.
     *
     * @param to The recipient email address.
     * @param subject The subject line of the email.
     * @param templateName The name of the Thymeleaf template (e.g., "email/welcome").
     * @param templateVariables A map of variables to pass to the template.
     * @return Mono<Void> indicating completion.
     */
    public Mono<Void> sendTemplatedEmail(String to, String subject, String templateName, Map<String, Object> templateVariables) {
        return Mono.fromCallable(() -> {
            // Create Thymeleaf context and add variables
            Context context = new Context(LocaleContextHolder.getLocale());
            templateVariables.forEach(context::setVariable); // Add all provided variables

            // Add common variables that all templates might need
            context.setVariable("appName", messageSource.getMessage("app.name", null, LocaleContextHolder.getLocale()));
            context.setVariable("currentYear", String.valueOf(java.time.Year.now().getValue()));


            // Process the template to get the HTML body
            String htmlBody = templateEngine.process(templateName, context);
            log.debug("Processed template '{}' for email to {}.", templateName, to);

            // Send the email
            return emailSenderService.sendEmail(to, subject, htmlBody);
        })
        .flatMap(m -> m) // Flatten the Mono<Mono<Void>> returned by sendEmail
        .doOnSuccess(v -> log.info("Successfully requested email to {} using template {}", to, templateName))
        .onErrorResume(e -> { // Catch any errors during template processing or email sending
            log.error("Failed to send email to {} using template {}: {}", to, templateName, e.getMessage(), e);
            return Mono.error(new NotificationException("Failed to send templated email.", e));
        })
        .subscribeOn(Schedulers.boundedElastic()); // Use a separate scheduler for blocking email operations
    }
}