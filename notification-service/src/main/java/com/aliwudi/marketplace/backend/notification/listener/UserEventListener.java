package com.aliwudi.marketplace.backend.notification.listener;

import static com.aliwudi.marketplace.backend.common.constants.QueueType.*;
import com.aliwudi.marketplace.backend.common.dto.event.PasswordResetRequestedEvent;
import com.aliwudi.marketplace.backend.common.dto.event.UserRegisteredEvent;
import com.aliwudi.marketplace.backend.common.dto.event.EmailVerificationRequestedEvent;
import com.aliwudi.marketplace.backend.notification.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Listener for user-related events from RabbitMQ, triggering email notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final EmailNotificationService emailNotificationService;

    // Define OTP validity for template variable (should match EmailVerificationService)
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(5);
    private static final Duration PASSWORD_RESET_TOKEN_VALIDITY = Duration.ofHours(1);


    /**
     * Listens for EmailVerificationRequestedEvent messages and sends an OTP email.
     *
     * @param event The EmailVerificationRequestedEvent message received from the queue.
     */
    @RabbitListener(queues = EMAIL_VERIFICATION_QUEUE)
    public void handleEmailVerificationRequest(EmailVerificationRequestedEvent event) {
        log.info("Received EmailVerificationRequestedEvent for user: {} ({})", event.getName(), event.getEmail());

        String subject = "Your Email Verification Code"; // Can be externalized in messages.properties
        String templateName = "email/verification-code"; // Path to your Thymeleaf template

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("otpCode", event.getAuthServerUserId()); // Assuming authServerUserId is the OTP for demo
        templateVariables.put("otpValidityMinutes", OTP_VALIDITY.toMinutes());
        templateVariables.put("name", event.getName()); // For personalization
        // Add other common variables if needed, e.g., app name, current year, etc.

        emailNotificationService.sendTemplatedEmail(
            event.getEmail(),
            subject,
            templateName,
            templateVariables
        ).subscribe(
            v -> log.info("Email verification OTP successfully sent to {}", event.getEmail()),
            e -> log.error("Failed to send email verification OTP to {}: {}", event.getEmail(), e.getMessage(), e)
        );
    }

    /**
     * Listens for UserRegisteredEvent messages and sends a welcome/onboarding email.
     *
     * @param event The UserRegisteredEvent message received from the queue.
     */
    @RabbitListener(queues = REGISTRATION_ONBOARDING_QUEUE)
    public void handleUserRegistration(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for user: {} ({})", event.getName(), event.getEmail());

        String subject = "Welcome to Our Application!"; // Can be externalized
        String templateName = "email/registration-success"; // Path to your Thymeleaf template

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("name", event.getName());
        templateVariables.put("loginUrl", event.getLoginUrl());
        // Add other common variables if needed

        emailNotificationService.sendTemplatedEmail(
            event.getEmail(),
            subject,
            templateName,
            templateVariables
        ).subscribe(
            v -> log.info("Welcome email successfully sent to {}", event.getEmail()),
            e -> log.error("Failed to send welcome email to {}: {}", event.getEmail(), e.getMessage(), e)
        );
    }

    /**
     * Listens for PasswordResetRequestedEvent messages and sends a password reset email.
     *
     * @param event The PasswordResetRequestedEvent message received from the queue.
     */
    @RabbitListener(queues = PASSWORD_RESET_QUEUE)
    public void handlePasswordResetRequest(PasswordResetRequestedEvent event) {
        log.info("Received PasswordResetRequestedEvent for user: {} ({})", event.getName(), event.getEmail());

        String subject = "Password Reset Request"; // Can be externalized
        String templateName = "email/password-reset"; // Path to your Thymeleaf template

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("name", event.getName());
        templateVariables.put("resetLink", event.getResetLink());
        templateVariables.put("tokenValidityHours", PASSWORD_RESET_TOKEN_VALIDITY.toHours());
        // Add other common variables if needed

        emailNotificationService.sendTemplatedEmail(
            event.getEmail(),
            subject,
            templateName,
            templateVariables
        ).subscribe(
            v -> log.info("Password reset email successfully sent to {}", event.getEmail()),
            e -> log.error("Failed to send password reset email to {}: {}", event.getEmail(), e.getMessage(), e)
        );
    }
}