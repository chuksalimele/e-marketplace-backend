package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.common.model.Product;
import com.aliwudi.marketplace.backend.product.dto.ProductRequest;
import com.aliwudi.marketplace.backend.product.service.ProductService;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.exception.InvalidProductDataException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Helper method to map Product entity to Product DTO for public exposure.
     */
    private Mono<Product> prepareDto(Product product) {
        if (product == null) {
            return null;
        }
        return product;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        // Basic validation for request data before passing to service
        if (productRequest.getName() == null || productRequest.getName().isBlank() ||
            productRequest.getPrice() == null || productRequest.getPrice().doubleValue() <= 0 ||
            productRequest.getStockQuantity() == null || productRequest.getStockQuantity() < 0 ||
            productRequest.getStoreId() == null || productRequest.getSellerId() == null) { // Ensure sellerId is present
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_CREATION_REQUEST));
        }

        return productService.createProduct(productRequest)
                .flatMap(this::prepareDto)
                .map(product -> (StandardResponseEntity) StandardResponseEntity.created(product, ApiResponseMessages.PRODUCT_CREATED_SUCCESS))
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
                .flatMap(this::prepareDto)
                .map(product -> (StandardResponseEntity) StandardResponseEntity.ok(product, ApiResponseMessages.PRODUCT_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .onErrorResume(InvalidProductDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_DATA + e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_PRODUCT + ": " + e.getMessage())));
    }
    /**
     * Endpoint to decrease the stock quantity of a specific product.
     * Accessible by 'USER', 'SELLER', or 'ADMIN' roles.
     * Uses a PUT mapping to reflect an update operation on a resource.
     *
     * @param productId The ID of the product whose stock needs to be decreased.
     * @param quantity The amount to decrease the stock by, provided as a request parameter.
     * @return A Mono emitting a StandardResponseEntity containing the updated Product.
     */
    @PutMapping("/{productId}/decrease-stock")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')") // Adjust roles as per business logic (e.g., only 'USER' for purchases)
    public Mono<StandardResponseEntity> decreaseProductStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {

        // Basic validation for quantity at the controller level
        if (quantity == null || quantity <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_STOCK_DECREMENT_QUANTITY));
        }

        return productService.decreaseAndSaveStock(productId, quantity)
                .flatMap(this::prepareDto)
                .map(product -> (StandardResponseEntity) StandardResponseEntity.ok(product, ApiResponseMessages.PRODUCT_STOCK_DECREASED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(InvalidProductDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.PRODUCT_STOCK_UPDATE_FAILED + ": " + e.getMessage())));
    }
    
    @GetMapping
    public Mono<StandardResponseEntity> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return productService.getAllProducts(page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS + ": " + e.getMessage())));
    }

    @GetMapping("/count")
    public Mono<StandardResponseEntity> countAllProducts() {
        return productService.countAllProducts()
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
                .flatMap(this::prepareDto)
                .map(product -> (StandardResponseEntity) StandardResponseEntity.ok(product, ApiResponseMessages.PRODUCT_RETRIEVED_SUCCESS))
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
                .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.PRODUCT_DELETED_SUCCESS)))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_PRODUCT + ": " + e.getMessage())));
    }

    // --- New Endpoints based on ProductRepository methods ---

    @GetMapping("/store/{storeId}")
    public Mono<StandardResponseEntity> getProductsByStore(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (storeId == null || storeId <= 0 || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return productService.getProductsByStore(storeId, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS_BY_STORE + ": " + e.getMessage())));
    }

    @GetMapping("/store/{storeId}/count")
    public Mono<StandardResponseEntity> countProductsByStore(@PathVariable Long storeId) {
        if (storeId == null || storeId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_STORE_ID));
        }
        return productService.countProductsByStore(storeId)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT_COUNT_BY_STORE + ": " + e.getMessage())));
    }

    @GetMapping("/seller/{sellerId}")
    public Mono<StandardResponseEntity> getProductsBySeller(
            @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (sellerId == null || sellerId <= 0 || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return productService.getProductsBySeller(sellerId, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS_BY_SELLER + ": " + e.getMessage())));
    }

    @GetMapping("/seller/{sellerId}/count")
    public Mono<StandardResponseEntity> countProductsBySeller(@PathVariable Long sellerId) {
        if (sellerId == null || sellerId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SELLER_ID));
        }
        return productService.countProductsBySeller(sellerId)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT_COUNT_BY_SELLER + ": " + e.getMessage())));
    }

    @GetMapping("/category/{category}")
    public Mono<StandardResponseEntity> getProductsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (category == null || category.isBlank() || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return productService.getProductsByCategory(category, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS_BY_CATEGORY + ": " + e.getMessage())));
    }

    @GetMapping("/category/{category}/count")
    public Mono<StandardResponseEntity> countProductsByCategory(@PathVariable String category) {
        if (category == null || category.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_CATEGORY_NAME));
        }
        return productService.countProductsByCategory(category)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT_COUNT_BY_CATEGORY + ": " + e.getMessage())));
    }

    @GetMapping("/price-range")
    public Mono<StandardResponseEntity> getProductsByPriceRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (minPrice == null || maxPrice == null || minPrice.compareTo(BigDecimal.ZERO) < 0 || maxPrice.compareTo(BigDecimal.ZERO) < 0 || minPrice.compareTo(maxPrice) > 0 || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRICE_RANGE_PARAMETERS));
        }

        return productService.getProductsByPriceRange(minPrice, maxPrice, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS_BY_PRICE_RANGE + ": " + e.getMessage())));
    }

    @GetMapping("/price-range/count")
    public Mono<StandardResponseEntity> countProductsByPriceRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice) {

        if (minPrice == null || maxPrice == null || minPrice.compareTo(BigDecimal.ZERO) < 0 || maxPrice.compareTo(BigDecimal.ZERO) < 0 || minPrice.compareTo(maxPrice) > 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRICE_RANGE_PARAMETERS));
        }

        return productService.countProductsByPriceRange(minPrice, maxPrice)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT_COUNT_BY_PRICE_RANGE + ": " + e.getMessage())));
    }

    @GetMapping("/store/{storeId}/category/{category}")
    public Mono<StandardResponseEntity> getProductsByStoreAndCategory(
            @PathVariable Long storeId,
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (storeId == null || storeId <= 0 || category == null || category.isBlank() || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return productService.getProductsByStoreAndCategory(storeId, category, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS_BY_STORE_AND_CATEGORY + ": " + e.getMessage())));
    }

    @GetMapping("/store/{storeId}/category/{category}/count")
    public Mono<StandardResponseEntity> countProductsByStoreAndCategory(
            @PathVariable Long storeId,
            @PathVariable String category) {

        if (storeId == null || storeId <= 0 || category == null || category.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PARAMETERS));
        }

        return productService.countProductsByStoreAndCategory(storeId, category)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT_COUNT_BY_STORE_AND_CATEGORY + ": " + e.getMessage())));
    }

    @GetMapping("/seller/{sellerId}/category/{category}")
    public Mono<StandardResponseEntity> getProductsBySellerAndCategory(
            @PathVariable Long sellerId,
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (sellerId == null || sellerId <= 0 || category == null || category.isBlank() || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return productService.getProductsBySellerAndCategory(sellerId, category, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS_BY_SELLER_AND_CATEGORY + ": " + e.getMessage())));
    }

    // No direct count for sellerIdAndCategory in provided repository, so no corresponding controller method.

    @GetMapping("/category/{category}/price-range")
    public Mono<StandardResponseEntity> getProductsByCategoryAndPriceBetween(
            @PathVariable String category,
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (category == null || category.isBlank() || minPrice == null || maxPrice == null || minPrice.compareTo(BigDecimal.ZERO) < 0 || maxPrice.compareTo(BigDecimal.ZERO) < 0 || minPrice.compareTo(maxPrice) > 0 || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRICE_RANGE_PARAMETERS));
        }

        return productService.getProductsByCategoryAndPriceBetween(category, minPrice, maxPrice, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS_BY_CATEGORY_AND_PRICE_RANGE + ": " + e.getMessage())));
    }

    // No direct count for categoryAndPriceBetween in provided repository, so no corresponding controller method.

    @GetMapping("/search")
    public Mono<StandardResponseEntity> searchProducts(
            @RequestParam String name, // Changed from product_name to name for consistency with repository
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (name == null || name.isBlank() || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }

        return productService.searchProducts(name, page, size)   
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_PRODUCTS + ": " + e.getMessage())));
    }

    @GetMapping("/search/count")
    public Mono<StandardResponseEntity> countSearchProducts(@RequestParam String name) { // Changed from product_name to name
        if (name == null || name.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }
        return productService.countSearchProducts(name)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_PRODUCT_COUNT + ": " + e.getMessage())));
    }
    
    // --- New Location-based Endpoints ---

    @GetMapping("/location/{locationId}")
    public Mono<StandardResponseEntity> getProductsByLocationId(
            @PathVariable Long locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (locationId == null || locationId <= 0 || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return productService.getProductsByLocationId(locationId, page, size)         
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS + ": " + e.getMessage())));
    }

    @GetMapping("/location/{locationId}/count")
    public Mono<StandardResponseEntity> countProductsByLocationId(@PathVariable Long locationId) {
        if (locationId == null || locationId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PARAMETERS));
        }
        return productService.countProductsByLocationId(locationId)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT_COUNT + ": " + e.getMessage())));
    }

    @GetMapping("/location/country/{country}/city/{city}")
    public Mono<StandardResponseEntity> getProductsByCountryAndCity(
            @PathVariable String country,
            @PathVariable String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (country == null || country.isBlank() || city == null || city.isBlank() || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return productService.getProductsByCountryAndCity(country, city, page, size)           
                .flatMap(this::prepareDto)
                .collectList()
                .map(productList -> (StandardResponseEntity) StandardResponseEntity.ok(productList, ApiResponseMessages.PRODUCTS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCTS + ": " + e.getMessage())));
    }

    @GetMapping("/location/country/{country}/city/{city}/count")
    public Mono<StandardResponseEntity> countProductsByCountryAndCity(
            @PathVariable String country,
            @PathVariable String city) {

        if (country == null || country.isBlank() || city == null || city.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PARAMETERS));
        }
        return productService.countProductsByCountryAndCity(country, city)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.PRODUCT_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_PRODUCT_COUNT + ": " + e.getMessage())));
    }
    
}
