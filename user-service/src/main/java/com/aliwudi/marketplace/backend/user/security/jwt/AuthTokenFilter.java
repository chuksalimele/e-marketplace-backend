// AuthTokenFilter.java
package com.aliwudi.marketplace.backend.user.security.jwt;

import com.aliwudi.marketplace.backend.user.service.UserDetailsServiceImpl; // Our custom service to load user details
import jakarta.servlet.FilterChain; // For chaining filters
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired; // For dependency injection
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // For creating an authentication object
import org.springframework.security.core.context.SecurityContextHolder; // To set the authenticated user in the security context
import org.springframework.security.core.userdetails.UserDetails; // Spring Security's user details interface
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource; // To build authentication details from the request
import org.springframework.util.StringUtils; // Spring utility for String operations
import org.springframework.web.filter.OncePerRequestFilter; // Ensures this filter runs only once per request

import java.io.IOException;

// This filter is executed once per request to validate JWT tokens.
public class AuthTokenFilter extends OncePerRequestFilter {

    @Autowired // Injects our JwtUtils bean
    private JwtUtils jwtUtils;

    @Autowired // Injects our UserDetailsServiceImpl bean
    private UserDetailsServiceImpl userDetailsService;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    /**
     * This is the core method of the filter that performs JWT validation.
     * @param request The incoming HTTP request.
     * @param response The HTTP response.
     * @param filterChain The filter chain to continue processing.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 1. Extract the JWT from the "Authorization" header
            String jwt = parseJwt(request);

            // 2. If a JWT is found and it's valid:
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                // Get the username from the validated token
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                // Load user details (username, roles, etc.) using our UserDetailsService
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Create an authentication object using the loaded user details
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null, // Credentials are null because the token itself is the credential here
                                userDetails.getAuthorities()); // User's roles/authorities

                // Set additional details about the authentication request (e.g., remote IP address)
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set the authentication object in Spring Security's context.
                // This tells Spring Security that the current user is authenticated for this request.
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e.getMessage());
        }

        // Continue to the next filter in the chain (or the controller if this is the last filter)
        filterChain.doFilter(request, response);
    }

    /**
     * Helper method to extract the JWT string from the "Authorization" header.
     * The header typically looks like: "Authorization: Bearer <JWT_TOKEN>"
     * @param request The HTTP request.
     * @return The JWT string, or null if not found or not in "Bearer" format.
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        // Check if the header exists and starts with "Bearer "
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); // Return the token part after "Bearer "
        }
        return null;
    }
}