/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.intersevice;

import com.aliwudi.marketplace.backend.common.dto.ProductDto;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


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
     * Fetches a single product by ID from the product-catalog-service
     * micro service using WebClient.
     *
     * @param productId The ID of the product to fetch.
     * @return A Mono of the ProductDto object.
     */
    public Mono<ProductDto> getProductDtoById(Long productId) {
        return webClient.get()
                .uri("/{id}", productId) // Append the product ID to the base URL
                .retrieve()
                .bodyToMono(ProductDto.class) // Convert the response body to a Mono of a single ProductDto object
                .doOnNext(
                        // For logging - REMOVE IN PRODUCTION
                        product -> System.out.println("WebClient fetched product by ID: " + product)
                );
    }

    public Mono<ProductDto> decreaseAndSaveStock(Long productId, Integer quantity) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
