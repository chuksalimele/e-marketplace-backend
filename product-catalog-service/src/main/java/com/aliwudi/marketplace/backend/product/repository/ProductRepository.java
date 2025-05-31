package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.product.model.Product;
import org.springframework.data.domain.Pageable; // For pagination
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Repository
public interface ProductRepository extends R2dbcRepository<Product, Long> {

    // --- Basic Retrieval & Pagination ---

    // Find all products with pagination
    Flux<Product> findAllBy(Pageable pageable); // 'By' is a common convention for findAll with Pageable

    // --- Product Filtering and Search with Pagination ---

    /**
     * Find products belonging to a specific store with pagination.
     */
    Flux<Product> findByStoreId(Long storeId, Pageable pageable);

    /**
     * Find products sold by a specific seller with pagination.
     */
    Flux<Product> findBySellerId(Long sellerId, Pageable pageable);

    /**
     * Find products by their category with pagination.
     */
    Flux<Product> findByCategory(String category, Pageable pageable);

    /**
     * Search products by name (case-insensitive, contains) with pagination.
     */
    Flux<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Find products within a given price range (inclusive) with pagination.
     */
    Flux<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    // --- Combined Filters with Pagination ---

    /**
     * Find products in a specific store and category with pagination.
     */
    Flux<Product> findByStoreIdAndCategory(Long storeId, String category, Pageable pageable);

    /**
     * Find products by a seller within a specific category with pagination.
     */
    Flux<Product> findBySellerIdAndCategory(Long sellerId, String category, Pageable pageable);

    /**
     * Find products by category and within a price range with pagination.
     */
    Flux<Product> findByCategoryAndPriceBetween(String category, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);


    // --- Count Queries (for pagination metadata) ---

    /**
     * Count all products.
     */
    Mono<Long> count();

    /**
     * Count products belonging to a specific store.
     */
    Mono<Long> countByStoreId(Long storeId);

    /**
     * Count products sold by a specific seller.
     */
    Mono<Long> countBySellerId(Long sellerId);

    /**
     * Count products by their category.
     */
    Mono<Long> countByCategory(String category);

    /**
     * Count products by name (case-insensitive, contains).
     */
    Mono<Long> countByNameContainingIgnoreCase(String name);

    /**
     * Count products within a given price range (inclusive).
     */
    Mono<Long> countByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    /**
     * Count products in a specific store and category.
     */
    Mono<Long> countByStoreIdAndCategory(Long storeId, String category);

    /**
     * Check if a product with a given name exists for a specific seller (e.g., for uniqueness).
     */
    Mono<Boolean> existsByNameIgnoreCaseAndSellerId(String name, Long sellerId);
}