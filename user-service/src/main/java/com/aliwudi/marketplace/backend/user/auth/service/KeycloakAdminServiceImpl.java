package com.aliwudi.marketplace.backend.user.auth.service;

import com.aliwudi.marketplace.backend.common.constants.IdentifierType;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation; // Keep for potential future password reset method
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
import jakarta.ws.rs.NotFoundException;

import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.common.exception.ServiceException;
import com.aliwudi.marketplace.backend.common.exception.UserNotFoundException;
import com.aliwudi.marketplace.backend.common.model.User;
import static com.aliwudi.marketplace.backend.user.enumeration.AuthServerAttribute.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManagerFactory;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.keycloak.admin.client.KeycloakBuilder;

/**
 * Implementation of IAdminService for Keycloak Authorization Server. Handles
 * interactions with Keycloak Admin API. Now includes enhanced methods for user
 * creation and password setting, supporting the "backend-first" hybrid
 * registration flow, with generic naming.
 */
@Service
@Slf4j
public class KeycloakAdminServiceImpl implements IAdminService { // Implements the generic interface

    private KeycloakSettings kcSettings;

    @Value("${truststore.path}")
    private String truststorePath;

    @Value("${truststore.password}")
    private String truststorePassword;

    private final Map<String, Keycloak> authServerClientCache = new ConcurrentHashMap<>(); // Generic name

    public KeycloakAdminServiceImpl(KeycloakSettings kcSettings) {
        this.kcSettings = kcSettings;
    }

    // Helper method to get an Authorization Server client instance, using the cache.
    // This client uses the 'client_credentials' grant type for service account authentication.
    private Keycloak getAuthServerClient() { // Generic method name
        return authServerClientCache.computeIfAbsent(kcSettings.getRealm(), realm -> {
            log.info("Initializing Keycloak admin client for realm: {}", realm);

            try {
                KeyStore trustStore = KeyStore.getInstance("PKCS12");
                if (this.truststorePath != null && !this.truststorePath.isEmpty()) {
                    InputStream is;
                    if (truststorePath.startsWith("classpath:")) {
                        is = getClass().getClassLoader().getResourceAsStream(truststorePath.substring("classpath:".length()));
                        if (is == null) {
                            throw new RuntimeException("Truststore file not found on classpath: " + truststorePath);
                        }
                    } else {
                        // Assuming it's a file system path if not 'classpath:'
                        is = Files.newInputStream(Paths.get(truststorePath));
                    }

                    try (InputStream stream = is) { // Use a try-with-resources for the stream
                        trustStore.load(stream, truststorePassword.toCharArray()); // Convert password String to char[]
                    }

                    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    trustManagerFactory.init(trustStore);
                    X509TrustManager x509TrustManager = null;
                    TrustManager[] trustMgr = trustManagerFactory.getTrustManagers();

                    for (int i = 0; i < trustMgr.length; ++i) {
                        TrustManager tm = trustMgr[i];
                        if (tm instanceof X509TrustManager x509TrustMgr) {
                            x509TrustManager = x509TrustMgr;
                            break;
                        }
                    }

                    if (x509TrustManager == null) {
                        throw new NoSuchAlgorithmException("No X509TrustManager found in TrustManagerFactory");
                    } else {
                        SSLContext sslContext = SSLContext.getInstance("TLS");
                        sslContext.init((KeyManager[]) null, trustManagerFactory.getTrustManagers(), new SecureRandom());
                        HostnameVerifier hostnameVerifier = (hostname, session) -> true;

                        return KeycloakBuilder.builder()
                                .serverUrl(kcSettings.getUrl())
                                .realm(kcSettings.getRealm())
                                .clientId(kcSettings.getClientId())
                                .grantType(kcSettings.getGrantType())
                                .clientSecret(kcSettings.getClientSecret())
                                .resteasyClient(
                                        new ResteasyClientBuilderImpl()
                                                .connectionPoolSize(10)
                                                .sslContext(sslContext) // Set the SSL context
                                                .hostnameVerifier(hostnameVerifier) // Set the hostname verifier
                                                .build()
                                )
                                .build();
                    }
                } else {
                    throw new IllegalArgumentException("Truststore Path must be configured.");
                }

            } catch (Exception ex) {
                throw new RuntimeException("Failed to Keycloak admin client", ex);
            }
        });
    }

    /**
     * Creates a user in the Authorization Server and sets their password. This
     * method is part of the "backend-first" hybrid registration.
     *
     * @param user the user model containing user details (email, phone, names,
     * primaryIdentifierType, roles, etc.). NOTE: This method DOES NOT set the
     * password as per the IAdminService interface. A separate password setting
     * mechanism or method call is required.
     * @return A Mono emitting the Authorization Server's 'authId' (UUID) of the
     * newly created user.
     * @throws DuplicateResourceException if user with the given identifier
     * (email/phone) already exists in Authorization Server.
     * @throws RuntimeException if user creation fails for other reasons.
     */
    @Override
    public Mono<String> createUserInAuthServer(User user) { // MODIFIED: Removed password parameter to match interface
        return Mono.fromCallable(() -> {

            String keycloakUsername = user.getPrimaryIdentifier();
            log.info("Attempting to create user '{}' in Keycloak for internal ID: {}", keycloakUsername, user.getId());

            Keycloak keycloak = getAuthServerClient();

            UserRepresentation keycloakUser = new UserRepresentation();
            keycloakUser.setEnabled(true);
            keycloakUser.setUsername(keycloakUsername); // Set the dynamic username
            keycloakUser.setEmail(user.getEmail()); // Always set email if available

            // the given_name 
            // but we will still create the firstName attribute below for consistentcy
            keycloakUser.setFirstName(user.getFirstName());

            // the family_name 
            // but we will still create the lastName attribute below for consistentcy       
            keycloakUser.setLastName(user.getLastName());

            keycloakUser.setEmailVerified(false);

            // Set password
            CredentialRepresentation passwordCred = new CredentialRepresentation();
            passwordCred.setTemporary(false);
            passwordCred.setType(CredentialRepresentation.PASSWORD);
            passwordCred.setValue(user.getPassword());
            keycloakUser.setCredentials(Collections.singletonList(passwordCred));

            // Set custom attributes
            String roleNames = user.getRoles().stream()
                    .map(role -> role.getName().name())
                    .collect(Collectors.joining(","));

            Map<String, List<String>> customAttributes = new HashMap<>();
            customAttributes.put(userId.name(), Collections.singletonList(String.valueOf(user.getId())));
            //for consistency set the firstName attribute - we know is same as given_name
            customAttributes.put(firstName.name(), Collections.singletonList(String.valueOf(user.getFirstName())));
            //for consistency set the lastName attribute - we know is same as family_name
            customAttributes.put(lastName.name(), Collections.singletonList(String.valueOf(user.getLastName())));
            customAttributes.put(primaryIdentifierType.name(), Collections.singletonList(user.getPrimaryIdentifierType()));
            customAttributes.put(phone.name(), Collections.singletonList(String.valueOf(user.getPhoneNumber())));
            customAttributes.put(phoneVerified.name(), Collections.singletonList(String.valueOf(user.isPhoneVerified())));
            customAttributes.put(roles.name(), Collections.singletonList(roleNames));

            // Add phone number as an attribute if available
            if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
                customAttributes.put("phoneNumber", Collections.singletonList(user.getPhoneNumber()));
            }
            keycloakUser.setAttributes(customAttributes);

            log.info("Attempting to create user '{}' in Authorization Server realm '{}' with user_id: {}.", keycloakUsername, kcSettings.getRealm(), user.getId());

            String authServerUserId;

            try (Response response = keycloak.realm(kcSettings.getRealm()).users().create(keycloakUser)) {
                switch (response.getStatus()) {
                    case 201 -> {
                        authServerUserId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
                        log.info("Successfully created user '{}' in Authorization Server. Auth Server ID: {}", keycloakUsername, authServerUserId);
                    }
                    case 409 -> { // Conflict
                        String errorBody = response.readEntity(String.class);
                        log.warn("User with identifier '{}' already exists in Authorization Server (Status 409). Response: {}", user.getPrimaryIdentifier(), errorBody);
                        throw new DuplicateResourceException(
                                String.format(ApiResponseMessages.IDENTIFIER_ALREADY_EXISTS_IN_AUTHORIZATION_SERVER, user.getPrimaryIdentifier())
                        );
                    }
                    default -> {
                        String errorBody = response.readEntity(String.class);
                        log.error("Failed to create user '{}' in Authorization Server. Status: {}, Response: {}", keycloakUsername, response.getStatus(), errorBody);
                        throw new RuntimeException(String.format("Authorization Server user creation failed: Status %d, Response: %s", response.getStatus(), errorBody));
                    }
                }
                return authServerUserId;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates a user's email verified status in Keycloak. This method is
     * specifically called by the EmailVerificationService after OTP validation.
     *
     * @param authServerUserId The Keycloak user ID.
     * @param isVerified The status to set (true for verified, false for
     * unverified).
     * @return Mono<Boolean> indicating completion.
     */
    @Override
    public Mono<Boolean> updateEmailVerifiedStatus(String authServerUserId, boolean isVerified) {
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getAuthServerClient();
            try {
                UserResource userResource = keycloak.realm(kcSettings.getRealm()).users().get(authServerUserId);
                UserRepresentation user = userResource.toRepresentation();
                user.setEmailVerified(isVerified);
                userResource.update(user);
                log.info("Keycloak user '{}' email verification status updated to {}", authServerUserId, isVerified);
                return true;
            } catch (NotFoundException e) {
                log.warn("Keycloak user '{}' not found when trying to update email verification status. It might have been deleted.", authServerUserId);
                throw new UserNotFoundException("User not found in Keycloak: " + authServerUserId, e);
            } catch (Exception e) {
                log.error("Failed to update email verification status for user '{}': {}", authServerUserId, e.getMessage(), e);
                throw new ServiceException("Failed to update email verification status in Keycloak.", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates a user's phone verified status in Keycloak. This method updates
     * the custom attribute "phoneVerified".
     *
     * @param authServerUserId The Keycloak user ID.
     * @param verified The status to set (true for verified, false for
     * unverified).
     * @return Mono<Boolean> indicating completion.
     */
    @Override
    public Mono<Boolean> updatePhoneVerifiedStatus(String authServerUserId, boolean verified) {
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getAuthServerClient();
            try {
                UserResource userResource = keycloak.realm(kcSettings.getRealm()).users().get(authServerUserId);
                UserRepresentation user = userResource.toRepresentation();

                Map<String, List<String>> attributes = user.getAttributes();
                if (attributes == null) {
                    attributes = new HashMap<>();
                }
                attributes.put(phoneVerified.name(), Collections.singletonList(String.valueOf(verified)));
                user.setAttributes(attributes);
                userResource.update(user);
                log.info("Keycloak user '{}' phone verification status updated to {}", authServerUserId, verified);
                return true;
            } catch (NotFoundException e) {
                log.warn("Keycloak user '{}' not found when trying to update phone verification status. It might have been deleted.", authServerUserId);
                throw new UserNotFoundException("User not found in Keycloak: " + authServerUserId, e);
            } catch (Exception e) {
                log.error("Failed to update phone verification status for user '{}': {}", authServerUserId, e.getMessage(), e);
                throw new ServiceException("Failed to update phone verification status in Keycloak.", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Retrieves a user from the Authorization Server (Keycloak) by their
     * Authorization Server ID.
     *
     * @param authServerUserId The Authorization Server ID of the user.
     * @return A Mono emitting the UserRepresentation, or empty if not found.
     */
    @Override
    public Mono<UserRepresentation> getUserFromAuthServerById(String authServerUserId) {
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getAuthServerClient();
            try {
                UserRepresentation user = keycloak.realm(kcSettings.getRealm()).users().get(authServerUserId).toRepresentation();
                log.debug("Found Authorization Server user '{}' in realm '{}'", authServerUserId, kcSettings.getRealm());
                return user;
            } catch (NotFoundException e) {
                log.warn("Authorization Server user '{}' not found in realm '{}'.", authServerUserId, kcSettings.getRealm());
                return null;
            } catch (Exception e) {
                log.error("Failed to get Authorization Server user '{}' from realm '{}': {}", authServerUserId, kcSettings.getRealm(), e.getMessage(), e);
                throw e;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Retrieves a user from the Authorization Server (Keycloak) by their email.
     *
     * @param email The email of the user.
     * @return A Mono emitting the UserRepresentation, or empty if not found.
     */
    @Override
    public Mono<UserRepresentation> getUserFromAuthServerByEmail(String email) {
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getAuthServerClient();
            List<UserRepresentation> users = keycloak.realm(kcSettings.getRealm()).users().searchByEmail(email, true);
            if (!users.isEmpty()) {
                log.debug("Found user by email '{}' in realm '{}'.", email, kcSettings.getRealm());
                return users.get(0);
            } else {
                log.warn("User with email '{}' not found in realm '{}'.", email, kcSettings.getRealm());
                return null;
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
    public Mono<Void> updateUserAttribute(String authServerUserId, String attributeName, String attributeValue) {
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getAuthServerClient();
            try {
                UserResource userResource = keycloak.realm(kcSettings.getRealm()).users().get(authServerUserId);
                UserRepresentation user = userResource.toRepresentation();

                Map<String, List<String>> attributes = user.getAttributes();
                if (attributes == null) {
                    attributes = new HashMap<>();
                }
                attributes.put(attributeName, Collections.singletonList(attributeValue));
                user.setAttributes(attributes);
                userResource.update(user);
                log.debug("Updated Authorization Server user '{}' in realm '{}' with attribute '{}' = '{}'", authServerUserId, kcSettings.getRealm(), attributeName, attributeValue);
                return (Void) null;
            } catch (Exception e) {
                log.error("Failed to update user '{}' attribute '{}' in Authorization Server realm '{}': {}", authServerUserId, attributeName, kcSettings.getRealm(), e.getMessage(), e);
                throw e;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a user from the Authorization Server (Keycloak).
     *
     * @param authServerUserId The Authorization Server ID of the user to
     * delete.
     * @return A Mono<Void> indicating completion.
     */
    @Override
    public Mono<Void> deleteUserFromAuthServer(String authServerUserId) {
        return Mono.fromCallable(() -> {
            Keycloak keycloak = getAuthServerClient();
            try {
                keycloak.realm(kcSettings.getRealm()).users().delete(authServerUserId);
                log.info("Successfully deleted user '{}' from Authorization Server realm '{}'.", authServerUserId, kcSettings.getRealm());
                return (Void) null;
            } catch (NotFoundException e) {
                log.warn("Authorization Server user '{}' not found in realm '{}' during deletion attempt. It might have been deleted already.", authServerUserId, kcSettings.getRealm());
                return (Void) null;
            } catch (Exception e) {
                log.error("Failed to delete user '{}' from Authorization Server realm '{}': {}", authServerUserId, kcSettings.getRealm(), e.getMessage(), e);
                throw e;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
