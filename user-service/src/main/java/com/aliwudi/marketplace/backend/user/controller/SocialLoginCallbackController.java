package com.aliwudi.marketplace.backend.auth.controller;

import com.aliwudi.marketplace.backend.user.service.UserService; // Import your UserService
import com.aliwudi.marketplace.backend.common.model.User; // Import your User model
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException; // If you handle duplicates
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException; // If you handle not found

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.LocalDateTime; // For setting creation/update times
import java.util.Map;
import java.util.Objects; // For Objects.requireNonNull

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class SocialLoginCallbackController {

    private final WebClient.Builder webClientBuilder;
    private WebClient webClient;
    private final UserService userService; // Inject UserService

    @Value("${auth-server.auth-url}")
    private String keycloakAuthServerUrl;

    @Value("${auth-server.realm}")
    private String keycloakRealm;

    @Value("${auth-server.resource}")
    private String keycloakClientId;

    @Value("${app.social.google.redirect-uri}")
    private String googleCallbackUri;

    @Value("${app.social.facebook.redirect-uri}")
    private String facebookCallbackUri;

    @Value("${app.frontend.redirect-scheme}")
    private String frontendRedirectScheme;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.webClient = webClientBuilder.baseUrl(keycloakAuthServerUrl).build();
    }

    @GetMapping("/google/callback")
    public Mono<Void> handleGoogleCallback(@RequestParam String code,
                                           @RequestParam String state,
                                           ServerWebExchange exchange) {
        log.info("Received Google callback. Code: {}, State: {}", code, state);
        // TODO: IMPORTANT! Implement robust CSRF state validation here.

        String tokenExchangeUrl = String.format("/realms/%s/broker/google/token", keycloakRealm);

        return webClient.post()
                .uri(tokenExchangeUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("code", code)
                                 .with("grant_type", "authorization_code")
                                 .with("redirect_uri", googleCallbackUri)
                                 .with("client_id", keycloakClientId))
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse ->
                    clientResponse.bodyToMono(String.class)
                                  .flatMap(errorBody -> {
                                      log.error("Error from Keycloak broker for Google login: Status={}, Body={}",
                                                clientResponse.statusCode(), errorBody);
                                      return Mono.error(new RuntimeException("Keycloak brokering failed: " + errorBody));
                                  })
                )
                .bodyToMono(Map.class)
                .flatMap(keycloakTokens -> {
                    String accessToken = (String) keycloakTokens.get("access_token");
                    String idToken = (String) keycloakTokens.get("id_token");
                    String refreshToken = (String) keycloakTokens.get("refresh_token");

                    // --- NEW: Process ID Token and Persist User Data ---
                    return processAndPersistUser(idToken)
                            .flatMap(persistedUser -> {
                                log.info("User {} (ID: {}) successfully processed/persisted after Google login.",
                                         persistedUser.getEmail(), persistedUser.getId());

                                // Now, redirect to frontend with Keycloak tokens
                                String frontendDeepLink = String.format("%s?access_token=%s&id_token=%s",
                                                                        frontendRedirectScheme, accessToken, idToken);
                                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                                exchange.getResponse().getHeaders().setLocation(URI.create(frontendDeepLink));
                                return Mono.empty();
                            });
                })
                .doOnError(e -> log.error("Failed to process Google callback: {}", e.getMessage(), e))
                .onErrorResume(e -> {
                    log.error("An error occurred during Google social login: {}", e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    return Mono.empty();
                });
    }

    @GetMapping("/facebook/callback")
    public Mono<Void> handleFacebookCallback(@RequestParam String code,
                                            @RequestParam String state,
                                            ServerWebExchange exchange) {
        log.info("Received Facebook callback. Code: {}, State: {}", code, state);
        // TODO: IMPORTANT! Implement robust CSRF state validation here.

        String tokenExchangeUrl = String.format("/realms/%s/broker/facebook/token", keycloakRealm);

        return webClient.post()
                .uri(tokenExchangeUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("code", code)
                                 .with("grant_type", "authorization_code")
                                 .with("redirect_uri", facebookCallbackUri)
                                 .with("client_id", keycloakClientId))
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse ->
                    clientResponse.bodyToMono(String.class)
                                  .flatMap(errorBody -> {
                                      log.error("Error from Keycloak broker for Facebook login: Status={}, Body={}",
                                                clientResponse.statusCode(), errorBody);
                                      return Mono.error(new RuntimeException("Keycloak brokering failed: " + errorBody));
                                  })
                )
                .bodyToMono(Map.class)
                .flatMap(keycloakTokens -> {
                    String accessToken = (String) keycloakTokens.get("access_token");
                    String idToken = (String) keycloakTokens.get("id_token");
                    String refreshToken = (String) keycloakTokens.get("refresh_token");

                    // --- NEW: Process ID Token and Persist User Data ---
                    return processAndPersistUser(idToken)
                            .flatMap(persistedUser -> {
                                log.info("User {} (ID: {}) successfully processed/persisted after Facebook login.",
                                         persistedUser.getEmail(), persistedUser.getId());

                                // Now, redirect to frontend with Keycloak tokens
                                String frontendDeepLink = String.format("%s?access_token=%s&id_token=%s",
                                                                        frontendRedirectScheme, accessToken, idToken);
                                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                                exchange.getResponse().getHeaders().setLocation(URI.create(frontendDeepLink));
                                return Mono.empty();
                            });
                })
                .doOnError(e -> log.error("Failed to process Facebook callback: {}", e.getMessage(), e))
                .onErrorResume(e -> {
                    log.error("An error occurred during Facebook social login: {}", e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    return Mono.empty();
                });
    }

    /**
     * Parses the Keycloak ID Token, extracts user information,
     * and either creates a new user or updates an existing one in the backend database.
     *
     * @param idToken The ID Token received from Keycloak.
     * @return Mono<User> emitting the persisted User entity.
     */
    private Mono<User> processAndPersistUser(String idToken) {
        return Mono.fromCallable(() -> {
            // Decode the ID Token (no signature verification needed here as Keycloak already issued it)
            DecodedJWT jwt = JWT.decode(idToken);
            String authId = jwt.getSubject(); // Keycloak's user ID (sub claim)
            String email = jwt.getClaim("email").asString();
            String firstName = jwt.getClaim("given_name").asString();
            String lastName = jwt.getClaim("family_name").asString();
            String fullName = jwt.getClaim("name").asString();
            // You can extract other claims as needed, e.g., preferred_username, picture, etc.

            log.debug("Decoded ID Token for authId: {}, email: {}", authId, email);

            // Return a DTO or map of claims to pass to userService
            return new UserClaims(authId, email, firstName, lastName, fullName);
        })
        .flatMap(claims -> userService.findByAuthId(claims.authId)
                .switchIfEmpty(Mono.defer(() -> {
                    // User does not exist, create a new one
                    log.info("Creating new user for social login: {}", claims.email);
                    User newUser = User.builder()
                                        .primaryIdentifierType("COME BACK")                                        
                                        .authId(claims.authId)                                      
                                        .email(claims.email)
                                        .firstName(claims.firstName)
                                        .lastName(claims.lastName)
                                        .username(claims.email) // Or generate a unique username if email is not unique
                                        .enabled(true)
                                        .emailVerified(true) // Assuming social provider verifies email which is very likely                                    
                                        .registrationDate(LocalDateTime.now())
                                        .lastLogin(LocalDateTime.now())
                                        .build();
                    // Call a service method to save the new user
                    // This method needs to be added/modified in your UserService
                    return userService.createSocialUser(newUser); // This method should also handle assigning default roles
                }))
                .flatMap(existingUser -> {
                    // User exists, potentially update last login time or other details
                    log.info("User {} already exists. Updating last login time.", existingUser.getEmail());
                    existingUser.setLastLogin(LocalDateTime.now());
                    // You might want to update other fields here if they can change in the social provider
                    // e.g., existingUser.setFirstName(claims.firstName);
                    return userService.updateUser(existingUser); // This method should save changes
                })
                .onErrorResume(e -> {
                    log.error("Error processing or persisting user from ID Token: {}", e.getMessage(), e);
                    return Mono.error(new RuntimeException("Failed to process social user data.", e));
                })
        );
    }

    // Helper class to hold parsed claims for easier passing
    private record UserClaims(String authId, String email, String firstName, String lastName, String fullName) {}
}