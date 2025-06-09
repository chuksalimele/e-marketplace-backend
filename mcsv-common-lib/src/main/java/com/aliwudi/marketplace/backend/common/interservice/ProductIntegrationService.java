package com.aliwudi.marketplace.backend.common.interservice;

import com.aliwudi.marketplace.backend.common.model.Product;
import com.aliwudi.marketplace.backend.common.exception.ServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Service responsible for integrating with the Product Catalog Service using
 * WebClient. WebClient is a non-blocking, reactive HTTP client.
 */
@Service
public class ProductIntegrationService {

    private final WebClient webClient;
    private final String path = "lb://product-catalog-service/api/products";

    // Constructor injection for WebClient.Builder
    // Spring Boot automatically configures a WebClient.Builder bean.
    public ProductIntegrationService(WebClient.Builder webClientBuilder) {
        // Build WebClient instance. The base URL uses the Eureka service ID.
        // 'lb://' prefix indicates client-side load balancing via Eureka.
        this.webClient = webClientBuilder.baseUrl(path).build();
    }

    /**
     * Helper method for common error handling logic.
     *
     * @param <T> The type of the Mono.
     * @param mono The Mono to apply error handling to.
     * @param contextMessage A message providing context for the error (e.g., "fetching product").
     * @param resourceIdentifier The ID or name of the resource being acted upon.
     * @return A Mono with enhanced error handling.
     */
    private <T> Mono<T> handleProductServiceErrors(Mono<T> mono, String contextMessage, Object resourceIdentifier) {
        return mono
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        System.out.println("Product " + resourceIdentifier + " not found in Product Service (404) during " + contextMessage + ".");
                        return Mono.empty(); // Signal not found by returning empty
                    }
                    System.err.println("WebClient response error during " + contextMessage + " for product " + resourceIdentifier + ": " + e.getMessage() + " (Status: " + e.getStatusCode() + ")");
                    return Mono.error(new ServiceUnavailableException("Product Service communication error during " + contextMessage + " for product " + resourceIdentifier + ": " + e.getMessage(), e));
                })
                .onErrorResume(ConnectException.class, e -> {
                    System.err.println("Connection error to Product Service during " + contextMessage + " for product " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to Product Service (connection refused/host unreachable) during " + contextMessage + " for product " + resourceIdentifier, e));
                })
                .onErrorResume(NoRouteToHostException.class, e -> {
                    System.err.println("No route to host for Product Service during " + contextMessage + " for product " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("No route to host for Product Service during " + contextMessage + " for product " + resourceIdentifier, e));
                })
                .onErrorResume(UnknownHostException.class, e -> {
                    System.err.println("Unknown host for Product Service during " + contextMessage + " for product " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Unknown host for Product Service during " + contextMessage + " for product " + resourceIdentifier, e));
                })
                .onErrorResume(TimeoutException.class, e -> {
                    System.err.println("Timeout connecting to Product Service during " + contextMessage + " for product " + resourceIdentifier + ": " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Product Service communication timeout during " + contextMessage + " for product " + resourceIdentifier, e));
                })
                .onErrorResume(Exception.class, e -> {
                    System.err.println("Failed during " + contextMessage + " for product " + resourceIdentifier + " from Product Service due to unexpected error: " + e.getMessage());
                    return Mono.error(new ServiceUnavailableException("Failed to connect to Product Service during " + contextMessage + " for product " + resourceIdentifier, e));
                });
    }

    /**
     * Fetches a single product by ID from the product-catalog-service
     * micro service using WebClient.
     *
     * @param productId The ID of the product to fetch.
     * @return A Mono of the Product object.
     */
    public Mono<Product> getProductById(Long productId) {
        Mono<Product> responseMono = webClient.get()
                .uri("/{id}", productId) // Append the product ID to the base URL
                .retrieve()
                .onStatus(status -> status.is4xxClientError() && status != HttpStatus.NOT_FOUND,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new ServiceUnavailableException(
                                        "Product Service returned client error for product ID " + productId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new ServiceUnavailableException(
                                        "Product Service returned server error for product ID " + productId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))))
                .bodyToMono(Product.class) // Convert the response body to a Mono of a single Product object
                .doOnNext(
                        // For logging - REMOVE IN PRODUCTION
                        product -> System.out.println("WebClient fetched product by ID: " + product)
                );

        return handleProductServiceErrors(responseMono, "fetching product", productId);
    }

    /**
     * Decreases the stock quantity of a product in the product-catalog-service
     * microservice using WebClient.
     *
     * @param productId The ID of the product whose stock to decrease.
     * @param quantity The amount to decrease the stock by.
     * @return A Mono of the updated Product object.
     */
    public Mono<Product> decreaseAndSaveStock(Long productId, Integer quantity) {
        Mono<Product> responseMono = webClient.put()
                .uri("/{productId}/decrease-stock?quantity={quantity}", productId, quantity)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() && status != HttpStatus.NOT_FOUND,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new ServiceUnavailableException(
                                        "Product Service returned client error for product ID " + productId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new ServiceUnavailableException(
                                        "Product Service returned server error for product ID " + productId + " (Status: " + clientResponse.statusCode() + "): " + errorBody))))
                .bodyToMono(Product.class)
                .doOnNext(
                        product -> System.out.println("WebClient decreased stock for product ID: " + productId + ", new stock: " + product.getStockQuantity())
                );

        return handleProductServiceErrors(responseMono, "decreasing stock", productId);
    }
}