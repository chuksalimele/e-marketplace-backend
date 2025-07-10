package com.aliwudi.marketplace.backend.user.auth.service;

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
     * @param username The username for the Authorization Server user.
     * @param email The email for the Authorization Server user.
     * @param password The plain-text password for the Authorization Server user.
     * @param internalUserId The internal ID from the backend database to store as a custom attribute.
     * @param firstName The first name of the user.
     * @param lastName The last name of the user.
     * @param roles The roles of the user
     * @return A Mono emitting the Authorization Server's 'authId' (UUID) of the newly created user.
     */
    Mono<String> createUserInAuthServer(String username, String email, String password, Long internalUserId, String firstName, String lastName, Set<String> roles);

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
    Mono<Void> updateUserAttributeInAuthServer(String authServerUserId, String attributeName, String attributeValue);

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

}
