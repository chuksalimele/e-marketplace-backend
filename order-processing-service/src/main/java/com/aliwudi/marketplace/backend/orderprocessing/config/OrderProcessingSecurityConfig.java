package com.aliwudi.marketplace.backend.orderprocessing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // For custom filter
import org.springframework.web.filter.OncePerRequestFilter; // For custom filter
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collections; // For Collections.singletonList

/**
 * Security configuration for the Order Processing Service.
 * Configures OAuth2 Resource Server to accept JWTs and a custom filter
 * to extract user ID from X-User-ID header propagated by the API Gateway.
 */
@Configuration
@EnableWebSecurity // Enables Spring Security for this microservice
public class OrderProcessingSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless APIs
            .authorizeHttpRequests(authorize -> authorize
                // Define which endpoints require authentication
                .requestMatchers("/api/cart/**").authenticated() // All cart endpoints require authentication
                // Add other public endpoints here if any
                .anyRequest().authenticated() // All other requests also require authentication by default
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Stateless sessions
            // Add the custom filter to read X-User-ID header
            .addFilterBefore(customUserIdHeaderFilter(), UsernamePasswordAuthenticationFilter.class)
            // Configure OAuth2 Resource Server for JWTs (even if not re-validating, it sets up the context)
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())); // Default JWT processing

        return http.build();
    }

    /**
     * Custom filter to extract the X-User-ID header and populate the SecurityContextHolder.
     * This filter runs before Spring Security's main authentication filters.
     */
    @Bean
    public OncePerRequestFilter customUserIdHeaderFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                String userIdHeader = request.getHeader("X-User-ID"); // Get the user ID from the header

                if (userIdHeader != null && !userIdHeader.isEmpty()) {
                    try {
                        Long userId = Long.parseLong(userIdHeader);
                        // Create an Authentication object.
                        // For simplicity, we'll use UsernamePasswordAuthenticationToken.
                        // The principal can be the userId itself. No credentials needed as it's pre-authenticated.
                        // We can add a dummy authority like "ROLE_USER" or "ROLE_AUTHENTICATED"
                        Authentication authentication = new UsernamePasswordAuthenticationToken(
                                userId, // Principal (the user ID)
                                null,   // Credentials (not needed, as it's already authenticated by Gateway)
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_AUTHENTICATED")) // Basic role
                        );
                        // Set the Authentication object in the SecurityContextHolder
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid X-User-ID header format: " + userIdHeader);
                        // Optionally, you could return an error response here if invalid ID is critical
                    }
                }

                filterChain.doFilter(request, response); // Continue the filter chain
            }
        };
    }

    /**
     * Configures a JwtAuthenticationConverter to extract authorities (roles) from JWT claims.
     * This is useful if your JWTs contain 'scope' or 'roles' claims.
     * Even if you primarily use X-User-ID, this is good practice for JWT processing.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Optionally, configure the claim name where roles are found, e.g., "roles" or "scope"
        // grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        // grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_"); // Add a prefix if needed

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }
}