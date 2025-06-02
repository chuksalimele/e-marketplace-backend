package com.aliwudi.marketplace.backend.common.intersevice;

import com.aliwudi.marketplace.backend.common.dto.OrderDto;
import com.aliwudi.marketplace.backend.common.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.reactivestreams.Publisher; // Import Publisher

@Service
public class OrderIntegrationService {

    private final WebClient webClient;

    public OrderIntegrationService(@Value("${order.service.url}") String orderServiceBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(orderServiceBaseUrl).build();
    }

    /**
     * Helper method for common error handling logic for Mono-returning methods.
     *
     * @param <T> The type of the Mono.
     * @param mono The Mono to apply error handling to.
     * @param contextMessage A message providing context for the error (e.g., "fetching order").
     * @param resourceIdentifier The ID or name of the resource being acted upon.
     * @param isNotFoundHandledSeparately A flag to indicate if 404 NOT_FOUND should result in Mono.empty() instead of an error.
     * @return A Mono with enhanced error handling.
     */
    private <T> Mono<T> handleOrderServiceErrors(Mono<T> mono, String contextMessage, Object resourceIdentifier, boolean isNotFoundHandledSeparately) {
        return mono
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (isNotFoundHandledSeparately && e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("Order " + resourceIdentifier + " not found in Order Service (404) during " + contextMessage + ".");
                        return Mono.empty(); // Signal not found by returning empty
                    }
                    System.err.println("WebClient response error during " + contextMessage + " for order " + resourceIdentifier + ": " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Mono.error(new ServiceUnavailableException("Order Service communication error during " + contextMessage + " for order ID " + resourceIdentifier + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    System.err.println("Connection error to Order Service during " + contextMessage + " for order " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to Order Service (connection refused/host unreachable) during " + contextMessage + " for order ID " + resourceIdentifier, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    System.err.println("No route to host for Order Service during " + contextMessage + " for order " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("No route to host for Order Service during " + contextMessage + " for order ID " + resourceIdentifier, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    System.err.println("Unknown host for Order Service during " + contextMessage + " for order " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Unknown host for Order Service during " + contextMessage + " for order ID " + resourceIdentifier, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    System.err.println("Timeout connecting to Order Service during " + contextMessage + " for order " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Order Service communication timeout during " + contextMessage + " for order ID " + resourceIdentifier, e));
                })
                .onErrorResume(Exception.class, e -> {
                    System.err.println("Failed during " + contextMessage + " for order " + resourceIdentifier + " from Order Service due to unexpected error: " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to Order Service during " + contextMessage + " for order ID " + resourceIdentifier, e));
                });
    }

    /**
     * Helper method for common error handling logic for Flux-returning methods.
     *
     * @param <T> The type of the Flux elements.
     * @param flux The Flux to apply error handling to.
     * @param contextMessage A message providing context for the error (e.g., "fetching orders").
     * @param resourceIdentifier The ID or name of the resource being acted upon.
     * @param isNotFoundHandledSeparately A flag to indicate if 404 NOT_FOUND should result in Flux.empty() instead of an error.
     * @return A Flux with enhanced error handling.
     */
    private <T> Flux<T> handleOrderServiceErrors(Flux<T> flux, String contextMessage, Object resourceIdentifier, boolean isNotFoundHandledSeparately) {
        return flux
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (isNotFoundHandledSeparately && e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("Orders for " + resourceIdentifier + " not found in Order Service (404) during " + contextMessage + ".");
                        return Flux.empty(); // Signal not found by returning empty Flux
                    }
                    System.err.println("WebClient response error during " + contextMessage + " for " + resourceIdentifier + ": " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Flux.error(new ServiceUnavailableException("Order Service communication error during " + contextMessage + " for " + resourceIdentifier + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    System.err.println("Connection error to Order Service during " + contextMessage + " for " + resourceIdentifier + ": " + e.getMessage());
                    return Flux.error(new ServiceUnavailableException("Failed to connect to Order Service (connection refused/host unreachable) during " + contextMessage + " for " + resourceIdentifier, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    System.err.println("No route to host for Order Service during " + contextMessage + " for " + resourceIdentifier + ": " + e.getMessage());
                    return Flux.error(new ServiceUnavailableException("No route to host for Order Service during " + contextMessage + " for " + resourceIdentifier, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    System.err.println("Unknown host for Order Service during " + contextMessage + " for " + resourceIdentifier + ": " + e.getMessage());
                    return Flux.error(new ServiceUnavailableException("Unknown host for Order Service during " + contextMessage + " for " + resourceIdentifier, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    System.err.println("Timeout connecting to Order Service during " + contextMessage + " for " + resourceIdentifier + ": " + e.getMessage());
                    return Flux.error(new ServiceUnavailableException("Order Service communication timeout during " + contextMessage + " for " + resourceIdentifier, e));
                })
                .onErrorResume(Exception.class, e -> {
                    System.err.println("Failed during " + contextMessage + " for " + resourceIdentifier + " from Order Service due to unexpected error: " + e.getMessage());
                    return Flux.error(new ServiceUnavailableException("Failed to connect to Order Service during " + contextMessage + " for " + resourceIdentifier, e));
                });
    }

    /**
     * Checks if an order exists by its ID.
     * This method is optimized to only check existence (HEAD request).
     * @param orderId The ID of the order to check.
     * @return Mono<Boolean> true if order exists, false otherwise.
     * Throws ServiceUnavailableException if the Order Service itself is unavailable or returns an error.
     */
    public Mono<Boolean> orderExistsById(Long orderId) {
        Mono<Boolean> responseMono = webClient.head() // Use HEAD request for efficiency
                .uri("/api/orders/{orderId}", orderId) // Adjust URI based on your Order Service API
                .retrieve()
                // Removed onStatus calls as WebClientResponseException will handle them
                .toBodilessEntity() // Discard the response body, get only headers/status
                .map(response -> response.getStatusCode() == HttpStatus.OK); // If 200 OK, order exists

        // Specific handling for 404 NOT_FOUND for existence checks
        return responseMono
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("Order " + orderId + " not found in Order Service (404 for HEAD).");
                        return Mono.just(false); // Order does not exist
                    }
                    // For other WebClientResponseExceptions, let the generic handler take over
                    return Mono.error(e);
                })
                // Apply the common error handling for other network/timeout issues
                .transform(mono -> handleOrderServiceErrors(mono, "checking order existence", orderId, false)); // Pass false as 404 is handled directly above
    }

    /**
     * Retrieves an OrderDto by order ID from the Order Service.
     * Returns Mono.empty() if the order is not found (404).
     * Throws ServiceUnavailableException for other 4xx/5xx errors or connectivity issues.
     * @param orderId The ID of the order to retrieve.
     * @return Mono<OrderDto> if order is found, Mono.empty() if not found, Mono.error() on other service errors.
     */
    public Mono<OrderDto> getOrderById(Long orderId) {
        Mono<OrderDto> responseMono = webClient.get()
                .uri("/api/orders/{orderId}", orderId) // Adjust URI based on your Order Service API
                .retrieve()
                // Removed onStatus calls as WebClientResponseException will handle them
                .bodyToMono(OrderDto.class);

        return handleOrderServiceErrors(responseMono, "fetching order", orderId, true);
    }

    /**
     * Retrieves a Flux of OrderDto by user ID from the Order Service.
     * Returns Flux.empty() if no orders are found for the user (404 or empty list).
     * Throws ServiceUnavailableException for other 4xx/5xx errors or connectivity issues.
     * @param userId The ID of the user whose orders to retrieve.
     * @return Flux<OrderDto> if orders are found, Flux.empty() if not found, Mono.error() on other service errors.
     */
    public Flux<OrderDto> getOrdersByUserId(Long userId) {
        Flux<OrderDto> responseFlux = webClient.get()
                .uri("/api/orders/user/{userId}", userId) // Adjust URI based on your Order Service API
                .retrieve()
                // Removed onStatus calls as WebClientResponseException will handle them
                .bodyToFlux(OrderDto.class); // Expecting a Flux of OrderDto

        return handleOrderServiceErrors(responseFlux, "fetching user's orders", userId, true);
    }
}