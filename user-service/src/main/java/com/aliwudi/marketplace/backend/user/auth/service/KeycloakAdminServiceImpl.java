package com.aliwudi.marketplace.backend.user.auth.service;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// Using jakarta.ws.rs instead of javax.ws.rs
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.NotFoundException; // jakarta.ws.rs.NotFoundException

import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException;
import java.util.Set;

/**
 * Implementation of IAdminService for Keycloak Authorization Server.
 * Handles interactions with Keycloak Admin API.
 * Now includes enhanced methods for user creation and password setting,
 * supporting the "backend-first" hybrid registration flow, with generic naming.
 */
@Service
@Slf4j
public class KeycloakAdminServiceImpl implements IAdminService { // Implements the generic interface

    @Value("${auth-server.admin.url}") // Using new generic config property
    private String authServerAdminUrl;
    @Value("${auth-server.admin.realm}") // Using new generic config property
    private String authServerAdminRealm;
    @Value("${auth-server.admin.client-id}") // Using new generic config property
    private String authServerAdminClientId;
    @Value("${auth-server.admin.client-secret}") // Using new generic config property
    private String authServerAdminClientSecret;

    @Value("${auth-server.realm}") // Existing realm for user data
    private String userAuthRealm; // Renamed for generic context

    private final Map<String, Keycloak> authServerClientCache = new ConcurrentHashMap<>(); // Generic name

    // Helper method to get an Authorization Server client instance, using the cache.
    // This client uses the 'client_credentials' grant type for service account authentication.
    private Keycloak getAuthServerClient() { // Generic method name
        return authServerClientCache.computeIfAbsent(authServerAdminRealm, realm -> {
            log.info("Initializing Authorization Server Admin Client for realm: {}", realm);
            return Keycloak.getInstance(
                    authServerAdminUrl,
                    authServerAdminRealm, // The realm where the admin client (e.g., admin-cli) exists
                    authServerAdminClientId,
                    authServerAdminClientSecret);
        });
    }

    /**
     * Creates a user in the Authorization Server (Keycloak) and sets their password.
     * This method is part of the "backend-first" hybrid registration.
     *
     * @param username The username for the Authorization Server user.
     * @param email The email for the Authorization Server user.
     * @param password The plain-text password for the Authorization Server user.
     * @param internalUserId The internal ID from the backend database to store as a custom attribute.
     * @param firstName The first name of the user.
     * @param lastName The last name of the user.
     * @return A Mono emitting the Authorization Server's 'authId' (UUID) of the newly created user.
     * @throws DuplicateResourceException if user with the given username/email already exists in Authorization Server.
     * @throws RuntimeException if user creation or password setting fails for other reasons.
     */
    @Override
    public Mono<String> createUserInAuthServer(String username, 
            String email, 
            String password,
            Long internalUserId,
            String firstName, 
            String lastName,
            Set<String> roles) { // Generic method name
        
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getAuthServerClient(); // Generic method call
            UserRepresentation user = new UserRepresentation();
            user.setEnabled(true);
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);

            // Set the user_id as a custom attribute
            Map<String, List<String>> attributes = new HashMap<>();
            attributes.put("user_id", Collections.singletonList(String.valueOf(internalUserId)));
            user.setAttributes(attributes);

            log.info("Attempting to create user '{}' in Authorization Server realm '{}' with user_id: {}", username, userAuthRealm, internalUserId); // Generic log

            try (Response response = keycloak.realm(userAuthRealm).users().create(user)) { // Use userAuthRealm
                switch (response.getStatus()) {
                    case 201 -> {
                        // 201 Created
                        String authServerUserId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1"); // Generic variable name
                        log.info("Successfully created user '{}' in Authorization Server. Auth Server ID: {}", username, authServerUserId); // Generic log
                        
                        // Set password
                        UserResource userResource = keycloak.realm(userAuthRealm).users().get(authServerUserId); // Use userAuthRealm, generic variable
                        CredentialRepresentation passwordCred = new CredentialRepresentation();
                        passwordCred.setTemporary(false);
                        passwordCred.setType(CredentialRepresentation.PASSWORD);
                        passwordCred.setValue(password);
                        
                        userResource.resetPassword(passwordCred);
                        log.info("Password set for Authorization Server user '{}' (Auth Server ID: {})", username, authServerUserId); // Generic log
                        
                        // Optionally assign default roles here if needed, e.g.:
                        // RoleRepresentation defaultRole = keycloak.realm(userAuthRealm).roles().get("default-user-role").toRepresentation();
                        // userResource.roles().realmLevel().add(Collections.singletonList(defaultRole));
                        
                        return authServerUserId;
                    }
                    case 409 -> { // Conflict
                        String errorBody = response.readEntity(String.class);
                        log.warn("User '{}' already exists in Authorization Server (Status 409). Response: {}", username, errorBody); // Corrected log format
                        throw new DuplicateResourceException(ApiResponseMessages.USERNAME_ALREADY_EXISTS_IN_AUTHORIZATION_SERVER); // This message is still Keycloak specific, might need a generic one
                    }
                    default -> {
                        String errorBody = response.readEntity(String.class);
                        log.error("Failed to create user '{}' in Authorization Server. Status: {}, Response: {}", username, response.getStatus(), errorBody); // Corrected log format
                        throw new RuntimeException(String.format("Authorization Server user creation failed: Status %d, Response: %s", response.getStatus(), errorBody)); // Generic message
                    }
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Retrieves a user from the Authorization Server (Keycloak) by their Authorization Server ID.
     *
     * @param authServerUserId The Authorization Server ID of the user.
     * @return A Mono emitting the UserRepresentation, or empty if not found.
     */
    @Override
    public Mono<UserRepresentation> getUserFromAuthServerById(String authServerUserId) { // Generic method name
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getAuthServerClient(); // Generic method call
            try {
                UserRepresentation user = keycloak.realm(userAuthRealm).users().get(authServerUserId).toRepresentation(); // Use userAuthRealm, generic variable
                log.debug("Found Authorization Server user '{}' in realm '{}'", authServerUserId, userAuthRealm); // Corrected log format
                return user;
            } catch (NotFoundException e) { // Catching jakarta.ws.rs.NotFoundException
                log.warn("Authorization Server user '{}' not found in realm '{}'.", authServerUserId, userAuthRealm); // Corrected log format
                return null;
            } catch (Exception e) {
                log.error("Failed to get Authorization Server user '{}' from realm '{}': {}", authServerUserId, userAuthRealm, e.getMessage(), e); // Corrected log format with Throwable
                throw e;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates an attribute for a user in the Authorization Server (Keycloak).
     *
     * @param authServerUserId The Authorization Server ID of the user.
     * @param attributeName The name of the attribute to update.
     * @param attributeValue The new value of the attribute.
     * @return A Mono<Void> indicating completion.
     */
    @Override
    public Mono<Void> updateUserAttributeInAuthServer(String authServerUserId, String attributeName, String attributeValue) { // Generic method name
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getAuthServerClient(); // Generic method call
            try {
                UserResource userResource = keycloak.realm(userAuthRealm).users().get(authServerUserId); // Use userAuthRealm, generic variable
                UserRepresentation user = userResource.toRepresentation();

                Map<String, List<String>> attributes = user.getAttributes();
                if (attributes == null) {
                    attributes = new HashMap<>();
                }
                attributes.put(attributeName, Collections.singletonList(attributeValue));
                user.setAttributes(attributes);
                userResource.update(user);
                log.debug("Updated Authorization Server user '{}' in realm '{}' with attribute '{}' = '{}'", authServerUserId, userAuthRealm, attributeName, attributeValue); // Corrected log format
                return (Void) null;
            } catch (Exception e) {
                log.error("Failed to update user '{}' attribute '{}' in Authorization Server realm '{}': {}", authServerUserId, attributeName, userAuthRealm, e.getMessage(), e); // Corrected log format with Throwable
                throw e;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a user from the Authorization Server (Keycloak).
     *
     * @param authServerUserId The Authorization Server ID of the user to delete.
     * @return A Mono<Void> indicating completion.
     */
    @Override
    public Mono<Void> deleteUserFromAuthServer(String authServerUserId) { // Generic method name
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getAuthServerClient(); // Generic method call
            try {
                keycloak.realm(userAuthRealm).users().delete(authServerUserId); // Use userAuthRealm, generic variable
                log.info("Successfully deleted user '{}' from Authorization Server realm '{}'.", authServerUserId, userAuthRealm); // Corrected log format
                return (Void) null;
            } catch (NotFoundException e) { // Catching jakarta.ws.rs.NotFoundException
                log.warn("Authorization Server user '{}' not found in realm '{}' during deletion attempt. It might have been deleted already.", authServerUserId, userAuthRealm); // Corrected log format
                return (Void) null;
            } catch (Exception e) {
                log.error("Failed to delete user '{}' from Authorization Server realm '{}': {}", authServerUserId, userAuthRealm, e.getMessage(), e); // Corrected log format with Throwable
                throw e;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}