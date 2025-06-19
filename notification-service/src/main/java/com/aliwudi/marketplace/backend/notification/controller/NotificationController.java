package com.aliwudi.marketplace.backend.notification.controller;

import com.aliwudi.marketplace.backend.common.model.Notification;
import com.aliwudi.marketplace.backend.notification.dto.NotificationRequest;
import com.aliwudi.marketplace.backend.notification.service.NotificationService;
import com.aliwudi.marketplace.backend.common.exception.NotificationNotFoundException;
import com.aliwudi.marketplace.backend.common.enumeration.NotificationType;
import com.aliwudi.marketplace.backend.common.status.NotificationStatus;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.util.AuthUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // For MediaType.TEXT_EVENT_STREAM
import org.springframework.security.access.prepost.PreAuthorize; // For role-based authorization
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Static import for API path constants and roles
import static com.aliwudi.marketplace.backend.common.constants.ApiPaths.*;

@RestController
@RequestMapping(NOTIFICATION_CONTROLLER_BASE) // MODIFIED
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthUtil authUtil;

    /**
     * Endpoint for creating a new notification.
     * This endpoint would typically be called by other internal microservices
     * (e.g., order-processing-service after an order status change)
     * or by an admin client.
     *
     * @param request The DTO containing notification data.
     * @return A Mono emitting the created Notification.
     * @throws IllegalArgumentException if input validation fails.
     */
    @PostMapping(NOTIFICATION_CREATE) // MODIFIED
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SERVICE + "')") // MODIFIED
    public Mono<Notification> createNotification(@Valid @RequestBody NotificationRequest request) {
        // Basic controller-level validation for critical fields
        if (request.getUserId() == null || request.getUserId() <= 0 ||
            request.getTitle() == null || request.getTitle().isBlank() ||
            request.getMessage() == null || request.getMessage().isBlank() ||
            request.getType() == null) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_NOTIFICATION_CREATION_REQUEST);
        }
        return notificationService.createNotification(request);
        // Exceptions (IllegalArgumentException) are handled by GlobalExceptionHandler.
    }

    /**
     * SSE endpoint to stream real-time notifications for the authenticated user.
     * Clients will subscribe to this endpoint to receive immediate updates.
     *
     * @param exchange
     * @return A Flux emitting Notification objects as server-sent events.
     * @throws IllegalArgumentException if authenticated user ID cannot be determined.
     */
    @GetMapping(value = NOTIFICATION_STREAM, produces = MediaType.TEXT_EVENT_STREAM_VALUE) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_USER + "') or hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Flux<Notification> getRealTimeNotifications(ServerWebExchange exchange) {
        return authUtil.getAuthenticatedUserId(exchange)
                .flatMapMany(notificationService::getRealTimeNotificationsStream)
                .switchIfEmpty(Flux.error(new IllegalArgumentException(ApiResponseMessages.UNAUTHENTICATED_USER)));
        // Error handling for authentication failure handled by GlobalExceptionHandler.
    }


    /**
     * Endpoint to retrieve all notifications for the authenticated user with pagination.
     *
     * @param exchange
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting Notification entities.
     * @throws IllegalArgumentException if pagination parameters are invalid or user not authenticated.
     */
    @GetMapping(NOTIFICATION_ME_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_USER + "') or hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Flux<Notification> getAllMyNotifications(
            ServerWebExchange exchange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return authUtil.getAuthenticatedUserId(exchange)
                .flatMapMany(userId -> notificationService.getAllNotificationsForUser(userId, pageable))
                .switchIfEmpty(Flux.error(new IllegalArgumentException(ApiResponseMessages.UNAUTHENTICATED_USER)));
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve notifications for the authenticated user by status with pagination.
     *
     * @param exchange
     * @param status The notification status (e.g., "READ", "UNREAD").
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting Notification entities.
     * @throws IllegalArgumentException if status or pagination parameters are invalid or user not authenticated.
     */
    @GetMapping(NOTIFICATION_ME_BY_STATUS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_USER + "') or hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Flux<Notification> getMyNotificationsByStatus(
            ServerWebExchange exchange,
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        if (status == null || status.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        NotificationStatus notificationStatus;
        try {
            notificationStatus = NotificationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_NOTIFICATION_STATUS + status);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return authUtil.getAuthenticatedUserId(exchange)
                .flatMapMany(userId -> notificationService.getNotificationsForUserByStatus(userId, notificationStatus, pageable))
                .switchIfEmpty(Flux.error(new IllegalArgumentException(ApiResponseMessages.UNAUTHENTICATED_USER)));
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve notifications for the authenticated user by type with pagination.
     *
     * @param exchange
     * @param type The notification type (e.g., "ORDER_UPDATE", "PROMOTION").
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting Notification entities.
     * @throws IllegalArgumentException if type or pagination parameters are invalid or user not authenticated.
     */
    @GetMapping(NOTIFICATION_ME_BY_TYPE) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_USER + "') or hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Flux<Notification> getMyNotificationsByType(
            ServerWebExchange exchange,
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        if (type == null || type.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        NotificationType notificationType;
        try {
            notificationType = NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_NOTIFICATION_TYPE + type);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return authUtil.getAuthenticatedUserId(exchange)
                .flatMapMany(userId -> notificationService.getNotificationsForUserByType(userId, notificationType, pageable))
                .switchIfEmpty(Flux.error(new IllegalArgumentException(ApiResponseMessages.UNAUTHENTICATED_USER)));
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to mark a specific notification as 'READ' for the authenticated user.
     *
     * @param id The ID of the notification to mark as read.
     * @return A Mono emitting the updated Notification.
     * @throws IllegalArgumentException if notification ID is invalid or user not authenticated.
     * @throws NotificationNotFoundException if the notification is not found or doesn't belong to the user.
     */
    @PutMapping(NOTIFICATION_ME_MARK_READ) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_USER + "') or hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Mono<Notification> markNotificationAsRead(
            ServerWebExchange exchange, 
            @PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_NOTIFICATION_ID);
        }
        return authUtil.getAuthenticatedUserId(exchange)
                .flatMap(userId -> notificationService.markNotificationAsRead(id, userId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException(ApiResponseMessages.UNAUTHENTICATED_USER)));
        // Exceptions (NotificationNotFoundException, IllegalArgumentException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a specific notification for the authenticated user.
     *
     * @param exchange
     * @param id The ID of the notification to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if notification ID is invalid or user not authenticated.
     * @throws NotificationNotFoundException if the notification is not found or doesn't belong to the user.
     */
    @DeleteMapping(NOTIFICATION_ME_DELETE) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    @PreAuthorize("hasRole('" + ROLE_USER + "') or hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Mono<Void> deleteMyNotification(
            ServerWebExchange exchange,
            @PathVariable Long id) {
        
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_NOTIFICATION_ID);
        }
        return authUtil.getAuthenticatedUserId(exchange)
                .flatMap(userId -> notificationService.deleteNotification(id, userId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException(ApiResponseMessages.UNAUTHENTICATED_USER)));
        // Exceptions (NotificationNotFoundException, IllegalArgumentException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all notifications for the authenticated user.
     *
     * @param exchange
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if user not authenticated.
     */
    @GetMapping(NOTIFICATION_ME_COUNT_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_USER + "') or hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Mono<Long> countMyNotifications(ServerWebExchange exchange) {
        return authUtil.getAuthenticatedUserId(exchange)
                .flatMap(notificationService::countNotificationsForUser)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(ApiResponseMessages.UNAUTHENTICATED_USER)));
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count notifications for the authenticated user by status.
     *
     * @param exchange
     * @param status The notification status (e.g., "READ", "UNREAD").
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if status is invalid or user not authenticated.
     */
    @GetMapping(NOTIFICATION_ME_COUNT_BY_STATUS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_USER + "') or hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Mono<Long> countMyNotificationsByStatus(
            ServerWebExchange exchange,
            @PathVariable String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_NOTIFICATION_STATUS);
        }
        NotificationStatus notificationStatus;
        try {
            notificationStatus = NotificationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_NOTIFICATION_STATUS + status);
        }

        return authUtil.getAuthenticatedUserId(exchange)
                .flatMap(userId -> notificationService.countNotificationsForUserByStatus(userId, notificationStatus))
                .switchIfEmpty(Mono.error(new IllegalArgumentException(ApiResponseMessages.UNAUTHENTICATED_USER)));
        // Errors are handled by GlobalExceptionHandler.
    }
}