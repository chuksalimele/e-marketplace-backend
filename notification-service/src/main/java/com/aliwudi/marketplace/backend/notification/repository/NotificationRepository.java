/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.notification.repository;

import com.aliwudi.marketplace.backend.common.model.Notification;
import com.aliwudi.marketplace.backend.common.enumeration.NotificationType;
import com.aliwudi.marketplace.backend.common.status.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive Repository for Notification entities.
 * Provides CRUD operations and custom queries for notifications.
 */
public interface NotificationRepository extends R2dbcRepository<Notification, Long> {

    /**
     * Finds all notifications for a specific user, with pagination.
     * Notifications are typically ordered by creation date descending (newest first).
     *
     * @param userId The ID of the user.
     * @param pageable Pagination information.
     * @return A Flux emitting Notification entities.
     */
    Flux<Notification> findByUserId(Long userId, Pageable pageable);
    
    Flux<Notification> findByUserAuthId(String userAuthId, Pageable pageable);

    /**
     * Finds all notifications for a specific user and a specific status, with pagination.
     *
     * @param userId The ID of the user.
     * @param status The read status of the notification (READ, UNREAD).
     * @param pageable Pagination information.
     * @return A Flux emitting Notification entities.
     */
    Flux<Notification> findByUserIdAndStatus(Long userId, NotificationStatus status, Pageable pageable);

    /**
     * Finds all notifications for a specific user and a specific type, with pagination.
     *
     * @param userId The ID of the user.
     * @param type The type of the notification (e.g., ORDER_UPDATE, PROMOTION).
     * @param pageable Pagination information.
     * @return A Flux emitting Notification entities.
     */
    Flux<Notification> findByUserIdAndType(Long userId, NotificationType type, Pageable pageable);

    /**
     * Counts all notifications for a specific user.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the count.
     */
    Mono<Long> countByUserId(Long userId);

    /**
     * Counts notifications for a specific user and status.
     *
     * @param userId The ID of the user.
     * @param status The read status of the notification.
     * @return A Mono emitting the count.
     */
    Mono<Long> countByUserIdAndStatus(Long userId, NotificationStatus status);

    /**
     * Checks if a notification with a given ID exists for a specific user.
     *
     * @param id The ID of the notification.
     * @param userId The ID of the user.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    Mono<Boolean> existsByIdAndUserId(Long id, Long userId);
}

