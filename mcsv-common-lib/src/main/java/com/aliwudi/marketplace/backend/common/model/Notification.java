/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.model;


import com.aliwudi.marketplace.backend.common.enumeration.NotificationType;
import com.aliwudi.marketplace.backend.common.status.NotificationStatus;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Represents a notification sent to a user.
 * Stored in the 'notifications' table.
 */
@Data // Generates getters, setters, equals, hashCode, and toString
@Builder // Provides a builder pattern for object creation
@Table("notifications") // Maps this entity to the 'notifications' table in the database
public class Notification {

    @Id // Marks 'id' as the primary key
    private Long id;

    private Long userId; // The ID of the user who receives the notification

    private String title; // A short title for the notification
    private String message; // The main content of the notification

    private NotificationType type; // Categorizes the notification (e.g., ORDER_UPDATE, PROMOTION)

    @Builder.Default // Sets a default value if not explicitly provided during building
    private NotificationStatus status = NotificationStatus.UNREAD; // Default status is UNREAD

    private LocalDateTime createdAt; // Timestamp when the notification was created
    private LocalDateTime readAt; // Timestamp when the notification was marked as read (nullable)

    private String targetEntityId; // Optional: ID of the related entity (e.g., orderId, productId)
    private String targetEntityType; // Optional: Type of the related entity (e.g., "order", "product")
}

