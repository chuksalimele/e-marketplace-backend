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
     * Retrieves a UserDto by user ID from the User Service.
     * Returns Mono.empty() if the user is not found (404).
     * Throws ServiceUnavailableException for other 4xx/5xx errors or connectivity issues.
     * @param userId The ID of the user to retrieve.
     * @return Mono<UserDto> if user is found, Mono.empty() if not found, Mono.error() on other service errors.
     */
    public Mono<UserDto> getUserDtoById(Long userId) {
        return webClient.get()
                .uri("/api/users/{userId}", userId)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() && status != HttpStatus.NOT_FOUND,
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(new ServiceUnavailableException(
                                "User Service returned client error for user ID " + userId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))))
                .onStatus(status -> status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(new ServiceUnavailableException(
                                "User Service returned server error for user ID " + userId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))))
                .bodyToMono(UserDto.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    // This catches all WebClientResponseException subclasses (e.g., HttpClientErrorException, HttpServerErrorException)
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("User " + userId + " not found in User Service (404).");
                        return Mono.empty(); // Signal not found by returning empty
                    }
                    System.err.println("WebClient response error for user " + userId + ": " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Mono.error(new ServiceUnavailableException("User Service communication error for user ID " + userId + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    // Catches connection refused, host unreachable
                    System.err.println("Connection error to User Service for user " + userId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to User Service (connection refused/host unreachable) for user ID " + userId, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    // Catches network routing issues
                    System.err.println("No route to host for User Service for user " + userId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("No route to host for User Service for user ID " + userId, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    // Catches DNS resolution failures
                    System.err.println("Unknown host for User Service for user " + userId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Unknown host for User Service for user ID " + userId, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    // Catches general read timeouts, potentially from underlying HTTP client
                    System.err.println("Timeout connecting to User Service for user " + userId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("User Service communication timeout for user ID " + userId, e));
                })
                .onErrorResume(Exception.class, e -> {
                    // Catch any other general exceptions (e.g., network issues, unexpected errors)
                    System.err.println("Failed to fetch user " + userId + " from User Service due to unexpected error: " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to User Service for user ID " + userId, e));
                });
    }

    /**
     * Checks if a user exists by their ID.
     * This method is optimized to only check existence (HEAD request).
     * @param userId The ID of the user to check.
     * @return Mono<Boolean> true if user exists, false otherwise.
     * Throws ServiceUnavailableException if the User Service itself is unavailable or returns an error.
     */
    public Mono<Boolean> userExistsById(Long userId) {
        return webClient.head()
                .uri("/api/users/{userId}", userId)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() && status != HttpStatus.NOT_FOUND,
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(new ServiceUnavailableException(
                                "User Service returned client error for user ID " + userId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))))
                .onStatus(status -> status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(new ServiceUnavailableException(
                                "User Service returned server error for user ID " + userId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))))
                .toBodilessEntity()
                .map(response -> response.getStatusCode() == HttpStatus.OK)
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("User " + userId + " not found in User Service (404 for HEAD).");
                        return Mono.just(false);
                    }
                    System.err.println("WebClient response error during user existence check for ID " + userId + ": " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Mono.error(new ServiceUnavailableException("User Service communication error for user ID " + userId + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    System.err.println("Connection error to User Service for user " + userId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to User Service (connection refused/host unreachable) for user ID " + userId, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    System.err.println("No route to host for User Service for user " + userId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("No route to host for User Service for user ID " + userId, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    System.err.println("Unknown host for User Service for user " + userId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Unknown host for User Service for user ID " + userId, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    System.err.println("Timeout connecting to User Service for user " + userId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("User Service communication timeout for user ID " + userId, e));
                })
                .onErrorResume(Exception.class, e -> {
                    System.err.println("Failed to check existence of user " + userId + " from User Service due to unexpected error: " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to User Service for user ID " + userId, e));
                });
    }

    /**
     * Checks if a user exists by their username.
     * Assumes an endpoint like /api/users/exists/username/{username} on the User Service.
     * @param username The username of the user to check.
     * @return Mono<Boolean> true if user exists, false otherwise.
     * Throws ServiceUnavailableException if the User Service itself is unavailable or returns an error.
     */
    public Mono<Boolean> userExistsByUsername(String username) {
        return webClient.get()
                .uri("/api/users/exists/username/{username}", username)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() && status != HttpStatus.NOT_FOUND,
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(new ServiceUnavailableException(
                                "User Service returned client error for username " + username + " (Status: " + clientResponse.statusCode() + "): " + errorBody))))
                .onStatus(status -> status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(new ServiceUnavailableException(
                                "User Service returned server error for username " + username + " (Status: " + clientResponse.statusCode() + "): " + errorBody))))
                .bodyToMono(Boolean.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("User with username " + username + " not found in User Service (404).");
                        return Mono.just(false);
                    }
                    System.err.println("WebClient response error during user existence check for username " + username + ": " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Mono.error(new ServiceUnavailableException("User Service communication error for username " + username + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    System.err.println("Connection error to User Service for username " + username + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to User Service (connection refused/host unreachable) for username " + username, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    System.err.println("No route to host for User Service for username " + username + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("No route to host for User Service for username " + username, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    System.err.println("Unknown host for User Service for username " + username + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Unknown host for User Service for username " + username, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    System.err.println("Timeout connecting to User Service for username " + username + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("User Service communication timeout for username " + username, e));
                })
                .onErrorResume(Exception.class, e -> {
                    System.err.println("Failed to check existence of user " + username + " from User Service due to unexpected error: " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to User Service for username " + username, e));
                });
    }
}