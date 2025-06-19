package com.aliwudi.marketplace.backend.common.interservice;

import static com.aliwudi.marketplace.backend.common.constants.ApiPaths.*;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.exception.ServiceUnavailableException;
import com.aliwudi.marketplace.backend.common.filter.JwtPropagationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger; // For logging
import org.slf4j.LoggerFactory; // For logging

@Service
public class UserIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(UserIntegrationService.class); // Add Logger

    private final WebClient webClient;
    private final String path = "lb://user-service"+USER_CONTROLLER_BASE;

    // Constructor injection for WebClient.Builder and JwtPropagationFilter
    public UserIntegrationService(WebClient.Builder webClientBuilder,
                                     JwtPropagationFilter jwtPropagationFilter) { // INJECT THE FILTER
        // Build WebClient instance. The base URL uses the Eureka service ID.
        // 'lb://' prefix indicates client-side load balancing via Eureka.        
        this.webClient = WebClient.builder()
                .baseUrl(path)
                .filter(jwtPropagationFilter) // APPLY THE FILTER HERE!
                .build();
    }

    /**
     * Helper method for common error handling logic across User Service calls.
     *
     * @param <T> The type of the Mono.
     * @param mono The Mono to apply error handling to.
     * @param contextMessage A message providing context for the error (e.g., "fetching user").
     * @param resourceIdentifier The ID or name of the resource being acted upon.
     * @param isNotFoundHandledSeparately A flag to indicate if 404 NOT_FOUND should result in Mono.empty() instead of an error.
     * @return A Mono with enhanced error handling.
     */
    private <T> Mono<T> handleUserServiceErrors(Mono<T> mono, String contextMessage, Object resourceIdentifier, boolean isNotFoundHandledSeparately) {
        return mono
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (isNotFoundHandledSeparately && e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        log.info("User {} not found in User Service (404) during {}.", resourceIdentifier, contextMessage);
                        return Mono.empty(); // Signal not found by returning empty
                    }
                    log.error("WebClient response error during {} for user {}: {} (Status: {})", contextMessage, resourceIdentifier, e.getMessage(), e.getStatusCode(), e);
                    return Mono.error(new ServiceUnavailableException("User Service communication error during " + contextMessage + " for user ID " + resourceIdentifier + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    log.error("Connection error to User Service during {} for user {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Mono.error(new ServiceUnavailableException("Failed to connect to User Service (connection refused/host unreachable) during " + contextMessage + " for user ID " + resourceIdentifier, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    log.error("No route to host for User Service during {} for user {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Mono.error(new ServiceUnavailableException("No route to host for User Service during " + contextMessage + " for user ID " + resourceIdentifier, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    log.error("Unknown host for User Service during {} for user {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Mono.error(new ServiceUnavailableException("Unknown host for User Service during " + contextMessage + " for user ID " + resourceIdentifier, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    log.error("Timeout connecting to User Service during {} for user {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Mono.error(new ServiceUnavailableException("User Service communication timeout during " + contextMessage + " for user ID " + resourceIdentifier, e));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed during {} for user {} from User Service due to unexpected error: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Mono.error(new ServiceUnavailableException("Failed to connect to User Service during " + contextMessage + " for user ID " + resourceIdentifier, e));
                });
    }

    /**
     * Retrieves a UserDto by user ID from the User Service.
     * Returns Mono.empty() if the user is not found (404).
     * Throws ServiceUnavailableException for other 4xx/5xx errors or connectivity issues.
     * @param userId The ID of the user to retrieve.
     * @return Mono<UserDto> if user is found, Mono.empty() if not found, Mono.error() on other service errors.
     */
    public Mono<User> getUserById(Long userId) {
        Mono<User> responseMono = webClient.get()
                .uri(USER_GET_BY_ID, userId)
                .retrieve()
                .bodyToMono(User.class);

        return handleUserServiceErrors(responseMono, "fetching user", userId, true);
    }

    /**
     * Checks if a user exists by their user ID.
     * @param userId The user ID of the user to check.
     * @return Mono<Boolean> true if user exists, false otherwise.
     * Throws ServiceUnavailableException if the User Service itself is unavailable or returns an error.
     */
    public Mono<Boolean> userExistsByUserId(Long userId) {
        Mono<Boolean> responseMono = webClient.get()
                .uri(USER_EXISTS_BY_USER_ID, userId)
                .retrieve()
                .bodyToMono(Boolean.class);

        return handleUserServiceErrors(responseMono, "checking user existence by user id", userId, true);
    }
    /**
     * Checks if a user exists by their auth ID.
     * @param authId The auth ID of the user to check.
     * @return Mono<Boolean> true if user exists, false otherwise.
     * Throws ServiceUnavailableException if the User Service itself is unavailable or returns an error.
     */
    public Mono<Boolean> userExistsByAuthId(String authId) {
        Mono<Boolean> responseMono = webClient.get()
                .uri(USER_EXISTS_BY_AUTH_ID, authId)
                .retrieve()
                .bodyToMono(Boolean.class);

        return handleUserServiceErrors(responseMono, "checking user existence by user id", authId, true);
    }
    
    /**
     * Checks if a user exists by their username.
     * @param email The email of the user to check.
     * @return Mono<Boolean> true if user exists, false otherwise.
     * Throws ServiceUnavailableException if the User Service itself is unavailable or returns an error.
     */
    public Mono<Boolean> userExistsByEmail(String email) {
        Mono<Boolean> responseMono = webClient.get()
                .uri(USER_EXISTS_BY_EMAIL, email)
                .retrieve()
                .bodyToMono(Boolean.class);

        return handleUserServiceErrors(responseMono, "checking user existence by email", email, true);
    }
    
    /**
     * Checks if a user exists by their username.
     * @param username The username of the user to check.
     * @return Mono<Boolean> true if user exists, false otherwise.
     * Throws ServiceUnavailableException if the User Service itself is unavailable or returns an error.
     */
    public Mono<Boolean> userExistsByUsername(String username) {
        Mono<Boolean> responseMono = webClient.get()
                .uri(USER_EXISTS_BY_USERNAME, username)
                .retrieve()
                .bodyToMono(Boolean.class);

        return handleUserServiceErrors(responseMono, "checking user existence by username", username, true);
    }    
}