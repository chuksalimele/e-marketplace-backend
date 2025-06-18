package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.common.model.Product;
import com.aliwudi.marketplace.backend.product.dto.ProductRequest;
import com.aliwudi.marketplace.backend.product.service.ProductService;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.InvalidProductDataException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException; // Corrected package

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; // For @ResponseStatus
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List; // Keep for collection methods

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Endpoint to create a new product.
     *
     * @param productRequest The DTO containing product creation data.
     * @return A Mono emitting the created Product.
     * @throws IllegalArgumentException if input validation fails.
     * @throws InvalidProductDataException if product data is invalid (e.g., negative price).
     * @throws DuplicateResourceException if a product with the same name already exists for the seller.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created for resource creation
    @PreAuthorize("hasRole('admin') or hasRole('seller')")
    public Mono<Product> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        // Basic validation for required fields
        if (productRequest.getName() == null || productRequest.getName().isBlank() ||
            productRequest.getPrice() == null ||
            productRequest.getStockQuantity() == null ||
            productRequest.getStoreId() == null || productRequest.getSellerId() == null) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PRODUCT_CREATION_REQUEST);
        }
        // Additional validation (e.g., positive price/stock) is handled in service or DTO @Min annotations

        return productService.createProduct(productRequest);
        // Exceptions (InvalidProductDataException, DuplicateResourceException, ResourceNotFoundException)
        // are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to update an existing product.
     *
     * @param id The ID of the product to update.
     * @param productRequest The DTO containing product update data.
     * @return A Mono emitting the updated Product.
     * @throws IllegalArgumentException if product ID is invalid.
     * @throws ResourceNotFoundException if the product is not found.
     * @throws InvalidProductDataException if updated product data is invalid.
     * @throws DuplicateResourceException if the updated name causes a duplicate.
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin') or hasRole('seller')")
    public Mono<Product> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest productRequest) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PRODUCT_ID);
        }
        return productService.updateProduct(id, productRequest);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to decrease the stock quantity of a specific product.
     * Accessible by 'user', 'seller', or 'admin' roles.
     *
     * @param productId The ID of the product whose stock needs to be decreased.
     * @param quantity The amount to decrease the stock by.
     * @return A Mono emitting the updated Product with decreased stock.
     * @throws IllegalArgumentException if quantity is invalid.
     * @throws ResourceNotFoundException if the product is not found.
     * @throws InvalidProductDataException if there's insufficient stock.
     */
    @PutMapping("/{productId}/decrease-stock")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('user', 'seller', 'admin')")
    public Mono<Product> decreaseProductStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        if (productId == null || productId <= 0 || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_STOCK_DECREMENT_QUANTITY);
        }
        return productService.decreaseAndSaveStock(productId, quantity);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all products with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux of Products.
     * @throws IllegalArgumentException if pagination parameters are invalid.
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return productService.getAllProducts(page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all products.
     *
     * @return A Mono emitting the total count of products.
     */
    @GetMapping("/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countAllProducts() {
        return productService.countAllProducts();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a product by its ID.
     *
     * @param id The ID of the product to retrieve.
     * @return A Mono emitting the Product.
     * @throws IllegalArgumentException if product ID is invalid.
     * @throws ResourceNotFoundException if the product is not found.
     */
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Product> getProductById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PRODUCT_ID);
        }
        return productService.getProductById(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a product by its ID.
     *
     * @param id The ID of the product to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if product ID is invalid.
     * @throws ResourceNotFoundException if the product is not found.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content for successful deletion
    @PreAuthorize("hasRole('admin') or hasRole('seller')")
    public Mono<Void> deleteProduct(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PRODUCT_ID);
        }
        return productService.deleteProduct(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    // --- New Endpoints based on ProductRepository methods ---

    /**
     * Finds products belonging to a specific store with pagination.
     *
     * @param storeId The ID of the store.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     * @throws IllegalArgumentException if store ID or pagination parameters are invalid.
     * @throws ResourceNotFoundException if the store is not found.
     */
    @GetMapping("/store/{storeId}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> getProductsByStore(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (storeId == null || storeId <= 0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return productService.getProductsByStore(storeId, page, size);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Counts products belonging to a specific store.
     *
     * @param storeId The ID of the store.
     * @return A Mono emitting the count of products for the store.
     * @throws IllegalArgumentException if store ID is invalid.
     * @throws ResourceNotFoundException if the store is not found.
     */
    @GetMapping("/store/{storeId}/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countProductsByStore(@PathVariable Long storeId) {
        if (storeId == null || storeId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_STORE_ID);
        }
        return productService.countProductsByStore(storeId);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Finds products sold by a specific seller with pagination.
     *
     * @param sellerId The ID of the seller.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     * @throws IllegalArgumentException if seller ID or pagination parameters are invalid.
     */
    @GetMapping("/seller/{sellerId}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> getProductsBySeller(
            @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (sellerId == null || sellerId <= 0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return productService.getProductsBySeller(sellerId, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts products sold by a specific seller.
     *
     * @param sellerId The ID of the seller.
     * @return A Mono emitting the count of products for the seller.
     * @throws IllegalArgumentException if seller ID is invalid.
     */
    @GetMapping("/seller/{sellerId}/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countProductsBySeller(@PathVariable Long sellerId) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SELLER_ID);
        }
        return productService.countProductsBySeller(sellerId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds products by their category with pagination.
     *
     * @param category The category name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     * @throws IllegalArgumentException if category or pagination parameters are invalid.
     */
    @GetMapping("/category/{category}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> getProductsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (category == null || category.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return productService.getProductsByCategory(category, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts products by their category.
     *
     * @param category The category name.
     * @return A Mono emitting the count of products for the category.
     * @throws IllegalArgumentException if category is invalid.
     */
    @GetMapping("/category/{category}/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countProductsByCategory(@PathVariable String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_CATEGORY_NAME);
        }
        return productService.countProductsByCategory(category);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds products within a given price range (inclusive) with pagination.
     *
     * @param minPrice The minimum price.
     * @param maxPrice The maximum price.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     * @throws IllegalArgumentException if price range or pagination parameters are invalid.
     */
    @GetMapping("/price-range")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> getProductsByPriceRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (minPrice == null || maxPrice == null || minPrice.compareTo(BigDecimal.ZERO) < 0 || maxPrice.compareTo(BigDecimal.ZERO) < 0 || minPrice.compareTo(maxPrice) > 0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PRICE_RANGE_PARAMETERS);
        }
        return productService.getProductsByPriceRange(minPrice, maxPrice, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts products within a given price range (inclusive).
     *
     * @param minPrice The minimum price.
     * @param maxPrice The maximum price.
     * @return A Mono emitting the count of products within the price range.
     * @throws IllegalArgumentException if price range is invalid.
     */
    @GetMapping("/price-range/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countProductsByPriceRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice) {
        if (minPrice == null || maxPrice == null || minPrice.compareTo(BigDecimal.ZERO) < 0 || maxPrice.compareTo(BigDecimal.ZERO) < 0 || minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PRICE_RANGE_PARAMETERS);
        }
        return productService.countProductsByPriceRange(minPrice, maxPrice);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds products in a specific store and category with pagination.
     *
     * @param storeId The ID of the store.
     * @param category The category name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     * @throws IllegalArgumentException if store ID, category or pagination parameters are invalid.
     * @throws ResourceNotFoundException if the store is not found.
     */
    @GetMapping("/store/{storeId}/category/{category}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> getProductsByStoreAndCategory(
            @PathVariable Long storeId,
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (storeId == null || storeId <= 0 || category == null || category.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return productService.getProductsByStoreAndCategory(storeId, category, page, size);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Counts products in a specific store and category.
     *
     * @param storeId The ID of the store.
     * @param category The category name.
     * @return A Mono emitting the count of products.
     * @throws IllegalArgumentException if store ID or category are invalid.
     * @throws ResourceNotFoundException if the store is not found.
     */
    @GetMapping("/store/{storeId}/category/{category}/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countProductsByStoreAndCategory(
            @PathVariable Long storeId,
            @PathVariable String category) {
        if (storeId == null || storeId <= 0 || category == null || category.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PARAMETERS);
        }
        return productService.countProductsByStoreAndCategory(storeId, category);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Finds products by a seller within a specific category with pagination.
     *
     * @param sellerId The ID of the seller.
     * @param category The category name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     * @throws IllegalArgumentException if seller ID, category or pagination parameters are invalid.
     */
    @GetMapping("/seller/{sellerId}/category/{category}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> getProductsBySellerAndCategory(
            @PathVariable Long sellerId,
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (sellerId == null || sellerId <= 0 || category == null || category.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return productService.getProductsBySellerAndCategory(sellerId, category, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds products by category and within a price range with pagination.
     *
     * @param category The category name.
     * @param minPrice The minimum price.
     * @param maxPrice The maximum price.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     * @throws IllegalArgumentException if category, price range or pagination parameters are invalid.
     */
    @GetMapping("/category/{category}/price-range")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> getProductsByCategoryAndPriceBetween(
            @PathVariable String category,
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (category == null || category.isBlank() || minPrice == null || maxPrice == null || minPrice.compareTo(BigDecimal.ZERO) < 0 || maxPrice.compareTo(BigDecimal.ZERO) < 0 || minPrice.compareTo(maxPrice) > 0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PRICE_RANGE_PARAMETERS);
        }
        return productService.getProductsByCategoryAndPriceBetween(category, minPrice, maxPrice, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Searches products by name (case-insensitive, contains) with pagination.
     *
     * @param name The product name to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching products.
     * @throws IllegalArgumentException if search term or pagination parameters are invalid.
     */
    @GetMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> searchProducts(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (name == null || name.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM);
        }
        return productService.searchProducts(name, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts products by name (case-insensitive, contains).
     *
     * @param name The product name to search for.
     * @return A Mono emitting the count of matching products.
     * @throws IllegalArgumentException if search term is invalid.
     */
    @GetMapping("/search/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countSearchProducts(@RequestParam String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM);
        }
        return productService.countSearchProducts(name);
        // Errors are handled by GlobalExceptionHandler.
    }

    // --- New Location-based Endpoints ---

    /**
     * Finds products by location ID with pagination.
     *
     * @param locationId The ID of the location.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     * @throws IllegalArgumentException if location ID or pagination parameters are invalid.
     */
    @GetMapping("/location/{locationId}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> getProductsByLocationId(
            @PathVariable Long locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (locationId == null || locationId <= 0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return productService.getProductsByLocationId(locationId, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts products by location ID.
     *
     * @param locationId The ID of the location.
     * @return A Mono emitting the count of products.
     * @throws IllegalArgumentException if location ID is invalid.
     */
    @GetMapping("/location/{locationId}/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countProductsByLocationId(@PathVariable Long locationId) {
        if (locationId == null || locationId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PARAMETERS);
        }
        return productService.countProductsByLocationId(locationId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds products by country and city with pagination.
     *
     * @param country The country name.
     * @param city The city name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     * @throws IllegalArgumentException if country, city or pagination parameters are invalid.
     */
    @GetMapping("/location/country/{country}/city/{city}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Product> getProductsByCountryAndCity(
            @PathVariable String country,
            @PathVariable String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (country == null || country.isBlank() || city == null || city.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return productService.getProductsByCountryAndCity(country, city, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts products by country and city.
     *
     * @param country The country name.
     * @param city The city name.
     * @return A Mono emitting the count of products.
     * @throws IllegalArgumentException if country or city are invalid.
     */
    @GetMapping("/location/country/{country}/city/{city}/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countProductsByCountryAndCity(
            @PathVariable String country,
            @PathVariable String city) {
        if (country == null || country.isBlank() || city == null || city.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PARAMETERS);
        }
        return productService.countProductsByCountryAndCity(country, city);
        // Errors are handled by GlobalExceptionHandler.
    }
}
