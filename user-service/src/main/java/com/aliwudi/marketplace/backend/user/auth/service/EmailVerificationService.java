package com.aliwudi.marketplace.backend.user.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.time.Duration;

import com.aliwudi.marketplace.backend.common.exception.EmailSendingException;
import com.aliwudi.marketplace.backend.common.exception.OtpValidationException; // Import new exception
import com.aliwudi.marketplace.backend.common.exception.UserNotFoundException; // Import new exception
import com.aliwudi.marketplace.backend.common.exception.ServiceException; // Import general exception

/**
 * Service for managing email verification using One-Time Passwords (OTPs).
 * Handles OTP generation, storage in Redis, sending via SMTP, and validation.
 */
@Service
@Slf4j
public class EmailVerificationService {

    private final KeycloakAdminServiceImpl keycloakAdminService;
    private final EmailSenderService emailSenderService;
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate; // For Redis operations

    // OTP validity period (e.g., 5 minutes)
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(5);
    private static final int OTP_LENGTH = 6; // e.g., 6-digit code
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String OTP_PREFIX = "email_otp:"; // Key prefix for Redis

    @Autowired
    public EmailVerificationService(
            KeycloakAdminServiceImpl keycloakAdminService,
            EmailSenderService emailSenderService,
            ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        this.keycloakAdminService = keycloakAdminService;
        this.emailSenderService = emailSenderService;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    /**
     * Initiates the email verification process by generating and sending an OTP.
     * The OTP is stored in Redis with an expiration.
     *
     * @param authServerUserId The Keycloak user ID for whom to send the OTP.
     * @param email The user's email address.
     * @return Mono<Void> indicating completion of sending the OTP.
     * @throws EmailSendingException if the email fails to send.
     * @throws ServiceException for other internal errors.
     */
    public Mono<Void> initiateEmailVerification(String authServerUserId, String email) {
        String otpCode = generateOtp();
        String redisKey = OTP_PREFIX + authServerUserId;

        // Store OTP in Redis with a TTL
        return reactiveRedisTemplate.opsForValue().set(redisKey, otpCode, OTP_VALIDITY)
                .then(Mono.fromCallable(() -> {
                    String subject = "Your Email Verification Code";
                    String body = String.format("Hi there,\n\nYour verification code is: %s\n\nThis code is valid for %d minutes. Please enter this code in your application to verify your email.\n\nThank you,\nYour App Team", otpCode, OTP_VALIDITY.toMinutes());

                    log.info("Generated OTP for user {}: {}. Stored in Redis.", authServerUserId, otpCode);
                    return emailSenderService.sendEmail(email, subject, body);
                }).flatMap(m -> m) // Flatten the Mono<Mono<Void>>
                .onErrorResume(EmailSendingException.class, e -> {
                    log.error("Failed to send OTP email to {}: {}", email, e.getMessage(), e);
                    // Optionally remove OTP from Redis if email sending fails consistently
                    // reactiveRedisTemplate.delete(redisKey).subscribe();
                    return Mono.error(new EmailSendingException("Failed to send verification email.", e));
                })
                .onErrorResume(e -> {
                    log.error("Unexpected error during OTP initiation for user {}: {}", authServerUserId, e.getMessage(), e);
                    return Mono.error(new ServiceException("Failed to initiate email verification.", e));
                }));
    }

    /**
     * Validates the provided OTP against the one stored in Redis.
     * If valid, marks the user's email as verified in Keycloak and removes the OTP from Redis.
     *
     * @param authServerUserId The Keycloak user ID.
     * @param providedOtp The OTP code provided by the user.
     * @return Mono<Boolean> true if verification successful, false otherwise.
     * @throws OtpValidationException if the OTP is invalid or expired.
     * @throws UserNotFoundException if the user is not found in Keycloak.
     * @throws ServiceException for other internal errors.
     */
    public Mono<Boolean> verifyEmailOtp(String authServerUserId, String providedOtp) {
        String redisKey = OTP_PREFIX + authServerUserId;

        return reactiveRedisTemplate.opsForValue().get(redisKey)
            .flatMap(storedOtp -> {
                if (storedOtp == null) {
                    log.warn("Attempt to verify email for user {} but no OTP found in Redis (or expired/already used).", authServerUserId);
                    return Mono.error(new OtpValidationException("Invalid or expired verification code."));
                }
                if (!storedOtp.equals(providedOtp)) {
                    log.warn("Invalid OTP provided for user {}.", authServerUserId);
                    return Mono.error(new OtpValidationException("Invalid verification code."));
                }

                // OTP is valid. Proceed to mark email as verified in Keycloak.
                log.info("OTP for user {} is valid. Marking email as verified in Keycloak and deleting OTP from Redis.", authServerUserId);
                return keycloakAdminService.updateEmailVerifiedStatus(authServerUserId, true)
                    .then(reactiveRedisTemplate.delete(redisKey)) // Delete OTP from Redis after successful verification
                    .thenReturn(true); // Indicate success
            })
            .switchIfEmpty(Mono.error(new OtpValidationException("Invalid or expired verification code."))) // Case where reactiveRedisTemplate.opsForValue().get returns empty
            .onErrorResume(UserNotFoundException.class, Mono::error) // Re-throw UserNotFoundException
            .onErrorResume(OtpValidationException.class, Mono::error) // Re-throw OtpValidationException
            .onErrorResume(e -> {
                log.error("Error during email OTP verification for user {}: {}", authServerUserId, e.getMessage(), e);
                return Mono.error(new ServiceException("Failed to verify email.", e));
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Generates a random numeric OTP.
     * @return The generated OTP string.
     */
    private String generateOtp() {
        int max = (int) Math.pow(10, OTP_LENGTH) - 1;
        int min = (int) Math.pow(10, OTP_LENGTH - 1);
        int otp = secureRandom.nextInt(max - min + 1) + min;
        return String.format("%0" + OTP_LENGTH + "d", otp); // Pad with leading zeros if necessary
    }
}