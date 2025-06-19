package com.aliwudi.marketplace.backend.user.service;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct; // For @PostConstruct

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // For caching Keycloak admin client instance

/**
 * Service for interacting with Keycloak Admin API, specifically to update user attributes.
 * Uses a dedicated Keycloak Service Account for authentication.
 */
@Service
@Slf4j
public class KeycloakAdminService {

    @Value("${keycloak.admin-auth-server-url}")
    private String authServerUrl;
    @Value("${keycloak.admin-realm}")
    private String adminRealm; // This is the realm where the user exists and the client is defined
    @Value("${keycloak.admin-client-id}")
    private String clientId;
    @Value("${keycloak.admin-client-secret}")
    private String clientSecret;

    // Use a map to cache Keycloak client instances per realm if managing multiple realms
    // For single realm, a single instance is fine.
    private final Map<String, Keycloak> keycloakClients = new ConcurrentHashMap<>();

    // We don't need @PostConstruct for Keycloak.getInstance as it handles its own token refresh.
    // However, we can add a method to ensure it's initialized on first use if not using @PostConstruct.

    private Keycloak getKeycloakClient() {
        return keycloakClients.computeIfAbsent(adminRealm, realm -> {
            log.info("Initializing Keycloak Admin Client for realm: {}", realm);
            return Keycloak.getInstance(
                    authServerUrl,
                    adminRealm, // Realm where the service account client resides and users are managed
                    clientId,
                    clientSecret);
        });
    }

    /**
     * Updates a custom user attribute in Keycloak for a given Keycloak User ID.
     * This method is called by UserService after a user profile is created/updated
     * and its internal Long ID is generated in the user-service database.
     *
     * @param keycloakUserId The UUID of the user in Keycloak (the 'sub' claim).
     * @param attributeName The name of the attribute to set (e.g., "app_internal_id").
     * @param attributeValue The value of the attribute (e.g., your local Long ID as a String).
     * @return Mono<Void> indicating completion.
     */
    public Mono<Void> updateUserAttribute(String keycloakUserId, String attributeName, String attributeValue) {
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getKeycloakClient(); // Get the initialized Keycloak admin client
            try {
                UserResource userResource = keycloak.realm(adminRealm).users().get(keycloakUserId);
                UserRepresentation user = userResource.toRepresentation();

                Map<String, List<String>> attributes = user.getAttributes();
                if (attributes == null) {
                    attributes = new HashMap<>();
                }
                // Store the attribute as a list of strings
                attributes.put(attributeName, Collections.singletonList(attributeValue));
                user.setAttributes(attributes);
                userResource.update(user);
                log.info("Updated Keycloak user '{}' with attribute '{}' = '{}'", keycloakUserId, attributeName, attributeValue);
                return (Void) null;
            } catch (Exception e) {
                log.error("Failed to update user '%s' attribute '%s' in Keycloak: %s", keycloakUserId, attributeName, e.getMessage());
                throw e; // Re-throw to propagate error in reactive chain
            }
        }).subscribeOn(Schedulers.boundedElastic()); // Offload blocking Keycloak Admin Client call
    }
}