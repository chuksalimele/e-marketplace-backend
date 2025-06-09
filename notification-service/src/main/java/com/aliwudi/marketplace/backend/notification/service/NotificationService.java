package com.aliwudi.marketplace.backend.notification.service;

import com.aliwudi.marketplace.backend.common.model.Notification;
import com.aliwudi.marketplace.backend.notification.repository.NotificationRepository;
import com.aliwudi.marketplace.backend.notification.dto.NotificationRequest;
import com.aliwudi.marketplace.backend.common.exception.NotificationNotFoundException;
import com.aliwudi.marketplace.backend.common.enumeration.NotificationType;
import com.aliwudi.marketplace.backend.common.status.NotificationStatus;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.interservice.UserIntegrationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

// NEW: Spring Security Imports for Reactive Context
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails; // Common interface for principal

import java.time.LocalDateTime;

/**
 * Service class for managing Notifications.
 * Handles creation, retrieval, updates (marking as read), deletion,
 * and broadcasting of notifications for real-time delivery via SSE.
 * Now also orchestrates sending notifications via Email and SMS.
 */
@Service
@RequiredArgsConstructor // Generates constructor for final fields
@Slf4j // Enables Lombok's logging
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserIntegrationService usertegrationService; // To get user's email/phone for external notifications
    // private final EmailService emailService; // Uncomment and inject once implemented
    // private final SmsService smsService;     // Uncomment and inject once implemented

    // Sinks.Many is used as a hot publisher to broadcast notifications to multiple subscribers (SSE clients).
    private final Sinks.Many<Notification> notificationsSink = Sinks.many().multicast().onBackpressureBuffer();

    /**
     * Creates and stores a new notification, then broadcasts it for real-time delivery.
     * This operation is transactional.
     * Additionally, it determines if an email or SMS notification should be sent
     * based on the notification type and user preferences (if implemented).
     *
     * @param request The DTO containing data for the new notification.
     * @return A Mono emitting the created Notification.
     */
    @Transactional
    public Mono<Notification> createNotification(NotificationRequest request) {
        log.info("Attempting to create notification for userId: {}, type: {}", request.getUserId(), request.getType());

        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .title(request.getTitle())
                .message(request.getMessage())
                .type(request.getType())
                .createdAt(LocalDateTime.now())
                .status(NotificationStatus.UNREAD) // Default to UNREAD
                .targetEntityId(request.getTargetEntityId())
                .targetEntityType(request.getTargetEntityType())
                .build();

        return notificationRepository.save(notification)
                .doOnSuccess(savedNotification -> {
                    log.info("Notification created successfully with ID: {}", savedNotification.getId());

                    // Emit to SSE sink for real-time in-app updates
                    notificationsSink.emitNext(savedNotification, Sinks.EmitFailureHandler.FAIL_FAST);
                    log.debug("Notification ID {} emitted to SSE sink.", savedNotification.getId());

                    // Trigger external notifications (Email/SMS) asynchronously
                    // We fetch the user details (email, phone) to send these notifications.
                    usertegrationService.getUserById(savedNotification.getUserId())
                            .doOnNext(user -> {
                                switch (savedNotification.getType()) {
                                    case ORDER_UPDATE:
                                    case ACCOUNT_UPDATE:
                                        // Send email for important updates
                                        if (user.getEmail() != null && !user.getEmail().isBlank()) {
                                            sendEmailNotification(user.getEmail(), savedNotification.getTitle(), savedNotification.getMessage())
                                                    .subscribeOn(Schedulers.boundedElastic()) // Offload to a blocking-friendly scheduler
                                                    .subscribe(
                                                            success -> log.info("Email notification sent for user {}: {}", user.getId(), savedNotification.getTitle()),
                                                            error -> log.error("Failed to send email for user {}: {}", user.getId(), error.getMessage())
                                                    );
                                        }
                                        // Example: Send SMS for critical order updates (e.g., "delivered")
                                        // This requires a 'phone_number' field in your User model
                                        // if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) { // Assuming user has getPhoneNumber()
                                        //     sendSmsNotification(user.getPhoneNumber(), savedNotification.getMessage())
                                        //             .subscribeOn(Schedulers.boundedElastic())
                                        //             .subscribe(
                                        //                 success -> log.info("SMS notification sent for user {}: {}", user.getId(), savedNotification.getTitle()),
                                        //                 error -> log.error("Failed to send SMS for user {}: {}", user.getId(), error.getMessage())
                                        //             );
                                        // }
                                        break;
                                    case PROMOTION:
                                        // Example: Send email for promotions, perhaps not SMS
                                        if (user.getEmail() != null && !user.getEmail().isBlank()) {
                                            sendEmailNotification(user.getEmail(), savedNotification.getTitle(), savedNotification.getMessage())
                                                    .subscribeOn(Schedulers.boundedElastic())
                                                    .subscribe(
                                                            success -> log.info("Email notification sent for user {}: {}", user.getId(), savedNotification.getTitle()),
                                                            error -> log.error("Failed to send email for user {}: {}", user.getId(), error.getMessage())
                                                    );
                                        }
                                        break;
                                    // Add more cases for other notification types
                                    default:
                                        log.debug("No external notification channel configured for type: {}", savedNotification.getType());
                                        break;
                                }
                            })
                            .subscribe(
                                    user -> log.debug("User details fetched for external notifications: {}", user.getId()),
                                    error -> log.warn("Could not fetch user details for external notifications (ID: {}): {}", savedNotification.getUserId(), error.getMessage())
                            );
                })
                .doOnError(e -> log.error("Error creating notification for userId {}: {}", request.getUserId(), e.getMessage(), e));
    }

    /**
     * Placeholder method for sending an email notification.
     * In a real application, this would call your EmailService.
     * Offloaded to Schedulers.boundedElastic() to prevent blocking.
     */
    private Mono<Void> sendEmailNotification(String to, String subject, String body) {
        return Mono.fromRunnable(() -> {
            log.info("Simulating sending email to: {} with subject: {}", to, subject);
            // Implement actual call to your EmailService here
            // e.g., emailService.sendEmail(to, subject, body);
            try {
                // Simulate network delay or external API call
                Thread.sleep(500); // Simulate blocking I/O
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Email sending simulation interrupted: {}", e.getMessage());
            }
            log.info("Simulated email sent to: {}", to);
        }).then(); // Convert to Mono<Void>
    }

    /**
     * Placeholder method for sending an SMS notification.
     * In a real application, this would call your SmsService.
     * Offloaded to Schedulers.boundedElastic() to prevent blocking.
     */
    private Mono<Void> sendSmsNotification(String toPhoneNumber, String message) {
        return Mono.fromRunnable(() -> {
            log.info("Simulating sending SMS to: {} with message: {}", toPhoneNumber, message);
            // Implement actual call to your SmsService here
            // e.g., smsService.sendSms(toPhoneNumber, message);
            try {
                // Simulate network delay or external API call
                Thread.sleep(300); // Simulate blocking I/O
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("SMS sending simulation interrupted: {}", e.getMessage());
            }
            log.info("Simulated SMS sent to: {}", toPhoneNumber);
        }).then(); // Convert to Mono<Void>
    }

    // ... (rest of the NotificationService methods remain the same) ...

    /**
     * Marks a specific notification as 'READ'.
     * This operation is transactional.
     *
     * @param notificationId The ID of the notification to mark as read.
     * @param userId The ID of the user who owns the notification (for authorization).
     * @return A Mono emitting the updated Notification.
     * @throws NotificationNotFoundException if the notification is not found for the given user.
     */
    @Transactional
    public Mono<Notification> markNotificationAsRead(Long notificationId, Long userId) {
        log.info("Attempting to mark notification ID: {} for user ID: {} as READ", notificationId, userId);
        return notificationRepository.findById(notificationId)
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(ApiResponseMessages.NOTIFICATION_NOT_FOUND + notificationId)))
                .filter(notification -> notification.getUserId().equals(userId)) // Ensure user owns the notification
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(ApiResponseMessages.NOTIFICATION_NOT_FOUND_FOR_USER + notificationId + " and user " + userId)))
                .flatMap(notification -> {
                    if (notification.getStatus() == NotificationStatus.UNREAD) {
                        notification.setStatus(NotificationStatus.READ);
                        notification.setReadAt(LocalDateTime.now()); // Set read timestamp
                        return notificationRepository.save(notification)
                                .doOnSuccess(updatedNotification -> log.info("Notification ID: {} marked as READ.", updatedNotification.getId()))
                                .doOnError(e -> log.error("Error marking notification ID {} as read: {}", notificationId, e.getMessage(), e));
                    }
                    log.info("Notification ID: {} was already READ.", notificationId);
                    return Mono.just(notification); // Already read, return as is
                });
    }

    /**
     * Deletes a specific notification.
     * This operation is transactional.
     *
     * @param notificationId The ID of the notification to delete.
     * @param userId The ID of the user who owns the notification (for authorization).
     * @return A Mono<Void> indicating completion.
     * @throws NotificationNotFoundException if the notification is not found for the given user.
     */
    @Transactional
    public Mono<Void> deleteNotification(Long notificationId, Long userId) {
        log.info("Attempting to delete notification ID: {} for user ID: {}", notificationId, userId);
        return notificationRepository.findById(notificationId)
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(ApiResponseMessages.NOTIFICATION_NOT_FOUND + notificationId)))
                .filter(notification -> notification.getUserId().equals(userId)) // Ensure user owns the notification
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(ApiResponseMessages.NOTIFICATION_NOT_FOUND_FOR_USER + notificationId + " and user " + userId)))
                .flatMap(notificationRepository::delete)
                .doOnSuccess(v -> log.info("Notification ID: {} deleted successfully.", notificationId))
                .doOnError(e -> log.error("Error deleting notification ID {}: {}", notificationId, e.getMessage(), e));
    }

    /**
     * Retrieves a specific notification by its ID and associated user ID.
     *
     * @param notificationId The ID of the notification.
     * @param userId The ID of the user who owns the notification (for authorization).
     * @return A Mono emitting the Notification.
     * @throws NotificationNotFoundException if the notification is not found for the given user.
     */
    public Mono<Notification> getNotificationByIdAndUserId(Long notificationId, Long userId) {
        log.info("Retrieving notification ID: {} for user ID: {}", notificationId, userId);
        return notificationRepository.findById(notificationId)
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(ApiResponseMessages.NOTIFICATION_NOT_FOUND + notificationId)))
                .filter(notification -> notification.getUserId().equals(userId)) // Ensure user owns the notification
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(ApiResponseMessages.NOTIFICATION_NOT_FOUND_FOR_USER + notificationId + " and user " + userId)))
                .doOnSuccess(notification -> log.info("Notification ID: {} retrieved successfully.", notificationId))
                .doOnError(e -> log.error("Error retrieving notification ID {}: {}", notificationId, e.getMessage(), e));
    }

    /**
     * Retrieves all notifications for a specific user with pagination.
     * Sorted by creation date descending.
     *
     * @param userId The ID of the user.
     * @param pageable Pagination information.
     * @return A Flux emitting Notification entities.
     */
    public Flux<Notification> getAllNotificationsForUser(Long userId, Pageable pageable) {
        log.info("Retrieving all notifications for user ID: {} with pagination: {}", userId, pageable);
        return notificationRepository.findByUserId(userId, pageable)
                .doOnComplete(() -> log.info("Finished retrieving notifications for user ID: {}", userId))
                .doOnError(e -> log.error("Error retrieving all notifications for user ID {}: {}", userId, e.getMessage(), e));
    }

    /**
     * Retrieves notifications for a specific user filtered by status with pagination.
     *
     * @param userId The ID of the user.
     * @param status The read status (READ, UNREAD).
     * @param pageable Pagination information.
     * @return A Flux emitting Notification entities.
     */
    public Flux<Notification> getNotificationsForUserByStatus(Long userId, NotificationStatus status, Pageable pageable) {
        log.info("Retrieving notifications for user ID: {} with status: {} and pagination: {}", userId, status, pageable);
        return notificationRepository.findByUserIdAndStatus(userId, status, pageable)
                .doOnComplete(() -> log.info("Finished retrieving notifications for user ID: {} and status: {}", userId, status))
                .doOnError(e -> log.error("Error retrieving notifications for user ID {} by status {}: {}", userId, status, e.getMessage(), e));
    }

    /**
     * Retrieves notifications for a specific user filtered by type with pagination.
     *
     * @param userId The ID of the user.
     * @param type The notification type.
     * @param pageable Pagination information.
     * @return A Flux emitting Notification entities.
     */
    public Flux<Notification> getNotificationsForUserByType(Long userId, NotificationType type, Pageable pageable) {
        log.info("Retrieving notifications for user ID: {} with type: {} and pagination: {}", userId, type, pageable);
        return notificationRepository.findByUserIdAndType(userId, type, pageable)
                .doOnComplete(() -> log.info("Finished retrieving notifications for user ID: {} and type: {}", userId, type))
                .doOnError(e -> log.error("Error retrieving notifications for user ID {} by type {}: {}", userId, type, e.getMessage(), e));
    }

    /**
     * Counts all notifications for a specific user.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countNotificationsForUser(Long userId) {
        log.info("Counting all notifications for user ID: {}", userId);
        return notificationRepository.countByUserId(userId)
                .doOnSuccess(count -> log.info("Total notifications for user {}: {}", userId, count))
                .doOnError(e -> log.error("Error counting all notifications for user ID {}: {}", userId, e.getMessage(), e));
    }

    /**
     * Counts notifications for a specific user and status.
     *
     * @param userId The ID of the user.
     * @param status The read status.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countNotificationsForUserByStatus(Long userId, NotificationStatus status) {
        log.info("Counting notifications for user ID: {} with status: {}", userId, status);
        return notificationRepository.countByUserIdAndStatus(userId, status)
                .doOnSuccess(count -> log.info("Notifications for user {} with status {}: {}", userId, status, count))
                .doOnError(e -> log.error("Error counting notifications for user ID {} by status {}: {}", userId, status, e.getMessage(), e));
    }

    /**
     * Provides a Flux of new notifications for real-time delivery via SSE.
     * Clients can subscribe to this stream to receive updates as they happen.
     * Filters notifications to only include those for the subscribed userId.
     *
     * @param userId The ID of the user to stream notifications for.
     * @return A Flux emitting Notification objects.
     */
    public Flux<Notification> getRealTimeNotificationsStream(Long userId) {
        log.info("Subscribing to real-time notifications for user ID: {}", userId);
        return notificationsSink.asFlux()
                .filter(notification -> notification.getUserId().equals(userId)) // Filter for specific user
                .doOnSubscribe(subscription -> log.info("New SSE subscriber for user ID: {}", userId))
                .doOnCancel(() -> log.info("SSE subscription cancelled for user ID: {}", userId))
                .doOnError(e -> log.error("Error in SSE stream for user ID {}: {}", userId, e.getMessage(), e))
                .publishOn(Schedulers.boundedElastic()); // Ensure operations on elements are not on event loop
    }

    /**
     * Helper method to get the current authenticated user's ID from Spring Security context.
     * This is the real-world implementation assuming a Spring Security WebFlux setup.
     * The principal is typically a UserDetails object or a custom UserPrincipal containing the ID.
     *
     * @return A Mono emitting the current user's ID (Long).
     * @throws RuntimeException if the user is not authenticated or user ID cannot be determined.
     */
    public Mono<Long> getCurrentAuthenticatedUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .switchIfEmpty(Mono.error(new RuntimeException(ApiResponseMessages.SECURITY_CONTEXT_NOT_FOUND)))
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .switchIfEmpty(Mono.error(new RuntimeException(ApiResponseMessages.UNAUTHENTICATED_USER)))
                .map(Authentication::getPrincipal)
                .flatMap(principal -> {
                    // Try to cast to Long directly (if principal is just the ID)
                    if (principal instanceof Long) {
                        return Mono.just((Long) principal);
                    }
                    // Try to cast to UserDetails (common for Spring Security)
                    else if (principal instanceof UserDetails) {
                        try {
                            // Assuming username is the user ID, or there's an getId() method
                            String username = ((UserDetails) principal).getUsername(); // Often the ID string
                            return Mono.just(Long.parseLong(username)); // Attempt to parse
                        } catch (NumberFormatException e) {
                            log.error("Principal username is not a valid Long ID: {}", ((UserDetails) principal).getUsername(), e);
                            return Mono.error(new RuntimeException(ApiResponseMessages.INVALID_USER_ID_FORMAT));
                        }
                    }
                    // If you have a custom UserPrincipal class with a getId() method:
                    // else if (principal instanceof YourCustomUserPrincipal) {
                    //     return Mono.just(((YourCustomUserPrincipal) principal).getId());
                    // }
                    else {
                        log.error("Unsupported principal type in security context: {}", principal.getClass().getName());
                        return Mono.error(new RuntimeException(ApiResponseMessages.INVALID_USER_ID_FORMAT));
                    }
                })
                .doOnSuccess(id -> log.debug("Retrieved authenticated user ID: {}", id))
                .doOnError(e -> log.error("Failed to retrieve authenticated user ID: {}", e.getMessage()));
    }
}
