package com.aliwudi.marketplace.backend.user.auth.service;

import com.aliwudi.marketplace.backend.common.model.User;
import java.util.Map;
import java.util.Set;
import org.keycloak.representations.idm.UserRepresentation;
import reactor.core.publisher.Mono;

/**
 * Interface for administrative operations with an Authorization Server (e.g., Keycloak).
 * This service is responsible for direct interactions with the Authorization Server's Admin API,
 * such as creating users, setting passwords, and managing user attributes.
 */
public interface IAdminService {

    /**
     * Creates a user in the Authorization Server and sets their password.
     * This method is part of the "backend-first" hybrid registration.
     *
     * @param user the user model
     * @return A Mono emitting the Authorization Server's 'authId' (UUID) of the newly created user.
     */
    Mono<String> createUserInAuthServer(User user);

    /**
     * Retrieves a user from the Authorization Server by their Authorization Server ID.
     *
     * @param authServerUserId The Authorization Server ID of the user.
     * @return A Mono emitting the UserRepresentation, or empty if not found.
     */
    Mono<UserRepresentation> getUserFromAuthServerById(String authServerUserId);

    /**
     * Updates an attribute for a user in the Authorization Server.
     *
     * @param authServerUserId The Authorization Server ID of the user.
     * @param attributeName The name of the attribute to update.
     * @param attributeValue The new value of the attribute.
     * @return A Mono<Void> indicating completion.
     */
    Mono<Void> updateUserAttribute(String authServerUserId, String attributeName, String attributeValue);

    /**
     * Deletes a user from the Authorization Server.
     *
     * @param authServerUserId The Authorization Server ID of the user to delete.
     * @return A Mono<Void> indicating completion.
     */
    Mono<Void> deleteUserFromAuthServer(String authServerUserId);
    
    
    /**
     * Update email verification status
     * 
     * @param authServerUserId
     * @param isVerified
     * @return 
     */    
    Mono<Boolean> updateEmailVerifiedStatus(String authServerUserId, boolean isVerified); // New method to be used by OTP service

    Mono<Boolean> updatePhoneVerifiedStatus(String authServerUserId, boolean verified);
    
    Mono<UserRepresentation> getUserFromAuthServerByEmail(String email);
    
}
