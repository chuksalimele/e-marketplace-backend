package com.aliwudi.marketplace.backend.common.interservice;

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

    // Inject the JwtPropagationFilter
    public UserIntegrationService(@Value("${user.service.url}") String userServiceBaseUrl,
                                  JwtPropagationFilter jwtPropagationFilter) { // INJECT THE FILTER
        this.webClient = WebClient.builder()
                .baseUrl(userServiceBaseUrl)
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
                .uri("/api/users/{userId}", userId)
                .retrieve()
                .bodyToMono(User.class);

        return handleUserServiceErrors(responseMono, "fetching user", userId, true);
    }

    /**
     * Checks if a user exists by their ID.
     * This method is optimized to only check existence (HEAD request).
     * @param userId The ID of the user to check.
     * @return Mono<Boolean> true if user exists, false otherwise.
     * Throws ServiceUnavailableException if the User Service itself is unavailable or returns an error.
     */
    public Mono<Boolean> userExistsById(Long userId) {
        Mono<Boolean> responseMono = webClient.head()
                .uri("/api/users/{userId}", userId)
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode() == HttpStatus.OK)
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        log.info("User {} not found in User Service (404 for HEAD).", userId);
                        return Mono.just(false);
                    }
                    log.error("WebClient response error during user existence check for ID {}: {} (Status: {})", userId, e.getMessage(), e.getStatusCode(), e);
                    return Mono.error(new ServiceUnavailableException("User Service communication error for user ID " + userId + ": " + e.getMessage(), e));
                });

        return responseMono
                .transform(mono -> handleUserServiceErrors(mono, "checking user existence", userId, false));
    }

    /**
     * Checks if a user exists by their username.
     * Assumes an endpoint like /api/users/exists/username/{username} on the User Service.
     * @param username The username of the user to check.
     * @return Mono<Boolean> true if user exists, false otherwise.
     * Throws ServiceUnavailableException if the User Service itself is unavailable or returns an error.
     */
    public Mono<Boolean> userExistsByUsername(String username) {
        Mono<Boolean> responseMono = webClient.get()
                .uri("/api/users/exists/username/{username}", username)
                .retrieve()
                .bodyToMono(Boolean.class);

        return handleUserServiceErrors(responseMono, "checking user existence by username", username, true);
    }
}