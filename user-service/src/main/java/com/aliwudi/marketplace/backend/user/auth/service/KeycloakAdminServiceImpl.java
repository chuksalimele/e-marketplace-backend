package com.aliwudi.marketplace.backend.user.auth.service; // Package remains the same

import com.aliwudi.marketplace.backend.user.service.AdminService;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service; // Keep Service annotation
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of AdminService for Keycloak Authorization Server.
 * Handles interactions with Keycloak Admin API.
 */
@Service // Keep @Service so it can be component scanned.
@Slf4j
public class KeycloakAdminServiceImpl implements AdminService { // Implement the new interface

    @Value("${keycloak.admin-auth-server-url}")
    private String authServerUrl;
    @Value("${keycloak.admin-realm}")
    private String adminRealm;
    @Value("${keycloak.admin-client-id}")
    private String clientId;
    @Value("${keycloak.admin-client-secret}")
    private String clientSecret;

    private final Map<String, Keycloak> keycloakClients = new ConcurrentHashMap<>();

    private Keycloak getKeycloakClient() {
        return keycloakClients.computeIfAbsent(adminRealm, realm -> {
            log.debug("Initializing Keycloak Admin Client for realm: {}", realm);
            return Keycloak.getInstance(
                    authServerUrl,
                    adminRealm,
                    clientId,
                    clientSecret);
        });
    }

    @Override // Implementing AdminService method
    public Mono<Void> updateUserAttribute(String asUserId, String attributeName, String attributeValue) {
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getKeycloakClient();
            try {
                UserResource userResource = keycloak.realm(adminRealm).users().get(asUserId);
                UserRepresentation user = userResource.toRepresentation();

                Map<String, List<String>> attributes = user.getAttributes();
                if (attributes == null) {
                    attributes = new HashMap<>();
                }
                attributes.put(attributeName, Collections.singletonList(attributeValue));
                user.setAttributes(attributes);
                userResource.update(user);
                log.debug("Updated Keycloak user '{}' with attribute '{}' = '{}'", asUserId, attributeName, attributeValue);
                return (Void) null;
            } catch (Exception e) {
                log.error("Failed to update user '%s' attribute '%s' in Keycloak: %s", asUserId, attributeName, e.getMessage());
                throw e; // Re-throw to propagate error in reactive chain
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override // Implementing AdminService method
    public Mono<Void> deleteUser(String asUserId) {
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getKeycloakClient();
            try {
                keycloak.realm(adminRealm).users().delete(asUserId);
                log.debug("Successfully deleted user '%s' from Keycloak.", asUserId);
                return (Void) null;
            } catch (Exception e) {
                log.error("Failed to delete user '%s' from Keycloak: %s", asUserId, e.getMessage());
                throw e; // Re-throw to propagate error in reactive chain
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}