package com.aliwudi.marketplace.backend.user.security.jwt;

import com.aliwudi.marketplace.backend.user.service.UserDetailsServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder; // NEW: Reactive context holder
import org.springframework.security.core.context.SecurityContext; // NEW: SecurityContext for reactive
import org.springframework.security.core.context.SecurityContextImpl; // NEW: Implementation for SecurityContext
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.WebFilter; // NEW: WebFilter for reactive applications
import org.springframework.security.web.server.authentication.WebFilterServerAuthenticationConverter; // Consider for complex cases
import org.springframework.stereotype.Component; // Mark as component for auto-detection
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange; // NEW: Reactive equivalent of HttpServletRequest/Response
import org.springframework.web.server.WebFilterChain; // NEW: Reactive filter chain
import reactor.core.publisher.Mono; // NEW: Import Mono

// This filter is executed once per request to validate JWT tokens in a reactive context.
@Component // Mark as a Spring component for auto-detection
public class AuthTokenFilter implements WebFilter { // Implements WebFilter for reactive

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    // Use constructor injection as it's generally preferred
    @Autowired
    public AuthTokenFilter(JwtUtils jwtUtils, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    /**
     * This is the core method of the reactive filter that performs JWT validation.
     * It processes the ServerWebExchange (reactive request/response) and returns a Mono<Void>.
     *
     * @param exchange The incoming reactive HTTP request/response exchange.
     * @param chain The reactive filter chain to continue processing.
     * @return A Mono<Void> indicating when the filter processing is complete.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.justOrEmpty(parseJwt(exchange)) // 1. Extract the JWT from the "Authorization" header
                .filter(jwt -> jwtUtils.validateJwtToken(jwt)) // 2. If a JWT is found and it's valid:
                .flatMap(jwt -> {
                    String username = jwtUtils.getUserNameFromJwtToken(jwt);
                    // Load user details reactively
                    return userDetailsService.findByUsername(username) // This returns Mono<UserDetails>
                            .flatMap(userDetails -> {
                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                userDetails,
                                                null,
                                                userDetails.getAuthorities());

                                // Store authentication in reactive security context
                                // This is crucial for subsequent security checks in reactive applications
                                SecurityContext securityContext = new SecurityContextImpl(authentication);
                                return chain.filter(exchange)
                                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                            })
                            .onErrorResume(e -> {
                                logger.error("Cannot set user authentication: {}", e.getMessage());
                                // Do not propagate error to client directly if only for auth failure,
                                // just continue filter chain without setting context.
                                return chain.filter(exchange);
                            });
                })
                .switchIfEmpty(chain.filter(exchange)); // If no JWT or invalid, just continue the chain
    }

    /**
     * Helper method to extract the JWT string from the "Authorization" header.
     * @param exchange The ServerWebExchange.
     * @return The JWT string wrapped in an Optional/Mono.
     */
    private String parseJwt(ServerWebExchange exchange) {
        String headerAuth = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}