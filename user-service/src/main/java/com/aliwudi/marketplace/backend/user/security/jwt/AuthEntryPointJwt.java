// AuthEntryPointJwt.java
package com.marketplace.emarketplacebackend.security.jwt;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException; // Spring Security's authentication exception
import org.springframework.security.web.AuthenticationEntryPoint; // Interface for handling authentication failures
import org.springframework.stereotype.Component; // Marks this as a Spring component

import java.io.IOException;

@Component // Marks this as a Spring component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(AuthEntryPointJwt.class);

    /**
     * This method is called when an unauthenticated user tries to access a protected resource.
     * It sends a 401 Unauthorized response to the client.
     * @param request The HTTP request.
     * @param response The HTTP response.
     * @param authException The authentication exception that occurred.
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        logger.error("Unauthorized error: {}", authException.getMessage());
        // Send a 401 Unauthorized status code with a custom error message
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Error: Unauthorized - " + authException.getMessage());
    }
}