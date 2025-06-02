package com.aliwudi.marketplace.backend.common.intersevice;

import com.aliwudi.marketplace.backend.common.dto.UserDto;
import com.aliwudi.marketplace.backend.common.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException; // Corrected: Import WebClientResponseException
import reactor.core.publisher.Mono;
import java.net.ConnectException; // For connection errors
import java.net.NoRouteToHostException; // For network routing issues
import java.net.UnknownHostException; // For DNS resolution issues
import java.util.concurrent.TimeoutException; // For timeouts that might not be WebClient specific

@Service
public class UserIntegrationService {

    private final WebClient webClient;

    public UserIntegrationService(@Value("${user.service.url}") String userServiceBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(userServiceBaseUrl).build();
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
                    // This catches all WebClientResponseException subclasses (e.g., HttpClientErrorException, HttpServerErrorException)
                    if (isNotFoundHandledSeparately && e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("User " + resourceIdentifier + " not found in User Service (404) during " + contextMessage + ".");
                        return Mono.empty(); // Signal not found by returning empty
                    }
                    // Handle other 4xx and all 5xx errors as ServiceUnavailableException
                    System.err.println("WebClient response error during " + contextMessage + " for user " + resourceIdentifier + ": " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Mono.error(new ServiceUnavailableException("User Service communication error during " + contextMessage + " for user ID " + resourceIdentifier + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    // Catches connection refused, host unreachable
                    System.err.println("Connection error to User Service during " + contextMessage + " for user " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to User Service (connection refused/host unreachable) during " + contextMessage + " for user ID " + resourceIdentifier, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    // Catches network routing issues
                    System.err.println("No route to host for User Service during " + contextMessage + " for user " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("No route to host for User Service during " + contextMessage + " for user ID " + resourceIdentifier, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    // Catches DNS resolution failures
                    System.err.println("Unknown host for User Service during " + contextMessage + " for user " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Unknown host for User Service during " + contextMessage + " for user ID " + resourceIdentifier, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    // Catches general read timeouts, potentially from underlying HTTP client
                    System.err.println("Timeout connecting to User Service during " + contextMessage + " for user " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("User Service communication timeout during " + contextMessage + " for user ID " + resourceIdentifier, e));
                })
                .onErrorResume(Exception.class, e -> {
                    // Catch any other general exceptions (e.g., network issues, unexpected errors)
                    System.err.println("Failed during " + contextMessage + " for user " + resourceIdentifier + " from User Service due to unexpected error: " + e.getMessage());
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
    public Mono<UserDto> getUserDtoById(Long userId) {
        Mono<UserDto> responseMono = webClient.get()
                .uri("/api/users/{userId}", userId)
                .retrieve()
                // Removed explicit onStatus for 4xx/5xx as common error handler will catch WebClientResponseException
                .bodyToMono(UserDto.class);

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
                // Removed explicit onStatus for 4xx/5xx as common error handler will catch WebClientResponseException
                .toBodilessEntity()
                .map(response -> response.getStatusCode() == HttpStatus.OK);

        // For userExistsById, 404 should result in Mono.just(false) directly from WebClientResponseException
        return responseMono
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("User " + userId + " not found in User Service (404 for HEAD).");
                        return Mono.just(false);
                    }
                    System.err.println("WebClient response error during user existence check for ID " + userId + ": " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Mono.error(new ServiceUnavailableException("User Service communication error for user ID " + userId + ": " + e.getMessage(), e));
                })
                // Apply the common error handling for other network/timeout issues
                .transform(mono -> handleUserServiceErrors(mono, "checking user existence", userId, false)); // Pass false because 404 is handled directly above
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
                // Removed explicit onStatus for 4xx/5xx as common error handler will catch WebClientResponseException
                .bodyToMono(Boolean.class);

        return handleUserServiceErrors(responseMono, "checking user existence by username", username, true);
    }
}