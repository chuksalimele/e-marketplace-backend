// JwtUtils.java
package com.aliwudi.marketplace.backend.user.security.jwt;

import com.aliwudi.marketplace.backend.user.service.UserDetailsImpl; // Our custom UserDetails implementation
import io.jsonwebtoken.*; // Core JWT classes from jjwt library (e.g., Jwts, Claims, SignatureAlgorithm)
import io.jsonwebtoken.io.Decoders; // Utility for Base64 decoding
import io.jsonwebtoken.security.Keys; // Utility for generating secure keys
import org.slf4j.Logger; // For logging messages
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; // To read properties from application.properties
import org.springframework.security.core.Authentication; // Spring Security's authentication object
import org.springframework.stereotype.Component; // Marks this class as a Spring component

import java.security.Key; // Java's security Key interface
import java.util.Date; // For setting token creation and expiration dates

@Component // Tells Spring to manage this class as a reusable component (bean)
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class); // Logger for debugging and error messages

    // Injects the JWT secret key from application.properties
    @Value("${marketplace.app.jwtSecret}")
    private String jwtSecret;

    // Injects the JWT expiration time (in milliseconds) from application.properties
    @Value("${marketplace.app.jwtExpirationMs}")
    private int jwtExpirationMs;

    /**
     * Generates a JWT token for an authenticated user.
     * @param authentication The Spring Security Authentication object, containing user details.
     * @return The generated JWT as a String.
     */
    public String generateJwtToken(Authentication authentication) {
        // Get our custom UserDetails from the authentication object
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();

        return Jwts.builder()
                .setSubject((userPrincipal.getUsername())) // Set the token's subject (usually the username)
                .setIssuedAt(new Date()) // Set the token's creation timestamp
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs)) // Set the token's expiration timestamp
                .signWith(key(), SignatureAlgorithm.HS256) // Sign the token using our secret key and HS256 algorithm
                .compact(); // Builds and compacts the JWT into its final string representation
    }

    /**
     * Generates a signing key from the base64-encoded secret.
     * @return A secure Key object for JWT signing.
     */
    private Key key() {
        // Decode the base64-encoded secret from application.properties to create a secure key
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    /**
     * Validates a given JWT token.
     * @param authToken The JWT string to validate.
     * @return true if the token is valid, false otherwise.
     */
    public boolean validateJwtToken(String authToken) {
        try {
            // Parse and validate the token using the secret key
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
            return true; // If parsing is successful, token is valid
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false; // Any exception means the token is invalid
    }

    /**
     * Extracts the username (subject) from a JWT token.
     * @param authToken The JWT string.
     * @return The username contained in the token.
     */
    public String getUserNameFromJwtToken(String authToken) {
        // Parse the token, get its body (claims), and extract the subject (username)
        return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken).getBody().getSubject();
    }
}