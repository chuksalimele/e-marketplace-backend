package com.aliwudi.marketplace.backend.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event for notifying successful user registration (for onboarding email).
 * This event would be published by the user-service after successful registration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {
    private String primaryIdentifierType;
    private String userId; // Internal ID of the user in your database
    private String primaryIdenter; //email or phone number
    private String name;
    private String loginUrl; // URL for the user to log in to the application
}
