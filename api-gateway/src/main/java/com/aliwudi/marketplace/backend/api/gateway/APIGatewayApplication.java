package com.aliwudi.marketplace.backend.api.gateway;

import com.aliwudi.marketplace.backend.common.enumeration.BasicAuthHeaders;
import com.aliwudi.marketplace.backend.common.enumeration.JwtClaims;
import com.aliwudi.marketplace.backend.common.util.JwtAuthConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
// CORRECTED: Updated import for ReferrerPolicy enum based on your feedback
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;

import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.web.server.ServerWebExchange;

/**
 * Main application class for the API Gateway.
 * Enables service discovery, Spring Security for JWT validation,
 * and configures various gateway features for robustness.
 */
@SpringBootApplication
@EnableDiscoveryClient // Enables this application to act as a Eureka client
@EnableWebFluxSecurity // Enables Spring Security for reactive applications (Spring Cloud Gateway is reactive)
@EnableReactiveMethodSecurity // For @PreAuthorize etc.
public class APIGatewayApplication {
    @Value("${jwt.auth.converter.principle-attribute}")
    private String principleAttribute;
    
    @Value("${jwt.auth.converter.resource-id}")
    private String resourceId;
    
    public static void main(String[] args) {
        SpringApplication.run(APIGatewayApplication.class, args);
    }
    private Converter<Jwt, ? extends Mono<? extends AbstractAuthenticationToken>> jwtAuthConverter;
    

    @Bean
    public JwtAuthConverter getJwtAuthConverter(){
        return new JwtAuthConverter(principleAttribute, resourceId);
    }

    /**
     * Configures the Spring Security filter chain for the API Gateway.
     * This bean defines security rules, JWT validation, and header hardening.
     *
     * @param http ServerHttpSecurity to configure web security for reactive applications.
     * @return A SecurityWebFilterChain bean.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            // Disable CSRF for stateless APIs (common for API Gateways)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)//CSRF protection is disabled, which is common for stateless APIs using token-based authentication.
            // Disable HTTP Basic and Form Login to prevent unwanted login redirects
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

            // Configure OAuth2 Resource Server for JWT validation
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> 
                jwt.jwtAuthenticationConverter(jwtAuthConverter)
            ))

            // Configure authorization rules based on request paths
            .authorizeExchange(exchanges -> exchanges

                // Public routes that do NOT require authentication
                .pathMatchers(
                    "/users/**",          // Allow public endpoints
                    "/products/**",       // Allow public access to product catalog
                    "/media/**",          // Allow public access to media (e.g images and videos)
                    "/eureka/**",          // Eureka dashboard (secure this in production environments!)
                    "/actuator/**",        // Spring Boot Actuator endpoints (secure these heavily in production!)
                    "/fallback/**"         // Fallback endpoints for circuit breaker
                ).permitAll()
                         
                // All other requests require authentication (JWT must be valid)
                .anyExchange().authenticated()
            )
            // For stateless APIs, we don't need a session to store security context
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            // Configure security headers for enhanced protection
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.mode(Mode.DENY)) // Prevent clickjacking
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'")) // Basic Content Security Policy
                // Referrer-Policy: Use the enum directly
                .referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER)) 
                // HSTS: Use the simpler method for maxAge and includeSubdomains
                .hsts(hsts -> hsts.maxAge(java.time.Duration.ofSeconds(31536000)).includeSubdomains(true)) // Set maxAge using Duration
                // Cache-Control: Commented out due to persistent compilation error.
                // If needed, consider implementing via a custom WebFilter -  we have done that already see CacheControlWebFilter.java.
                // .cacheControl().disable()
                    
            );
        return http.build();
    }

    /**
     * Configures Cross-Origin Resource Sharing (CORS) for the API Gateway.
     * This is essential for web applications (e.g., React, Angular) running on different origins
     * to be able to make requests to your API Gateway.
     *
     * @return A CorsWebFilter bean.
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        // IMPORTANT: In production, replace "*" with specific allowed origins (your frontend URLs)
        corsConfig.addAllowedOrigin("*"); // Allow all origins for development, restrict for production
        corsConfig.addAllowedMethod("*"); // Allow all HTTP methods (GET, POST, PUT, DELETE, etc.)
        corsConfig.addAllowedHeader("*"); // Allow all headers
        corsConfig.setAllowCredentials(true); // Allow sending credentials (cookies, authentication headers)
        corsConfig.setMaxAge(3600L); // How long the pre-flight response can be cached by clients (1 hour)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig); // Apply this CORS configuration to all paths
        return new CorsWebFilter(source);
    }

    /**
     * Defines a KeyResolver bean for rate limiting based on the user's ID.
     * This is preferred over IP-based rate limiting for authenticated users.
     * It extracts the user ID from the authenticated principal (assuming JWT 'sub' claim).
     *
     * @return A KeyResolver bean.
     */
    @Bean
    @Primary //Let Spring pick this one
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> {
                    // Extract user ID from JWT if available
                    if (principal instanceof Authentication && ((Authentication) principal).getPrincipal() instanceof Jwt) {
                        Jwt jwt = (Jwt) ((Authentication) principal).getPrincipal();
                        return jwt.getClaimAsString("sub"); // 'sub' claim is common for user ID
                    }
                    // Fallback to username for other principal types, or "anonymous"
                    return principal.getName();
                })
                .defaultIfEmpty("anonymous"); // For unauthenticated requests
    }

    /**
     * Defines a KeyResolver bean for rate limiting based on the client's IP address.
     * This is useful for unauthenticated requests or as a fallback.
     *
     * @return A KeyResolver bean.
     */
    @Bean
    KeyResolver ipAddressResolver() {
        return exchange -> Mono.just(
            Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(addr -> addr.getAddress().getHostAddress())
                    .orElse("unknown")
        );
    }

    /**
     * A custom GlobalFilter to propagate the authenticated user's ID (and potentially roles)
     * from the SecurityContextHolder into custom HTTP headers (e.g., X-User-AuthID)
     * before forwarding the request to downstream microservices.
     * Downstream services can then trust these headers for user identity.
     */
    @Component
    public static class CustomAuthenticationHeaderFilter implements GlobalFilter, Ordered {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            return ReactiveSecurityContextHolder.getContext()
                .map(context -> {
                    Authentication authentication = context.getAuthentication();
                    if (authentication != null && authentication.isAuthenticated()) {
                        String authId = null;
                        String userId = null;
                        String email = null;
                        String phone = null;
                        String firstName = null;
                        String lastName = null;
                        String rolesStr = null;
                        // Extract user ID from JWT principal
                        if (authentication.getPrincipal() instanceof Jwt jwt) {
                            authId = jwt.getClaimAsString(JwtClaims.sub.getClaimName()); // Assuming 'sub' claim holds the user auth ID
                            userId = jwt.getClaimAsString(JwtClaims.userId.getClaimName());
                            email = jwt.getClaimAsString(JwtClaims.email.getClaimName());
                            phone = jwt.getClaimAsString(JwtClaims.phone.getClaimName());
                            firstName = jwt.getClaimAsString(JwtClaims.firstName.getClaimName());
                            lastName = jwt.getClaimAsString(JwtClaims.lastName.getClaimName());
                            
                            rolesStr = authentication.getAuthorities().stream()
                                    .map(a -> a.getAuthority())
                                    .collect(Collectors.joining(","));
                        }

                        if (authId != null) {
                            ServerHttpRequest request = exchange.getRequest().mutate()
                                .header(BasicAuthHeaders.X_USER_AUTH_ID.getHeaderName(), authId) // Propagate user auth ID
                                .header(BasicAuthHeaders.X_USER_ID.getHeaderName(), userId) // Propagate user ID - registration id on the database
                                .header(BasicAuthHeaders.X_USER_EMAIL.getHeaderName(), email) // Propagate user email
                                .header(BasicAuthHeaders.X_USER_PHONE.getHeaderName(), phone) // Propagate user phone number
                                .header(BasicAuthHeaders.X_USER_FIRST_NAME.getHeaderName(), phone) // Propagate user first name number
                                .header(BasicAuthHeaders.X_USER_LAST_NAME.getHeaderName(), phone) // Propagate user last name number
                                .header(BasicAuthHeaders.X_USER_ROLES.getHeaderName(), rolesStr) // Propagate user email
                                .build();
                            return exchange.mutate().request(request).build();
                        }
                    }
                    return exchange; // No authenticated user or no user ID to add
                })
                .defaultIfEmpty(exchange) // Handle case where security context is empty
                .flatMap(chain::filter); // Continue the filter chain
        }

        @Override
        public int getOrder() {
            // Run this filter after Spring Security filters have processed the authentication
            // and before other routing filters.
            return Ordered.HIGHEST_PRECEDENCE + 1;
        }
    }
}