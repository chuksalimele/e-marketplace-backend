package com.aliwudi.marketplace.backend.common.util;

import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties; // Import properties
import reactor.core.publisher.Mono;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component; // For @Component annotation
import org.springframework.web.server.ServerWebExchange;
import org.slf4j.Logger; // For logging
import org.slf4j.LoggerFactory; // For logging

/**
 * Utility class to retrieve authenticated user ID from Spring Security context
 * or HTTP headers.
 */
@Component // Make this a Spring-managed component
public class AuthUtil {

    private static final Logger log = LoggerFactory.getLogger(AuthUtil.class);

    private final String principalClaimName;

    // Inject OAuth2ResourceServerProperties to get the configured principal claim name
    public AuthUtil(OAuth2ResourceServerProperties oauth2ResourceServerProperties) {
        // Get the principal-claim-name from the injected properties
        String configuredClaimName = oauth2ResourceServerProperties.getJwt().getPrincipalClaimName();
        // If principal-claim-name is not explicitly set in application.yml,
        // Spring Security's default JwtAuthenticationConverter uses "sub".
        // We align with that default here.
        if (configuredClaimName == null || configuredClaimName.isEmpty()) {
            this.principalClaimName = "sub";
            log.warn("spring.security.oauth2.resourceserver.jwt.principal-claim-name is not explicitly set. Defaulting to 'sub'.");
        } else {
            this.principalClaimName = configuredClaimName;
            log.debug("Using configured principal claim name: {}", this.principalClaimName);
        }
    }

    /**
     * Helper method to get the current authenticated user's ID from Spring
     * Security context, falling back to X-User-ID HTTP header if necessary.
     * This method handles different principal types (Jwt, Long, UserDetails).
     *
     * @param exchange The ServerWebExchange to access HTTP headers for fallback.
     * @return A Mono emitting the current user's ID (Long).
     * @throws RuntimeException if the user is not authenticated or user ID
     * cannot be determined from either source.
     */
    public Mono<Long> getAuthenticatedUserId(ServerWebExchange exchange) {
        // Step 1: Try to get user ID from SecurityContext (Primary Approach - JWT, UserDetails, Long)
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .flatMap(principal -> {
                    if (principal instanceof Jwt) {
                        // Use the injected principalClaimName here instead of hardcoding "sub"
                        String userIdString = ((Jwt) principal).getClaimAsString(principalClaimName);
                        if (userIdString == null) {
                            log.warn("JWT claim '{}' is null. Falling back to next check.", principalClaimName);
                            return Mono.empty(); // Fall through if claim not found
                        }
                        try {
                            return Mono.just(Long.parseLong(userIdString));
                        } catch (NumberFormatException e) {
                            log.error("JWT claim '{}' ('{}') is not a valid Long ID. Falling back.", principalClaimName, userIdString, e);
                            return Mono.empty(); // Propagate empty to trigger fallback
                        }
                    } else if (principal instanceof Long) {
                        log.debug("Principal is of type Long.");
                        return Mono.just((Long) principal);
                    } else if (principal instanceof UserDetails) {
                        log.debug("Principal is of type UserDetails.");
                        try {
                            String username = ((UserDetails) principal).getUsername();
                            return Mono.just(Long.parseLong(username));
                        } catch (NumberFormatException e) {
                            log.error("Principal username ('{}') is not a valid Long ID. Falling back.", ((UserDetails) principal).getUsername(), e);
                            return Mono.empty(); // Propagate empty to trigger fallback
                        }
                    } else {
                        log.warn("Unsupported principal type in security context: {}. Falling back to header lookup.", principal.getClass().getName());
                        return Mono.empty(); // Propagate empty to trigger fallback
                    }
                })
                // Step 2: If primary approach fails, try to get user ID from X-User-ID header (Secondary Approach)
                .switchIfEmpty(Mono.defer(() -> {
                    String userIdHeader = exchange.getRequest().getHeaders().getFirst("X-User-ID");
                    if (userIdHeader != null && !userIdHeader.isEmpty()) {
                        try {
                            log.debug("Found X-User-ID header: {}", userIdHeader);
                            return Mono.just(Long.parseLong(userIdHeader));
                        } catch (NumberFormatException e) {
                            log.error("X-User-ID header ('{}') is not a valid Long ID.", userIdHeader, e);
                            return Mono.error(new RuntimeException(ApiResponseMessages.INVALID_USER_ID_FORMAT + " (X-User-ID header)"));
                        }
                    }
                    log.error("Authenticated User ID not found via JWT/SecurityContext or X-User-ID header.");
                    return Mono.error(new RuntimeException(ApiResponseMessages.UNAUTHENTICATED_USER)); // More specific error
                }));
    }
}