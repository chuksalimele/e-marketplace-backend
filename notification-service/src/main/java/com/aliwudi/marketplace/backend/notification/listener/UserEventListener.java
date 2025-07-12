package com.aliwudi.marketplace.backend.notification.listener;

import static com.aliwudi.marketplace.backend.common.constants.QueueType.*;
import com.aliwudi.marketplace.backend.common.dto.event.PasswordResetRequestedEvent;
import com.aliwudi.marketplace.backend.common.dto.event.UserRegisteredEvent;
import com.aliwudi.marketplace.backend.common.dto.event.EmailVerificationRequestedEvent;
import com.aliwudi.marketplace.backend.common.dto.event.SmsVerificationRequestedEvent; // NEW IMPORT
import com.aliwudi.marketplace.backend.common.dto.event.PhoneCallVerificationRequestedEvent; // NEW IMPORT

import com.aliwudi.marketplace.backend.notification.service.EmailNotificationService;
import com.aliwudi.marketplace.backend.notification.service.SmsSenderService; // NEW IMPORT
import com.aliwudi.marketplace.backend.notification.service.PhoneCallService; // NEW IMPORT

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Listener for user-related events from RabbitMQ, triggering email, SMS, and Phone Call notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final EmailNotificationService emailNotificationService;
    private final SmsSenderService smsSenderService; // NEW INJECTION
    private final PhoneCallService phoneCallService; // NEW INJECTION

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

        String subject = "Email Verification OTP"; // Can be externalized
        String templateName = "email/verification-code"; // Path to your Thymeleaf template

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("name", event.getName());
        // CRITICAL CORRECTION: Ensure event.getOtpCode() actually carries the OTP
        templateVariables.put("otpCode", event.getOtpCode()); // Assuming OTP is now in the event DTO
        templateVariables.put("otpValidityMinutes", OTP_VALIDITY.toMinutes());
        // Add other common variables if needed

        emailNotificationService.sendTemplatedEmail(
            event.getEmail(), // This is the email address
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
        log.info("Received UserRegisteredEvent for user: {} ({})", event.getName(), event.getPrimaryIdentifier());

        String subject = "Welcome to Our Marketplace!"; // Can be externalized
        String templateName = "email/registration-success"; // Path to your Thymeleaf template

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("name", event.getName());
        templateVariables.put("loginUrl", "https://your-marketplace.com/login"); // Example login URL
        // Add other variables like initial steps, features etc.

        emailNotificationService.sendTemplatedEmail(
            event.getPrimaryIdentifier(), // This is the email address
            subject,
            templateName,
            templateVariables
        ).subscribe(
            v -> log.info("Welcome email successfully sent to {}", event.getPrimaryIdentifier()),
            e -> log.error("Failed to send welcome email to {}: {}", event.getPrimaryIdentifier(), e.getMessage(), e)
        );
    }

    /**
     * Listens for PasswordResetRequestedEvent messages and sends a password reset email.
     *
     * @param event The PasswordResetRequestedEvent message received from the queue.
     */
    @RabbitListener(queues = PASSWORD_RESET_QUEUE)
    public void handlePasswordResetRequest(PasswordResetRequestedEvent event) {
        log.info("Received PasswordResetRequestedEvent for user: {} ({})", event.getName(), event.getPrimaryIdentifier());

        String subject = "Password Reset Request"; // Can be externalized
        String templateName = "email/password-reset"; // Path to your Thymeleaf template

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("name", event.getName());
        templateVariables.put("resetLink", event.getResetLink());
        templateVariables.put("tokenValidityHours", PASSWORD_RESET_TOKEN_VALIDITY.toHours());
        // Add other common variables if needed

        emailNotificationService.sendTemplatedEmail(
            event.getPrimaryIdentifier(),
            subject,
            templateName,
            templateVariables
        ).subscribe(
            v -> log.info("Password reset email successfully sent to {}", event.getPrimaryIdentifier()),
            e -> log.error("Failed to send password reset email to {}: {}", event.getPrimaryIdentifier(), e.getMessage(), e)
        );
    }

    // --- NEW SMS NOTIFICATION HANDLER ---
    /**
     * Listens for SmsVerificationRequestedEvent messages and sends an OTP SMS.
     *
     * @param event The SmsVerificationRequestedEvent message received from the queue.
     */
    @RabbitListener(queues = SMS_VERIFICATION_QUEUE) // Assumes SMS_VERIFICATION_QUEUE exists
    public void handleSmsVerificationRequest(SmsVerificationRequestedEvent event) {
        log.info("Received SmsVerificationRequestedEvent for user: {} ({})", event.getName(), event.getPhoneNumber());

        String smsMessage = String.format("Your verification code is %s. It is valid for %d minutes.",
            event.getOtpCode(), OTP_VALIDITY.toMinutes()); // Assuming OTP is in event DTO

        smsSenderService.sendSms(
            event.getPhoneNumber(),
            smsMessage
        ).subscribe(
            v -> log.info("SMS verification OTP successfully sent to {}", event.getPhoneNumber()),
            e -> log.error("Failed to send SMS verification OTP to {}: {}", event.getPhoneNumber(), e.getMessage(), e)
        );
    }

    // --- NEW PHONE CALL NOTIFICATION HANDLER ---
    /**
     * Listens for PhoneCallVerificationRequestedEvent messages and initiates a voice call with OTP.
     *
     * @param event The PhoneCallVerificationRequestedEvent message received from the queue.
     */
    @RabbitListener(queues = PHONE_CALL_VERIFICATION_QUEUE) // Assumes PHONE_CALL_VERIFICATION_QUEUE exists
    public void handlePhoneCallVerificationRequest(PhoneCallVerificationRequestedEvent event) {
        log.info("Received PhoneCallVerificationRequestedEvent for user: {} ({})", event.getName(), event.getPhoneNumber());

        String voiceMessage = String.format("Hello %s. Your verification code is %s. Please enter this code on the verification screen. This code is valid for %d minutes. Thank you.",
            event.getName(), event.getOtpCode(), OTP_VALIDITY.toMinutes());

        phoneCallService.initiateCall(
            event.getPhoneNumber(),
            voiceMessage
        ).subscribe(
            v -> log.info("Voice call verification OTP successfully initiated for {}", event.getPhoneNumber()),
            e -> log.error("Failed to initiate voice call verification OTP for {}: {}", event.getPhoneNumber(), e.getMessage(), e)
        );
    }
}