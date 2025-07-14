package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.common.constants.IdentifierType;
import com.aliwudi.marketplace.backend.common.exception.KeycloakBrokeringException;
import com.aliwudi.marketplace.backend.common.exception.SocialLoginProcessingException;
import com.aliwudi.marketplace.backend.user.service.UserService;
import com.aliwudi.marketplace.backend.common.model.User; // Keep if User model is still directly used elsewhere
import com.aliwudi.marketplace.backend.common.dto.UserProfileCreateRequest; // NEW IMPORT: Your DTO
import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import com.aliwudi.marketplace.backend.common.model.Role;
import com.aliwudi.marketplace.backend.user.auth.service.KeycloakSettings;
import com.aliwudi.marketplace.backend.user.dto.UserRequest;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set; // For roles
import java.util.HashSet; // For roles
import java.util.UUID; // For generating placeholder password
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class SocialLoginCallbackController {

    private final WebClient.Builder webClientBuilder;
    private final ReactorClientHttpConnector connector;
    private WebClient webClient;
    private final UserService userService;
    private final JwtDecoder jwtDecoder;

    private final KeycloakSettings kcSetting;

    @Value("${app.social.google.redirect-uri}")
    private String googleCallbackUri;

    @Value("${app.social.facebook.redirect-uri}")
    private String facebookCallbackUri;

    @Value("${app.frontend.redirect-scheme}")
    private String frontendRedirectScheme;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.webClient = webClientBuilder
                .clientConnector(connector)
                .baseUrl(kcSetting.getUrl())
                .build();
    }

    @GetMapping("/google/callback")
    public Mono<Void> handleGoogleCallback(@RequestParam String code,
            @RequestParam String state,
            ServerWebExchange exchange) {
        log.info("Received Google callback. Code: {}, State: {}", code, state);
        // TODO: IMPORTANT! Implement robust CSRF state validation here.

        String tokenExchangeUrl = String.format("/realms/%s/broker/google/token", kcSetting.getRealm());

        return webClient.post()
                .uri(tokenExchangeUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("code", code)
                        .with("grant_type", "authorization_code")
                        .with("redirect_uri", googleCallbackUri)
                        .with("client_id", kcSetting.getClientId()))
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse
                        -> clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("Error from Keycloak broker for Google login: Status={}, Body={}",
                                    clientResponse.statusCode(), errorBody);
                            return Mono.error(new KeycloakBrokeringException("Keycloak brokering failed for Google: " + errorBody));
                        })
                )
                .bodyToMono(Map.class)
                .flatMap(keycloakTokens -> {
                    String accessToken = (String) keycloakTokens.get("access_token");
                    String idToken = (String) keycloakTokens.get("id_token");

                    return processAndPersistUser(idToken)
                            .flatMap(persistedUser -> { // Now returns User object from userService
                                log.info("User {} (ID: {}) successfully processed/persisted after Google login.",
                                        persistedUser.getEmail(), persistedUser.getId());

                                String frontendDeepLink = String.format("%s?access_token=%s&id_token=%s",
                                        frontendRedirectScheme, accessToken, idToken);
                                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                                exchange.getResponse().getHeaders().setLocation(URI.create(frontendDeepLink));
                                return Mono.empty();
                            });
                });
    }

    @GetMapping("/facebook/callback")
    public Mono<Void> handleFacebookCallback(@RequestParam String code,
            @RequestParam String state,
            ServerWebExchange exchange) {
        log.info("Received Facebook callback. Code: {}, State: {}", code, state);
        // TODO: IMPORTANT! Implement robust CSRF state validation here.

        String tokenExchangeUrl = String.format("/realms/%s/broker/facebook/token", kcSetting.getRealm());

        return webClient.post()
                .uri(tokenExchangeUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("code", code)
                        .with("grant_type", "authorization_code")
                        .with("redirect_uri", facebookCallbackUri)
                        .with("client_id", kcSetting.getClientId()))
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse
                        -> clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("Error from Keycloak broker for Facebook login: Status={}, Body={}",
                                    clientResponse.statusCode(), errorBody);
                            return Mono.error(new KeycloakBrokeringException("Keycloak brokering failed for Facebook: " + errorBody));
                        })
                )
                .bodyToMono(Map.class)
                .flatMap(keycloakTokens -> {
                    String accessToken = (String) keycloakTokens.get("access_token");
                    String idToken = (String) keycloakTokens.get("id_token");

                    return processAndPersistUser(idToken)
                            .flatMap(persistedUser -> { // Now returns User object from userService
                                log.info("User {} (ID: {}) successfully processed/persisted after Facebook login.",
                                        persistedUser.getEmail(), persistedUser.getId());

                                String frontendDeepLink = String.format("%s?access_token=%s&id_token=%s",
                                        frontendRedirectScheme, accessToken, idToken);
                                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                                exchange.getResponse().getHeaders().setLocation(URI.create(frontendDeepLink));
                                return Mono.empty();
                            });
                });
    }

    /**
     * Parses the Keycloak ID Token using JwtDecoder, extracts user information,
     * constructs a UserProfileCreateRequest, and then either creates a new user
     * or updates an existing one in the backend database.
     *
     * @param idToken The ID Token string received from Keycloak.
     * @return Mono<User> emitting the persisted User entity.
     */
    private Mono<User> processAndPersistUser(String idToken) {
        return Mono.fromCallable(() -> {
            try {
                Jwt jwt = jwtDecoder.decode(idToken);
                Map<String, Object> claims = jwt.getClaims();

                String authId = jwt.getSubject();
                String email = (String) claims.get("email");
                String firstName = (String) claims.get("given_name");
                String lastName = (String) claims.get("family_name");
                String fullName = (String) claims.get("name"); // Used for logging, not directly in DTO

                log.debug("Decoded ID Token for authId: {}, email: {}", authId, email);

                // Construct UserProfileCreateRequest
                UserProfileCreateRequest userProfileCreateRequest = new UserProfileCreateRequest();
                userProfileCreateRequest.setAuthId(authId);
                userProfileCreateRequest.setEmail(email);
                userProfileCreateRequest.setFirstName(firstName);
                userProfileCreateRequest.setLastName(lastName);
                userProfileCreateRequest.setIdentifierType(IdentifierType.EMAIL); // For social login, email is primary identifier
                userProfileCreateRequest.setPhoneNumber(""); // Not provided by social login
                // Set a placeholder password to satisfy @NotBlank on DTO. UserService.createSocialUser will ignore it.
                userProfileCreateRequest.setPassword(UUID.randomUUID().toString());

                Set<String> roles = new HashSet<>();
                roles.add(ERole.ROLE_USER.name()); // Assign a default role for social users
                userProfileCreateRequest.setRoles(roles);

                return userProfileCreateRequest;

            } catch (Exception e) {
                throw new SocialLoginProcessingException("Failed to decode or parse ID Token with JwtDecoder.", e);
            }
        })
                .flatMap(userProfileCreateRequest
                        -> userService.findByAuthId(userProfileCreateRequest.getAuthId())
                        .switchIfEmpty(Mono.defer(() -> {
                            log.info("Creating new social user: {}", userProfileCreateRequest.getEmail());
                            // Call the new createSocialUser method in UserService
                            return userService.createUser(userProfileCreateRequest);
                        })))
                .flatMap(existingUser -> {
                    log.info("User {} already exists. Updating last login time.", existingUser.getEmail());
                    existingUser.setLastLoginAt(LocalDateTime.now());
                    return userService.updateUserOnDB(existingUser);
                })
                .onErrorResume(e -> {
                    log.error("Error during user persistence after social login: {}", e.getMessage(), e);
                    return Mono.error(new SocialLoginProcessingException("Failed to persist user data after social login.", e));
                });
    }
}
