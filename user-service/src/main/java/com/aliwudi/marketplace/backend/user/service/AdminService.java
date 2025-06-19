package com.aliwudi.marketplace.backend.user.service;

import reactor.core.publisher.Mono;

/**
 * Interface for abstracting Authorization Server (AS) administrative operations.
 * This allows for loose coupling between UserService and specific AS implementations (e.g., Keycloak).
 */
public interface AdminService {

    /**
     * Updates a custom user attribute in the Authorization Server.
     *
     * @param asUserId The unique ID of the user in the Authorization Server (e.g., Keycloak's UUID).
     * @param attributeName The name of the attribute to set (e.g., "app_internal_id").
     * @param attributeValue The value of the attribute (e.g., your local Long ID as a String).
     * @return Mono<Void> indicating completion.
     */
    Mono<Void> updateUserAttribute(String asUserId, String attributeName, String attributeValue);

    /**
     * Deletes a user from the Authorization Server.
     * This is used for compensating transactions (rollbacks) when profile creation fails.
     *
     * @param asUserId The unique ID of the user in the Authorization Server to delete.
     * @return Mono<Void> indicating completion.
     */
    Mono<Void> deleteUser(String asUserId);
}