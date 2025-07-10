package com.aliwudi.marketplace.backend.user.service; // Adjust package as appropriate for your user-service

import static com.aliwudi.marketplace.backend.common.constants.ExchangeType.*;
import static com.aliwudi.marketplace.backend.common.constants.EventRoutingKey.*;
import com.aliwudi.marketplace.backend.common.dto.event.EmailVerificationRequestedEvent;
import com.aliwudi.marketplace.backend.common.dto.event.PasswordResetRequestedEvent;
import com.aliwudi.marketplace.backend.common.dto.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service responsible for publishing various notification-related events to RabbitMQ.
 * Other services (like the notification-service) will consume these events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventPublisherService {

    private final AmqpTemplate rabbitTemplate;

    /**
     * Publishes an event to request an email verification OTP after user registration.
     *
     * @param authServerUserId The ID from the authentication server (e.g., Keycloak ID).
     * @param email The user's email address.
     * @param username The user's username.
     * @return Mono<Void> indicating the event has been published.
     */
    public Mono<Void> publishEmailVerificationRequestedEvent(String authServerUserId, String email, String name, String otpCode) {
        EmailVerificationRequestedEvent event = new EmailVerificationRequestedEvent(authServerUserId, email, name, otpCode);
        log.info("Publishing EmailVerificationRequestedEvent for user: {} to exchange {} with routing key {}",
                 email, USER_EVENTS_EXCHANGE, EMAIL_VERIFICATION_ROUTING_KEY);

        return Mono.fromRunnable(() ->
            rabbitTemplate.convertAndSend(
                USER_EVENTS_EXCHANGE,
                EMAIL_VERIFICATION_ROUTING_KEY,
                event
            )
        ).doOnSuccess(v -> log.debug("EmailVerificationRequestedEvent for {} published.", email))
         .doOnError(e -> log.error("Failed to publish EmailVerificationRequestedEvent for {}: {}", email, e.getMessage(), e))
         .then()
         .subscribeOn(Schedulers.boundedElastic()); // Use a separate scheduler for blocking RabbitMQ send
    }

    /**
     * Publishes an event after successful user registration (for onboarding email).
     *
     * @param userId The internal user ID from your database.
     * @param email The user's email address.
     * @param username The user's username.
     * @param loginUrl The URL for the user to log in.
     * @return Mono<Void> indicating the event has been published.
     */
    public Mono<Void> publishUserRegisteredEvent(String userId, String email, String name, String loginUrl) {
        UserRegisteredEvent event = new UserRegisteredEvent(userId, email, name, loginUrl);
        log.info("Publishing UserRegisteredEvent for user: {} to exchange {} with routing key {}",
                 email, USER_EVENTS_EXCHANGE, USER_REGISTERED_ROUTING_KEY);

        return Mono.fromRunnable(() ->
            rabbitTemplate.convertAndSend(
                USER_EVENTS_EXCHANGE,
                USER_REGISTERED_ROUTING_KEY,
                event
            )
        ).doOnSuccess(v -> log.debug("UserRegisteredEvent for {} published.", email))
         .doOnError(e -> log.error("Failed to publish UserRegisteredEvent for {}: {}", email, e.getMessage(), e))
         .then()
         .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Publishes an event to request a password reset email.
     *
     * @param userId The internal user ID from your database.
     * @param email The user's email address.
     * @param username The user's username.
     * @param resetLink The full URL for password reset (with token).
     * @return Mono<Void> indicating the event has been published.
     */
    public Mono<Void> publishPasswordResetRequestedEvent(String userId, String email, String name, String resetLink) {
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent(userId, email, name, resetLink);
        log.info("Publishing PasswordResetRequestedEvent for user: {} to exchange {} with routing key {}",
                 email, USER_EVENTS_EXCHANGE, PASSWORD_RESET_ROUTING_KEY);

        return Mono.fromRunnable(() ->
            rabbitTemplate.convertAndSend(
                USER_EVENTS_EXCHANGE,
                PASSWORD_RESET_ROUTING_KEY,
                event
            )
        ).doOnSuccess(v -> log.debug("PasswordResetRequestedEvent for {} published.", email))
         .doOnError(e -> log.error("Failed to publish PasswordResetRequestedEvent for {}: {}", email, e.getMessage(), e))
         .then()
         .subscribeOn(Schedulers.boundedElastic());
    }
}