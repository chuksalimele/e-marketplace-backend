// src/main/java/com/aliwudi/marketplace/backend/user/service/KeycloakAdminService.java
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

@Service
@Slf4j
public class KeycloakAdminService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;
    @Value("${keycloak.realm}")
    private String realm;
    @Value("${keycloak.admin-cli-client-id}")
    private String adminClientId;
    @Value("${keycloak.admin-username}")
    private String adminUsername;
    @Value("${keycloak.admin-password}")
    private String adminPassword;

    private Keycloak keycloak;

    @PostConstruct
    public void init() {
        // Initialize Keycloak admin client
        keycloak = Keycloak.getInstance(
                authServerUrl,
                "master", // Admin realm is usually 'master'
                adminUsername,
                adminPassword,
                adminClientId);
        log.info("Keycloak Admin Client initialized for realm: {}", realm);
    }

    /**
     * Updates a user attribute in Keycloak for a given Keycloak User ID.
     * This method should be called after a user profile is created in your local DB
     * and its internal Long ID is generated.
     *
     * @param keycloakUserId The UUID of the user in Keycloak (the 'sub' claim).
     * @param attributeName The name of the attribute to set (e.g., "app_internal_id").
     * @param attributeValue The value of the attribute (e.g., your local Long ID as a String).
     * @return Mono<Void> indicating completion.
     */
    public Mono<Void> updateUserAttribute(String keycloakUserId, String attributeName, String attributeValue) {
        return Mono.fromCallable(() -> {
            UserResource userResource = keycloak.realm(realm).users().get(keycloakUserId);
            UserRepresentation user = userResource.toRepresentation();

            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            attributes.put(attributeName, Collections.singletonList(attributeValue));
            user.setAttributes(attributes);
            userResource.update(user);
            log.debug("Updated Keycloak user '{}' with attribute '{}' = '{}'", keycloakUserId, attributeName, attributeValue);
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic()); // Offload blocking admin client call
    }
}