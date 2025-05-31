package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.dto.ProductRequest;
import com.aliwudi.marketplace.backend.product.model.Product;
import com.aliwudi.marketplace.backend.product.repository.ProductRepository;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.exception.InvalidProductDataException;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Creates a new product.
     * Performs basic validation for price and stock quantity.
     *
     * @param productRequest The DTO containing product creation data.
     * @return A Mono emitting the created Product.
     */
    public Mono<Product> createProduct(ProductRequest productRequest) {
        // You might add more complex validation here
        if (productRequest.getPrice().doubleValue() < 0 || productRequest.getStockQuantity() < 0) {
            return Mono.error(new InvalidProductDataException("Price and stock quantity cannot be negative."));
        }

        // Check if a product with the same name already exists for the seller
        return productRepository.existsByNameIgnoreCaseAndSellerId(productRequest.getName(), productRequest.getSellerId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new InvalidProductDataException("Product with name '" + productRequest.getName() + "' already exists for this seller."));
                    }
                    Product product = Product.builder()
                            .name(productRequest.getName())
                            .description(productRequest.getDescription())
                            .price(productRequest.getPrice())
                            .stockQuantity(productRequest.getStockQuantity())
                            .category(productRequest.getCategory())
                            .storeId(productRequest.getStoreId())
                            .sellerId(productRequest.getSellerId()) // Assuming sellerId is part of ProductRequest
                            .imageUrl(productRequest.getImageUrl())
                            .locationId(productRequest.getLocationId())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return productRepository.save(product);
                });
    }

    /**
     * Updates an existing product.
     * Finds the product by ID, updates its fields based on the request, and saves it.
     *
     * @param id The ID of the product to update.
     * @param productRequest The DTO containing product update data.
     * @return A Mono emitting the updated Product.
     */
    public Mono<Product> updateProduct(Long id, ProductRequest productRequest) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .flatMap(existingProduct -> {
                    // Update fields from request
                    if (productRequest.getName() != null && !productRequest.getName().isBlank()) {
                        existingProduct.setName(productRequest.getName());
                    }
                    if (productRequest.getDescription() != null && !productRequest.getDescription().isBlank()) {
                        existingProduct.setDescription(productRequest.getDescription());
                    }
                    if (productRequest.getPrice() != null) {
                        if (productRequest.getPrice().doubleValue() >= 0) {
                            existingProduct.setPrice(productRequest.getPrice());
                        } else {
                            return Mono.error(new InvalidProductDataException("Price cannot be negative."));
                        }
                    }
                    if (productRequest.getStockQuantity() != null) {
                        if (productRequest.getStockQuantity() >= 0) {
                            existingProduct.setStockQuantity(productRequest.getStockQuantity());
                        } else {
                            return Mono.error(new InvalidProductDataException("Stock quantity cannot be negative."));
                        }
                    }
                    if (productRequest.getCategory() != null && !productRequest.getCategory().isBlank()) {
                        existingProduct.setCategory(productRequest.getCategory());
                    }
                    if (productRequest.getStoreId() != null) {
                        existingProduct.setStoreId(productRequest.getStoreId());
                    }
                    if (productRequest.getSellerId() != null) { // Assuming sellerId can be updated
                        existingProduct.setSellerId(productRequest.getSellerId());
                    }
                    if (productRequest.getImageUrl() != null && !productRequest.getImageUrl().isBlank()) {
                        existingProduct.setImageUrl(productRequest.getImageUrl());
                    }
                    if (productRequest.getLocationId() != null ) {
                        existingProduct.setLocationId(productRequest.getLocationId());
                    }

                    existingProduct.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(existingProduct);
                });
    }

    /**
     * Deletes a product by its ID.
     *
     * @param id The ID of the product to delete.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteProduct(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .flatMap(productRepository::delete);
    }

    /**
     * Retrieves a product by its ID.
     *
     * @param id The ID of the product to retrieve.
     * @return A Mono emitting the Product if found, or an error if not.
     */
    public Mono<Product> getProductById(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + id)));
    }

    // --- All Products ---

    /**
     * Retrieves all products with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     */
    public Flux<Product> getAllProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findAllBy(pageable);
    }

    /**
     * Counts all products.
     *
     * @return A Mono emitting the total count of products.
     */
    public Mono<Long> countAllProducts() {
        return productRepository.count();
    }

    // --- Products by Store ---

    /**
     * Finds products belonging to a specific store with pagination.
     *
     * @param storeId The ID of the store.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     */
    public Flux<Product> getProductsByStore(Long storeId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByStoreId(storeId, pageable);
    }

    /**
     * Counts products belonging to a specific store.
     *
     * @param storeId The ID of the store.
     * @return A Mono emitting the count of products for the store.
     */
    public Mono<Long> countProductsByStore(Long storeId) {
        return productRepository.countByStoreId(storeId);
    }

    // --- Products by Seller ---

    /**
     * Finds products sold by a specific seller with pagination.
     *
     * @param sellerId The ID of the seller.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     */
    public Flux<Product> getProductsBySeller(Long sellerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findBySellerId(sellerId, pageable);
    }

    /**
     * Counts products sold by a specific seller.
     *
     * @param sellerId The ID of the seller.
     * @return A Mono emitting the count of products for the seller.
     */
    public Mono<Long> countProductsBySeller(Long sellerId) {
        return productRepository.countBySellerId(sellerId);
    }

    // --- Products by Category ---

    /**
     * Finds products by their category with pagination.
     *
     * @param category The category name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     */
    public Flux<Product> getProductsByCategory(String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByCategory(category, pageable);
    }

    /**
     * Counts products by their category.
     *
     * @param category The category name.
     * @return A Mono emitting the count of products for the category.
     */
    public Mono<Long> countProductsByCategory(String category) {
        return productRepository.countByCategory(category);
    }

    // --- Products by Price Range ---

    /**
     * Finds products within a given price range (inclusive) with pagination.
     *
     * @param minPrice The minimum price.
     * @param maxPrice The maximum price.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     */
    public Flux<Product> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByPriceBetween(minPrice, maxPrice, pageable);
    }

    /**
     * Counts products within a given price range (inclusive).
     *
     * @param minPrice The minimum price.
     * @param maxPrice The maximum price.
     * @return A Mono emitting the count of products within the price range.
     */
    public Mono<Long> countProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return productRepository.countByPriceBetween(minPrice, maxPrice);
    }

    // --- Combined Filters ---

    /**
     * Finds products in a specific store and category with pagination.
     *
     * @param storeId The ID of the store.
     * @param category The category name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     */
    public Flux<Product> getProductsByStoreAndCategory(Long storeId, String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByStoreIdAndCategory(storeId, category, pageable);
    }

    /**
     * Counts products in a specific store and category.
     *
     * @param storeId The ID of the store.
     * @param category The category name.
     * @return A Mono emitting the count of products.
     */
    public Mono<Long> countProductsByStoreAndCategory(Long storeId, String category) {
        return productRepository.countByStoreIdAndCategory(storeId, category);
    }

    /**
     * Finds products by a seller within a specific category with pagination.
     *
     * @param sellerId The ID of the seller.
     * @param category The category name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     */
    public Flux<Product> getProductsBySellerAndCategory(Long sellerId, String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findBySellerIdAndCategory(sellerId, category, pageable);
    }

    // No direct count for sellerIdAndCategory in provided repository, but can be added if needed.
    // For now, we'll assume it's not explicitly required by the repository.

    /**
     * Finds products by category and within a price range with pagination.
     *
     * @param category The category name.
     * @param minPrice The minimum price.
     * @param maxPrice The maximum price.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products.
     */
    public Flux<Product> getProductsByCategoryAndPriceBetween(String category, BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByCategoryAndPriceBetween(category, minPrice, maxPrice, pageable);
    }

    // No direct count for categoryAndPriceBetween in provided repository, but can be added if needed.

    // --- Search Products (by name) ---

    /**
     * Searches products by name (case-insensitive, contains) with pagination.
     *
     * @param productName The product name to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching products.
     */
    public Flux<Product> searchProducts(String productName, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByNameContainingIgnoreCase(productName, pageable);
    }

    /**
     * Counts products by name (case-insensitive, contains).
     *
     * @param productName The product name to search for.
     * @return A Mono emitting the count of matching products.
     */
    public Mono<Long> countSearchProducts(String productName) {
        return productRepository.countByNameContainingIgnoreCase(productName);
    }

    // The existing methods for location-based filtering are removed as they are not explicitly in the repository.
    // If location filtering is needed, it should be added to the Product model and ProductRepository.
    // For now, we will only implement methods explicitly defined in the provided ProductRepository.
}
