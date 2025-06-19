package com.aliwudi.marketplace.backend.common.interservice;

import static com.aliwudi.marketplace.backend.common.constants.ApiPaths.*;
import com.aliwudi.marketplace.backend.common.model.Order;
import com.aliwudi.marketplace.backend.common.exception.ServiceUnavailableException;
import com.aliwudi.marketplace.backend.common.filter.JwtPropagationFilter;
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
import org.slf4j.Logger; // Import Logger
import org.slf4j.LoggerFactory; // Import LoggerFactory

@Service
public class OrderIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(OrderIntegrationService.class); // Add Logger


    private final WebClient webClient;
    private final String path = "lb://order-processing-service"+ORDER_CONTROLLER_BASE;

    // Constructor injection for WebClient.Builder and JwtPropagationFilter
    public OrderIntegrationService(WebClient.Builder webClientBuilder,
                                     JwtPropagationFilter jwtPropagationFilter) { // INJECT THE FILTER
        // Build WebClient instance. The base URL uses the Eureka service ID.
        // 'lb://' prefix indicates client-side load balancing via Eureka.        
        this.webClient = WebClient.builder()
                .baseUrl(path)
                .filter(jwtPropagationFilter) // APPLY THE FILTER HERE!
                .build();
    }

    /**
     * Helper method for common error handling logic for Mono-returning methods.
     *
     * @param <T> The type of the Mono.
     * @param mono The Mono to apply error handling to.
     * @param contextMessage A message providing context for the error (e.g.,
     * "fetching order").
     * @param resourceIdentifier The ID or name of the resource being acted
     * upon.
     * @param isNotFoundHandledSeparately A flag to indicate if 404 NOT_FOUND
     * should result in Mono.empty() instead of an error.
     * @return A Mono with enhanced error handling.
     */
    private <T> Mono<T> handleOrderServiceErrors(Mono<T> mono, String contextMessage, Object resourceIdentifier, boolean isNotFoundHandledSeparately) {
        return mono
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (isNotFoundHandledSeparately && e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        log.info("Order {} not found in Order Service (404) during {}.", resourceIdentifier, contextMessage);
                        return Mono.empty(); // Signal not found by returning empty
                    }
                    log.error("WebClient response error during {} for order {}: {} (Status: {})", contextMessage, resourceIdentifier, e.getMessage(), e.getStatusCode(), e);
                    return Mono.error(new ServiceUnavailableException("Order Service communication error during " + contextMessage + " for order ID " + resourceIdentifier + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    log.error("Connection error to Order Service during {} for order {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Mono.error(new ServiceUnavailableException("Failed to connect to Order Service (connection refused/host unreachable) during " + contextMessage + " for order ID " + resourceIdentifier, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    log.error("No route to host for Order Service during {} for order {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Mono.error(new ServiceUnavailableException("No route to host for Order Service during " + contextMessage + " for order ID " + resourceIdentifier, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    log.error("Unknown host for Order Service during {} for order {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Mono.error(new ServiceUnavailableException("Unknown host for Order Service during " + contextMessage + " for order ID " + resourceIdentifier, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    log.error("Timeout connecting to Order Service during {} for order {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Mono.error(new ServiceUnavailableException("Order Service communication timeout during " + contextMessage + " for order ID " + resourceIdentifier, e));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed during {} for order {} from Order Service due to unexpected error: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Mono.error(new ServiceUnavailableException("Failed to connect to Order Service during " + contextMessage + " for order ID " + resourceIdentifier, e));
                });
    }

    /**
     * Helper method for common error handling logic for Flux-returning methods.
     *
     * @param <T> The type of the Flux elements.
     * @param flux The Flux to apply error handling to.
     * @param contextMessage A message providing context for the error (e.g.,
     * "fetching orders").
     * @param resourceIdentifier The ID or name of the resource being acted
     * upon.
     * @param isNotFoundHandledSeparately A flag to indicate if 404 NOT_FOUND
     * should result in Flux.empty() instead of an error.
     * @return A Flux with enhanced error handling.
     */
    private <T> Flux<T> handleOrderServiceErrors(Flux<T> flux, String contextMessage, Object resourceIdentifier, boolean isNotFoundHandledSeparately) {
        return flux
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (isNotFoundHandledSeparately && e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        log.info("Orders for {} not found in Order Service (404) during {}.", resourceIdentifier, contextMessage);
                        return Flux.empty(); // Signal not found by returning empty Flux
                    }
                    log.error("WebClient response error during {} for {}: {} (Status: {})", contextMessage, resourceIdentifier, e.getMessage(), e.getStatusCode(), e);
                    return Flux.error(new ServiceUnavailableException("Order Service communication error during " + contextMessage + " for " + resourceIdentifier + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    log.error("Connection error to Order Service during {} for {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Flux.error(new ServiceUnavailableException("Failed to connect to Order Service (connection refused/host unreachable) during " + contextMessage + " for " + resourceIdentifier, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    log.error("No route to host for Order Service during {} for {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Flux.error(new ServiceUnavailableException("No route to host for Order Service during " + contextMessage + " for " + resourceIdentifier, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    log.error("Unknown host for Order Service during {} for {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Flux.error(new ServiceUnavailableException("Unknown host for Order Service during " + contextMessage + " for " + resourceIdentifier, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    log.error("Timeout connecting to Order Service during {} for {}: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Flux.error(new ServiceUnavailableException("Order Service communication timeout during " + contextMessage + " for " + resourceIdentifier, e));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed during {} for {} from Order Service due to unexpected error: {}", contextMessage, resourceIdentifier, e.getMessage(), e);
                    return Flux.error(new ServiceUnavailableException("Failed to connect to Order Service during " + contextMessage + " for " + resourceIdentifier, e));
                });
    }

    /**
     * Checks if an order exists by its ID. This method is optimized to only
     * check existence (HEAD request).
     *
     * @param orderId The ID of the order to check.
     * @return Mono<Boolean> true if order exists, false otherwise. Throws
     * ServiceUnavailableException if the Order Service itself is unavailable or
     * returns an error.
     */
    public Mono<Boolean> orderExistsById(Long orderId) {
        // We need to use GET to retrieve the actual boolean value from the body
        return webClient.get()
                .uri(ORDER_CHECK_EXISTS, orderId) // Dynamically builds the URI, e.g., "/exists/123"
                .retrieve()
                .bodyToMono(Boolean.class) // Extract the response body as a Mono<Boolean>
                .onErrorResume(WebClientResponseException.class, e -> {
                    // If the target service's GlobalExceptionHandler returns a 4xx or 5xx,
                    // this block will catch it.
                    // You might want to map specific HTTP statuses to boolean outcomes
                    // if they represent "not found" differently, but based on the provided
                    // checkOrderExists endpoint, it will always return 200 OK with true/false.
                    // So, WebClientResponseException here would imply a non-2xx status,
                    // indicating a server error or an invalid request (like path variable parsing error).
                    return Mono.error(e); // Re-throw the original error
                })
                // Apply your common error handling for other network/timeout issues
                .transform(mono -> handleOrderServiceErrors(mono, "checking order existence", orderId, false));
    }

    /**
     * Retrieves an OrderDto by order ID from the Order Service. Returns
     * Mono.empty() if the order is not found (404). Throws
     * ServiceUnavailableException for other 4xx/5xx errors or connectivity
     * issues.
     *
     * @param orderId The ID of the order to retrieve.
     * @return Mono<OrderDto> if order is found, Mono.empty() if not found,
     * Mono.error() on other service errors.
     */
    public Mono<Order> getOrderById(Long orderId) {
        Mono<Order> responseMono = webClient.get()
                .uri(ORDER_GET_BY_ID, orderId) // Adjust URI based on your Order Service API
                .retrieve()
                .bodyToMono(Order.class);

        return handleOrderServiceErrors(responseMono, "fetching order", orderId, true);
    }

    /**
     * Retrieves a Flux of OrderDto by user ID from the Order Service. Returns
     * Flux.empty() if no orders are found for the user (404 or empty list).
     * Throws ServiceUnavailableException for other 4xx/5xx errors or
     * connectivity issues.
     *
     * @param userId The ID of the user whose orders to retrieve.
     * @return Flux<OrderDto> if orders are found, Flux.empty() if not found,
     * Mono.error() on other service errors.
     */
    public Flux<Order> getOrdersByUserId(Long userId) {
        Flux<Order> responseFlux = webClient.get()
                .uri(ORDER_GET_BY_USER, userId) // Adjust URI based on your Order Service API
                .retrieve()
                .bodyToFlux(Order.class); // Expecting a Flux of Order

        return handleOrderServiceErrors(responseFlux, "fetching user's orders", userId, true);
    }
}
