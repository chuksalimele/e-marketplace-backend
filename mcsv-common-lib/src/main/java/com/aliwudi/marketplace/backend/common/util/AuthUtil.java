package com.aliwudi.marketplace.backend.common.util;

import com.aliwudi.marketplace.backend.common.enumeration.BasicAuthHeaders;
import com.aliwudi.marketplace.backend.common.enumeration.JwtClaims;
import com.aliwudi.marketplace.backend.common.dto.BasicProfile;
import com.aliwudi.marketplace.backend.common.model.Role;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import java.util.List;
import java.util.Set;
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
import org.springframework.security.core.GrantedAuthority;

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
     * Security context, falling back to X-User-AuthID HTTP header if necessary.
     * This method handles different principal types (Jwt, Long, UserDetails).
     *
     * @param exchange The ServerWebExchange to access HTTP headers for
     * fallback.
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
                    String userIdClaimName = JwtClaims.sub.getClaimName(); // we will create in authorizatin server
                    if (principal != null && principal instanceof Jwt jwt) {
                        // Use the injected principalClaimName here instead of hardcoding "sub"
                        String userIdStr = jwt.getClaimAsString(userIdClaimName);
                        if (userIdStr == null) {
                            log.warn("JWT claim '{}' is null. Falling back to next check.", userIdClaimName);
                            return Mono.empty(); // Fall through if claim not found
                        }
                        try {

                            return Mono.just(Long.valueOf(userIdStr));
                        } catch (NumberFormatException e) {
                            log.error("JWT claim '{}' ('{}') is not a valid Long ID. Falling back.", userIdClaimName, userIdStr, e);
                            return Mono.empty(); // Propagate empty to trigger fallback
                        }
                    } else {
                        if (principal != null) {
                            log.warn("Unsupported principal type in security context: {}. Falling back to header lookup.", principal.getClass().getName());
                        } else {
                            log.warn("principal in security context is null");
                        }
                        return Mono.empty(); // Propagate empty to trigger fallback
                    }
                })
                // Step 2: If primary approach fails, try to get user ID from X-User-AuthID header (Secondary Approach)
                .switchIfEmpty(Mono.defer(() -> {
                    String userIdHeader = exchange.getRequest()
                            .getHeaders()
                            .getFirst(BasicAuthHeaders.X_USER_ID.getHeaderName());
                    if (userIdHeader != null && !userIdHeader.isEmpty()) {
                        return Mono.just(Long.valueOf(userIdHeader));
                    }
                    log.error("Authenticated User ID not found via JWT/SecurityContext or X-User-AuthID header.");
                    return Mono.error(new RuntimeException(ApiResponseMessages.UNAUTHENTICATED_USER)); // More specific error
                }));
    }

    /**
     * Helper method to get the current authenticated user's ID from Spring
     * Security context, falling back to X-User-AuthID HTTP header if necessary.
     * This method handles different principal types (Jwt, Long, UserDetails).
     *
     * @param exchange The ServerWebExchange to access HTTP headers for
     * fallback.
     * @return A Mono emitting the current user's ID (Long).
     * @throws RuntimeException if the user is not authenticated or user ID
     * cannot be determined from either source.
     */
    public Mono<BasicProfile> getBasicUserProfile(ServerWebExchange exchange) {
        BasicProfile profile = new BasicProfile();
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .flatMap(authentication -> {
                    Object principal = authentication.getPrincipal();
                    if (principal instanceof Jwt jwt) {
                        profile.setAuthId(jwt.getClaimAsString(JwtClaims.sub.getClaimName()));
                        profile.setEmail(jwt.getClaimAsString(JwtClaims.email.getClaimName()));
                        profile.setPhoneNumber(jwt.getClaimAsString(JwtClaims.phone.getClaimName()));
                        profile.setFirstName(JwtClaims.firstName.getClaimName());
                        profile.setLastName(JwtClaims.lastName.getClaimName());
                        try {
                            profile.setUserId(
                                    Long.getLong(jwt.getClaimAsString(JwtClaims.userId.getClaimName()))
                            );
                        } catch (NumberFormatException e) {
                            return Mono.empty();
                        }

                    } else {
                        log.warn("JWT claim '{}' is null. Falling back to next check.", principalClaimName);
                        return Mono.empty(); // Fall through if claim not found
                    }

                    List<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
                    profile.setRoles(Set.copyOf(roles));

                    return Mono.just(profile);

                })
                // Step 2: If primary approach fails, try to get user ID from X-User-AuthID header (Secondary Approach)
                .switchIfEmpty(Mono.defer(() -> {
                    String authIdHeader = exchange.getRequest()
                            .getHeaders()
                            .getFirst(BasicAuthHeaders.X_USER_AUTH_ID.getHeaderName());
                    String userIdHeader = exchange.getRequest()
                            .getHeaders()
                            .getFirst(BasicAuthHeaders.X_USER_ID.getHeaderName());
                    String emailHeader = exchange.getRequest()
                            .getHeaders()
                            .getFirst(BasicAuthHeaders.X_USER_EMAIL.getHeaderName());
                    String phoneHeader = exchange.getRequest()
                            .getHeaders()
                            .getFirst(BasicAuthHeaders.X_USER_PHONE.getHeaderName());
                    String firstNameHeader = exchange.getRequest()
                            .getHeaders()
                            .getFirst(BasicAuthHeaders.X_USER_FIRST_NAME.getHeaderName());
                    String lastNameHeader = exchange.getRequest()
                            .getHeaders()
                            .getFirst(BasicAuthHeaders.X_USER_LAST_NAME.getHeaderName());
                    String rolesHeader = exchange.getRequest()
                            .getHeaders()
                            .getFirst(BasicAuthHeaders.X_USER_ROLES.getHeaderName());

                    if (authIdHeader != null && !authIdHeader.isEmpty()) {

                        log.debug("Found " + BasicAuthHeaders.X_USER_AUTH_ID.getHeaderName() + " header: {}", authIdHeader);

                        profile.setAuthId(authIdHeader);
                        profile.setEmail(emailHeader);
                        profile.setPhoneNumber(phoneHeader);
                        profile.setFirstName(firstNameHeader);
                        profile.setLastName(lastNameHeader);
                        try {
                            profile.setUserId(Long.getLong(userIdHeader));
                        } catch (NumberFormatException e) {
                            return Mono.empty();
                        }
                        if (rolesHeader != null
                                && !rolesHeader.isBlank()
                                && !rolesHeader.isEmpty()) {
                            profile.setRoles(Set.of(rolesHeader.split(",")));
                        } else {
                            profile.setRoles(Set.of());//empty roles
                        }

                        return Mono.just(profile);
                    }

                    return Mono.error(new RuntimeException(ApiResponseMessages.UNAUTHENTICATED_USER)); // More specific error
                }));
    }
}
