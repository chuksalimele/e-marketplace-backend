package com.aliwudi.marketplace.backend.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event for notifying a password reset request.
 * This event would be published by the user-service when a user requests a password reset.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequestedEvent {
    private String userId; // Internal ID of the user in your database
    private String email;
    private String username;
    private String resetLink; // The full URL with the reset token
}