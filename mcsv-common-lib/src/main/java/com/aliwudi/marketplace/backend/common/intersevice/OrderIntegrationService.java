package com.aliwudi.marketplace.backend.common.intersevice;

import com.aliwudi.marketplace.backend.common.dto.OrderDto;
import com.aliwudi.marketplace.backend.common.exception.ServiceUnavailableException; // NEW: Import custom exception
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode; // Import HttpStatusCode
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux; // NEW: Import Flux
import reactor.core.publisher.Mono;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

@Service
public class OrderIntegrationService {

    private final WebClient webClient;

    // Assuming your Order Service URL is configured in application.properties/yml
    public OrderIntegrationService(@Value("${order.service.url}") String orderServiceBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(orderServiceBaseUrl).build();
    }

    /**
     * Checks if an order exists by its ID.
     * This method is optimized to only check existence (HEAD request).
     * @param orderId The ID of the order to check.
     * @return Mono<Boolean> true if order exists, false otherwise.
     * Throws ServiceUnavailableException if the Order Service itself is unavailable or returns an error.
     */
    public Mono<Boolean> orderExistsById(String orderId) {
        return webClient.head() // Use HEAD request for efficiency
                .uri("/api/orders/{orderId}", orderId) // Adjust URI based on your Order Service API
                .retrieve()
                .onStatus(status -> status.is4xxClientError() && status != HttpStatus.NOT_FOUND,
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .map(errorBody -> new ServiceUnavailableException(
                            "Order Service returned client error for order ID " + orderId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))
                        .flatMap(Mono::error))
                .onStatus(status -> status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .map(errorBody -> new ServiceUnavailableException(
                            "Order Service returned server error for order ID " + orderId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))
                        .flatMap(Mono::error))
                .toBodilessEntity() // Discard the response body, get only headers/status
                .map(response -> response.getStatusCode() == HttpStatus.OK) // If 200 OK, order exists
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("Order " + orderId + " not found in Order Service (404 for HEAD).");
                        return Mono.just(false); // Order does not exist
                    }
                    System.err.println("WebClient response error during order existence check for ID " + orderId + ": " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Mono.error(new ServiceUnavailableException("Order Service communication error for order ID " + orderId + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    System.err.println("Connection error to Order Service for order " + orderId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to Order Service (connection refused/host unreachable) for order ID " + orderId, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    System.err.println("No route to host for Order Service for order " + orderId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("No route to host for Order Service for order ID " + orderId, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    System.err.println("Unknown host for Order Service for order " + orderId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Unknown host for Order Service for order ID " + orderId, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    System.err.println("Timeout connecting to Order Service for order " + orderId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Order Service communication timeout for order ID " + orderId, e));
                })
                .onErrorResume(Exception.class, e -> {
                    System.err.println("Failed to check existence of order " + orderId + " from Order Service due to unexpected error: " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to Order Service for order ID " + orderId, e));
                });
    }

    /**
     * Retrieves an OrderDto by order ID from the Order Service.
     * Returns Mono.empty() if the order is not found (404).
     * Throws ServiceUnavailableException for other 4xx/5xx errors or connectivity issues.
     * @param orderId The ID of the order to retrieve.
     * @return Mono<OrderDto> if order is found, Mono.empty() if not found, Mono.error() on other service errors.
     */
    public Mono<OrderDto> getOrderById(String orderId) {
        return webClient.get()
                .uri("/api/orders/{orderId}", orderId) // Adjust URI based on your Order Service API
                .retrieve()
                .onStatus(status -> status.is4xxClientError() && status != HttpStatus.NOT_FOUND,
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .map(errorBody -> new ServiceUnavailableException(
                            "Order Service returned client error for order ID " + orderId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))
                        .flatMap(Mono::error))
                .onStatus(status -> status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .map(errorBody -> new ServiceUnavailableException(
                            "Order Service returned server error for order ID " + orderId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))
                        .flatMap(Mono::error))
                .bodyToMono(OrderDto.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("Order " + orderId + " not found in Order Service (404).");
                        return Mono.empty(); // Signal not found by returning empty
                    }
                    System.err.println("WebClient response error for order " + orderId + ": " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Mono.error(new ServiceUnavailableException("Order Service communication error for order ID " + orderId + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    System.err.println("Connection error to Order Service for order " + orderId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to Order Service (connection refused/host unreachable) for order ID " + orderId, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    System.err.println("No route to host for Order Service for order " + orderId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("No route to host for Order Service for order ID " + orderId, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    System.err.println("Unknown host for Order Service for order " + orderId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Unknown host for Order Service for order ID " + orderId, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    System.err.println("Timeout connecting to Order Service for order " + orderId + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Order Service communication timeout for order ID " + orderId, e));
                })
                .onErrorResume(Exception.class, e -> {
                    System.err.println("Failed to fetch order " + orderId + " from Order Service due to unexpected error: " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to Order Service for order ID " + orderId, e));
                });
    }

    /**
     * Retrieves a Flux of OrderDto by user ID from the Order Service.
     * Returns Flux.empty() if no orders are found for the user (404 or empty list).
     * Throws ServiceUnavailableException for other 4xx/5xx errors or connectivity issues.
     * @param userId The ID of the user whose orders to retrieve.
     * @return Flux<OrderDto> if orders are found, Flux.empty() if not found, Mono.error() on other service errors.
     */
    public Flux<OrderDto> getOrdersByUserId(Long userId) {
        return webClient.get()
                .uri("/api/orders/user/{userId}", userId) // Adjust URI based on your Order Service API
                .retrieve()
                .onStatus(status -> status.is4xxClientError() && status != HttpStatus.NOT_FOUND,
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .map(errorBody -> new ServiceUnavailableException(
                            "Order Service returned client error for user ID " + userId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))
                        .flatMap(Mono::error))
                .onStatus(status -> status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .map(errorBody -> new ServiceUnavailableException(
                            "Order Service returned server error for user ID " + userId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))
                        .flatMap(Mono::error))
                .bodyToFlux(OrderDto.class) // Expecting a Flux of OrderDto
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("Orders for user " + userId + " not found in Order Service (404).");
                        return Flux.empty(); // Signal not found by returning empty Flux
                    }
                    System.err.println("WebClient response error for user " + userId + "'s orders: " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Flux.error(new ServiceUnavailableException("Order Service communication error for user ID " + userId + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    System.err.println("Connection error to Order Service for user " + userId + "'s orders: " + e.getMessage());
                    return Flux.error(new ServiceUnavailableException("Failed to connect to Order Service (connection refused/host unreachable) for user ID " + userId, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    System.err.println("No route to host for Order Service for user " + userId + "'s orders: " + e.getMessage());
                    return Flux.error(new ServiceUnavailableException("No route to host for Order Service for user ID " + userId, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    System.err.println("Unknown host for Order Service for user " + userId + "'s orders: " + e.getMessage());
                    return Flux.error(new ServiceUnavailableException("Unknown host for Order Service for user ID " + userId, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    System.err.println("Timeout connecting to Order Service for user " + userId + "'s orders: " + e.getMessage());
                    return Flux.error(new ServiceUnavailableException("Order Service communication timeout for user ID " + userId, e));
                })
                .onErrorResume(Exception.class, e -> {
                    System.err.println("Failed to fetch orders for user " + userId + " from Order Service due to unexpected error: " + e.getMessage());
                    return Flux.error(new ServiceUnavailableException("Failed to connect to Order Service for user ID " + userId, e));
                });
    }
}