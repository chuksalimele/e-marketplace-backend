package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.common.constants.IdentifierType;
import com.aliwudi.marketplace.backend.common.exception.KeycloakBrokeringException;
import com.aliwudi.marketplace.backend.common.exception.SocialLoginProcessingException;
import com.aliwudi.marketplace.backend.user.service.UserService;
import com.aliwudi.marketplace.backend.common.dto.UserProfileCreateRequest;
import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.user.auth.service.KeycloakSettings;
import static com.aliwudi.marketplace.backend.user.enumeration.KeycloakFormParams.*;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class SocialLoginCallbackController {

    private final WebClient.Builder webClientBuilder;
    private final ReactorClientHttpConnector connector;
    private final UserService userService;
    private final JwtDecoder jwtDecoder;
    private final KeycloakSettings kcSetting;
    private final ReactiveRedisTemplate<String, String> redisStateTemplate; // For CSRF state
    private final ReactiveRedisTemplate<String, Map<String, String>> redisTokenTemplate; // For tokens

    @Value("${app.social.google.redirect-uri}")
    private String googleCallbackUri;

    @Value("${app.social.facebook.redirect-uri}")
    private String facebookCallbackUri;

    @Value("${app.frontend.redirect-scheme}")
    private String frontendRedirectScheme;

    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    private static final String STATE_KEY_PREFIX = "csrf:state:";
    private static final String TOKEN_KEY_PREFIX = "tokens:";

    private WebClient webClient;

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
                                          @RequestParam(value = "sessionId", required = false) String sessionId,
                                          ServerWebExchange exchange) {
        return handleSocialCallback("google", code, state, sessionId, googleCallbackUri, exchange);
    }

    @GetMapping("/facebook/callback")
    public Mono<Void> handleFacebookCallback(@RequestParam String code,
                                            @RequestParam String state,
                                            @RequestParam(value = "sessionId", required = false) String sessionId,
                                            ServerWebExchange exchange) {
        return handleSocialCallback("facebook", code, state, sessionId, facebookCallbackUri, exchange);
    }

    /**
     * Generates and stores a CSRF state token in Redis for the social login flow.
     *
     * @param sessionId Unique session identifier (UUID)
     * @return Mono<String> emitting the generated state
     */
    public Mono<String> generateCsrfState(String sessionId) {
        return Mono.justOrEmpty(sessionId)
                .filter(this::isValidUUID)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Session ID must be a valid UUID")))
                .flatMap(sid -> {
                    String state = UUID.randomUUID().toString();
                    String redisKey = STATE_KEY_PREFIX + sid;
                    return redisStateTemplate.opsForValue()
                            .set(redisKey, state, TOKEN_TTL)
                            .thenReturn(state);
                });
    }

    /**
     * Generic handler for social login callbacks (Google, Facebook).
     *
     * @param provider     The social login provider (e.g., "google", "facebook")
     * @param code         The authorization code
     * @param state        The CSRF state parameter
     * @param sessionId    The session identifier
     * @param redirectUri  The provider-specific redirect URI
     * @param exchange     The ServerWebExchange
     * @return Mono<Void> for the redirect response
     */
    private Mono<Void> handleSocialCallback(String provider, String code, String state, String sessionId,
                                           String redirectUri, ServerWebExchange exchange) {
        if (code == null || code.isBlank()) {
            log.error("Authorization code is missing for {} login", provider);
            return redirectWithError(exchange, "Missing authorization code");
        }

        log.info("Received {} callback. SessionId: {}", provider, sessionId);

        return validateCsrfState(state, sessionId)
                .then(Mono.defer(() -> {
                    String tokenExchangeUrl = String.format("/realms/%s/broker/%s/token", kcSetting.getRealm(), provider);

                    return webClient.post()
                            .uri(tokenExchangeUrl)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(BodyInserters.fromFormData("code", code)
                                    .with(GRANT_TYPE.name(), AUTHORIZATION_CODE.name())
                                    .with(REDIRECT_URI.name(), redirectUri)
                                    .with(CLIENT_ID.name(), kcSetting.getClientId()))
                            .retrieve()
                            .onStatus(status->status.isError(), clientResponse ->
                                    clientResponse.bodyToMono(String.class)
                                            .flatMap(errorBody -> {
                                                log.error("Error from Keycloak broker for {} login: Status={}, Body={}",
                                                        provider, clientResponse.statusCode(), errorBody);
                                               
                                               redirectWithError(exchange,
                                                        "Keycloak brokering failed for " + provider);
                                                return Mono.empty();
                                            }))
                            .bodyToMono(Map.class)
                            .flatMap(keycloakTokens -> {
                                String accessToken = (String) keycloakTokens.get("access_token");
                                String idToken = (String) keycloakTokens.get("id_token");

                                if (accessToken == null || idToken == null) {
                                    log.error("Missing tokens in Keycloak response for {} login", provider);
                                    return redirectWithError(exchange, "Missing access_token or id_token");
                                }

                                // Store tokens in Redis
                                String tokenKey = TOKEN_KEY_PREFIX + sessionId;
                                Map<String, String> tokens = Map.of("access_token", accessToken, "id_token", idToken);
                                return redisTokenTemplate.opsForValue()
                                        .set(tokenKey, tokens, TOKEN_TTL)
                                        .then(processAndPersistUser(idToken))
                                        .flatMap(persistedUser -> {
                                            log.info("User (ID: {}) successfully processed/persisted after {} login.",
                                                    persistedUser.getId(), provider);

                                            String frontendDeepLink = String.format("%s?sessionId=%s",
                                                    frontendRedirectScheme, sessionId);
                                            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                                            exchange.getResponse().getHeaders().setLocation(URI.create(frontendDeepLink));
                                            return Mono.empty();
                                        })
                                        .onErrorResume(e -> redirectWithError(exchange, e.getMessage()));
                            });
                }))
                .doFinally(signal -> redisStateTemplate.delete(STATE_KEY_PREFIX + sessionId).subscribe())
                .then();
    }

    /**
     * Validates the CSRF state parameter against the value stored in Redis.
     *
     * @param state     The state parameter from the callback
     * @param sessionId The session ID to retrieve the stored state
     * @return Mono<Void> if valid, or an error redirect if invalid
     */
    private Mono<Void> validateCsrfState(String state, String sessionId) {
        if (state == null || state.isBlank() || sessionId == null || sessionId.isBlank()) {
            return Mono.error(new SecurityException("Invalid or missing CSRF state or session ID"));
        }
        if (!isValidUUID(sessionId)) {
            return Mono.error(new SecurityException("Invalid session ID format"));
        }

        String redisKey = STATE_KEY_PREFIX + sessionId;
        return redisStateTemplate.opsForValue()
                .get(redisKey)
                .switchIfEmpty(Mono.error(new SecurityException("CSRF state not found for session ID: " + sessionId)))
                .flatMap(storedState -> {
                    if (!state.equals(storedState)) {
                        log.error("CSRF state validation failed for sessionId: {}", sessionId);
                        return Mono.error(new SecurityException("CSRF state validation failed"));
                    }
                    return Mono.empty();
                });
    }

    /**
     * Parses the Keycloak ID Token, extracts user information with validation,
     * constructs a UserProfileCreateRequest, and persists or updates the user.
     *
     * @param idToken The ID Token string from Keycloak
     * @return Mono<User> emitting the persisted User entity
     */
    private Mono<User> processAndPersistUser(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return Mono.error(new SocialLoginProcessingException("ID token is missing or empty"));
        }

        return Mono.fromCallable(() -> {
            try {
                Jwt jwt = jwtDecoder.decode(idToken);
                Map<String, Object> claims = jwt.getClaims();

                String authId = jwt.getSubject();
                if (authId == null || authId.isBlank()) {
                    throw new SocialLoginProcessingException("JWT subject (authId) is missing");
                }

                // Defensive claim validation
                String email = claims.get("email") != null ? claims.get("email").toString() : "";
                String firstName = claims.get("given_name") != null ? claims.get("given_name").toString() : "";
                String lastName = claims.get("family_name") != null ? claims.get("family_name").toString() : "";

                if (email.isBlank()) {
                    log.warn("Email is missing in JWT claims for authId: {}", authId);
                }

                log.debug("Decoded ID Token for authId: {}", authId);

                UserProfileCreateRequest userProfileCreateRequest = new UserProfileCreateRequest();
                userProfileCreateRequest.setAuthId(authId);
                userProfileCreateRequest.setEmail(email);
                userProfileCreateRequest.setFirstName(firstName);
                userProfileCreateRequest.setLastName(lastName);
                userProfileCreateRequest.setIdentifierType(IdentifierType.EMAIL);
                userProfileCreateRequest.setPhoneNumber("");
                userProfileCreateRequest.setPassword(UUID.randomUUID().toString());

                Set<String> roles = new HashSet<>();
                roles.add(ERole.ROLE_USER.name());
                userProfileCreateRequest.setRoles(roles);

                return userProfileCreateRequest;

            } catch (Exception e) {
                throw new SocialLoginProcessingException("Failed to decode or parse ID Token with JwtDecoder.", e);
            }
        })
                .flatMap(userProfileCreateRequest ->
                        userService.findByAuthId(userProfileCreateRequest.getAuthId())
                                .switchIfEmpty(Mono.defer(() -> {
                                    log.info("Creating new social user with authId: {}", userProfileCreateRequest.getAuthId());
                                    return userService.createUser(userProfileCreateRequest);
                                })))
                .flatMap(user -> {
                    log.info("User (ID: {}) logging in. Updating last login time.", user.getId());
                    user.setLastLoginAt(LocalDateTime.now());
                    return userService.updateUserOnDB(user);
                })
                .onErrorResume(e -> {
                    log.error("Error during user persistence after social login: {}", e.getMessage(), e);
                    return Mono.error(new SocialLoginProcessingException("Failed to persist user data after social login.", e));
                });
    }

    /**
     * Retrieves stored tokens from Redis for a given session ID.
     *
     * @param sessionId The session identifier
     * @return Mono<Map<String, String>> containing access_token and id_token
     */
    @GetMapping("/tokens")
    public Mono<Map<String, String>> getTokens(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.isBlank() || !isValidUUID(sessionId)) {
            return Mono.error(new IllegalArgumentException("Session ID must be a valid UUID"));
        }

        String tokenKey = TOKEN_KEY_PREFIX + sessionId;
        return redisTokenTemplate.opsForValue()
                .get(tokenKey)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid or expired session ID")))
                .doFinally(signal -> redisTokenTemplate.delete(tokenKey).subscribe());
    }

    /**
     * Redirects to the frontend with an error message in the query parameters.
     *
     * @param exchange The ServerWebExchange
     * @param errorMessage The error message to include
     * @return Mono<Void> for the redirect response
     */
    private Mono<Void> redirectWithError(ServerWebExchange exchange, String errorMessage) {
        String errorRedirect = String.format("%s?error=%s", frontendRedirectScheme,
                URLEncoder.encode(errorMessage, StandardCharsets.UTF_8));
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(errorRedirect));
        return Mono.empty();
    }

    /**
     * Validates if a string is a valid UUID.
     *
     * @param uuid The string to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidUUID(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}