package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.product.dto.ProductRequest;
import com.aliwudi.marketplace.backend.product.dto.ProductResponse; // Assuming you'll need a ProductResponse DTO
import com.aliwudi.marketplace.backend.product.model.Product;
import com.aliwudi.marketplace.backend.product.service.ProductService;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException; // Custom exception for product not found
import com.aliwudi.marketplace.backend.product.exception.InvalidProductDataException; // Custom exception for invalid product data

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor // Using Lombok for constructor injection
public class ProductController {

    private final ProductService productService;

    /**
     * Helper method to map Product entity to ProductResponse DTO for public exposure.
     */
    private ProductResponse mapProductToProductResponse(Product product) {
        if (product == null) {
            return null;
        }
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .category(product.getCategory())
                .storeId(product.getStoreId())
                .imageUrl(product.getImageUrl())
                .location(product.getLocation())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        // Basic validation for request data before passing to service
        if (productRequest.getName() == null || productRequest.getName().isBlank() ||
            productRequest.getPrice() == null || productRequest.getPrice().doubleValue() <= 0 ||
            productRequest.getStockQuantity() == null || productRequest.getStockQuantity() < 0 ||
            productRequest.getStoreId() == null) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_CREATION_REQUEST));
        }

        return productService.createProduct(productRequest)
                .map(createdProduct -> (StandardResponseEntity) StandardResponseEntity.created(mapProductToProductResponse(createdProduct), ApiResponseMessages.PRODUCT_CREATED_SUCCESS))
                .onErrorResume(InvalidProductDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_DATA + e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CREATING_PRODUCT + ": " + e.getMessage())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest productRequest) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_ID));
        }

        return productService.updateProduct(id, productRequest)
                .map(updatedProduct -> (StandardResponseEntity) StandardResponseEntity.ok(mapProductToProductResponse(updatedProduct), ApiResponseMessages.PRODUCT_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .onErrorResume(InvalidProductDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_DATA + e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_PRODUCT + ": " + e.getMessage())));
    }

    @GetMapping
    public Mono<StandardResponseEntity> getAllProducts(
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {

        Flux<Product> productsFlux;
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        if (location != null && !location.trim().isEmpty()) {
            productsFlux = productService.getAllProductsByLocation(location, offset, limit);
        } else {
            productsFlux = productService.getAllProducts(offset, limit);
        }

        return productsFlux.collectList()
                .map(products -> products.stream()
                        .map(this::mapProductToProductResponse)
                        .collect(Collectors.toList()))
                .map(productResponses -> (StandardResponseEntity) StandardResponseEntity.ok(productResponses, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS + ": " + e.getMessage())));
    }

    @GetMapping("/count")
    public Mono<StandardResponseEntity> countAllProducts(@RequestParam(required = false) String location) {
        Mono<Long> countMono;
        if (location != null && !location.trim().isEmpty()) {
            countMono = productService.countProductsByLocation(location);
        } else {
            countMono = productService.countAllProducts();
        }
        return countMono
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT_COUNT + ": " + e.getMessage())));
    }

    @GetMapping("/{id}")
    public Mono<StandardResponseEntity> getProductById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_ID));
        }

        return productService.getProductById(id)
                .map(product -> (StandardResponseEntity) StandardResponseEntity.ok(mapProductToProductResponse(product), ApiResponseMessages.PRODUCT_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT + ": " + e.getMessage())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> deleteProduct(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_ID));
        }

        return productService.deleteProduct(id)
                .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.PRODUCT_DELETED_SUCCESS))) // Use then to return a response after Mono<Void> completes
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_PRODUCT + ": " + e.getMessage())));
    }

    @GetMapping("/category/{categoryName}")
    public Mono<StandardResponseEntity> getProductsByCategory(
            @PathVariable String categoryName,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {

        if (categoryName == null || categoryName.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_CATEGORY_NAME));
        }
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        Flux<Product> productsFlux;
        if (location != null && !location.trim().isEmpty()) {
            productsFlux = productService.getProductsByCategoryAndLocation(categoryName, location, offset, limit);
        } else {
            productsFlux = productService.getProductsByCategory(categoryName, offset, limit);
        }

        return productsFlux.collectList()
                .map(products -> products.stream()
                        .map(this::mapProductToProductResponse)
                        .collect(Collectors.toList()))
                .map(productResponses -> (StandardResponseEntity) StandardResponseEntity.ok(productResponses, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS_BY_CATEGORY + ": " + e.getMessage())));
    }

    @GetMapping("/category/{categoryName}/count")
    public Mono<StandardResponseEntity> countProductsByCategory(
            @PathVariable String categoryName,
            @RequestParam(required = false) String location) {

        if (categoryName == null || categoryName.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_CATEGORY_NAME));
        }

        Mono<Long> countMono;
        if (location != null && !location.trim().isEmpty()) {
            countMono = productService.countProductsByCategoryAndLocation(categoryName, location);
        } else {
            countMono = productService.countProductsByCategory(categoryName);
        }
        return countMono
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT_COUNT_BY_CATEGORY + ": " + e.getMessage())));
    }

    @GetMapping("/store/{storeId}")
    public Mono<StandardResponseEntity> getProductsByStore(
            @PathVariable Long storeId,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {

        if (storeId == null || storeId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_STORE_ID));
        }
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        Flux<Product> productsFlux;
        if (location != null && !location.trim().isEmpty()) {
            productsFlux = productService.getProductsByStoreAndLocation(storeId, location, offset, limit);
        } else {
            productsFlux = productService.getProductsByStore(storeId, offset, limit);
        }

        return productsFlux.collectList()
                .map(products -> products.stream()
                        .map(this::mapProductToProductResponse)
                        .collect(Collectors.toList()))
                .map(productResponses -> (StandardResponseEntity) StandardResponseEntity.ok(productResponses, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS_BY_STORE + ": " + e.getMessage())));
    }

    @GetMapping("/store/{storeId}/count")
    public Mono<StandardResponseEntity> countProductsByStore(
            @PathVariable Long storeId,
            @RequestParam(required = false) String location) {

        if (storeId == null || storeId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_STORE_ID));
        }

        Mono<Long> countMono;
        if (location != null && !location.trim().isEmpty()) {
            countMono = productService.countProductsByStoreAndLocation(storeId, location);
        } else {
            countMono = productService.countProductsByStore(storeId);
        }
        return countMono
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT_COUNT_BY_STORE + ": " + e.getMessage())));
    }

    @GetMapping("/search")
    public Mono<StandardResponseEntity> searchProducts(
            @RequestParam String product_name,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {

        if (product_name == null || product_name.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        Flux<Product> productsFlux;
        if (location != null && !location.trim().isEmpty()) {
            productsFlux = productService.searchProductsByNameAndLocation(product_name, location, offset, limit);
        } else {
            productsFlux = productService.searchProducts(product_name, offset, limit);
        }

        return productsFlux.collectList()
                .map(products -> products.stream()
                        .map(this::mapProductToProductResponse)
                        .collect(Collectors.toList()))
                .map(productResponses -> (StandardResponseEntity) StandardResponseEntity.ok(productResponses, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_PRODUCTS + ": " + e.getMessage())));
    }

    @GetMapping("/search/count")
    public Mono<StandardResponseEntity> countSearchProducts(
            @RequestParam String product_name,
            @RequestParam(required = false) String location) {

        if (product_name == null || product_name.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }

        Mono<Long> countMono;
        if (location != null && !location.trim().isEmpty()) {
            countMono = productService.countSearchProductsByNameAndLocation(product_name, location);
        } else {
            countMono = productService.countSearchProducts(product_name);
        }
        return countMono
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_PRODUCT_COUNT + ": " + e.getMessage())));
    }
}