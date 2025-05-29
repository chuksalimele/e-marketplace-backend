/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.intersevice;


import com.aliwudi.marketplace.backend.common.dto.ProductDto;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service responsible for integrating with the Product Catalog Service using WebClient.
 * WebClient is a non-blocking, reactive HTTP client.
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
     * Fetches all products from the ProductDto Catalog Service using WebClient.
     *
     * @return A Flux of ProductDto objects.
     */
    public Flux<ProductDto> getAllProductsWebClient() {
        return webClient.get()
                .retrieve() // Perform the request and retrieve the response
                .bodyToFlux(ProductDto.class) // Convert the response body to a Flux of ProductDto objects
                .doOnNext(product -> System.out.println("WebClient fetched product: " + product)); // For logging
    }

    /**
     * Fetches a single product by ID from the ProductDto Catalog Service using WebClient.
     *
     * @param productId The ID of the product to fetch.
     * @return A Mono of the ProductDto object.
     */
    public Mono<ProductDto> getProductByIdWebClient(Long productId) {
        return webClient.get()
                .uri("/{id}", productId) // Append the product ID to the base URL
                .retrieve()
                .bodyToMono(ProductDto.class) // Convert the response body to a Mono of a single ProductDto object
                .doOnNext(product -> System.out.println("WebClient fetched product by ID: " + product)); // For logging
    }
}
