package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.common.model.Store;
import org.springframework.data.domain.Pageable; // For pagination
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface StoreRepository extends R2dbcRepository<Store, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<Store> findAllBy(Pageable pageable);

    // --- Store Filtering and Search with Pagination ---

    /**
     * Find all stores owned by a specific seller with pagination.
     */
    Flux<Store> findBySellerId(Long sellerId, Pageable pageable);

    /**
     * Find all stores owned by a specific seller without pagination.
     */
    Flux<Store> findBySellerId(Long sellerId);
    
    /**
     * Search stores by name (case-insensitive, contains) with pagination.
     */
    Flux<Store> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Search stores by locationId (case-insensitive, contains) with pagination.
     */
    Flux<Store> findByLocationIdContainingIgnoreCase(String locationId, Pageable pageable);

    /**
     * Find stores with a rating greater than or equal to a minimum value, with pagination.
     */
    Flux<Store> findByRatingGreaterThanEqual(Double minRating, Pageable pageable);

    // --- Count Queries (for pagination metadata) ---

    /**
     * Count all stores.
     */
    Mono<Long> count();

    /**
     * Count all stores owned by a specific seller.
     */
    Mono<Long> countBySellerId(Long sellerId);

    /**
     * Count stores by name (case-insensitive, contains).
     */
    Mono<Long> countByNameContainingIgnoreCase(String name);

    /**
     * Count stores by locationId (case-insensitive, contains).
     */
    Mono<Long> countByLocationIdContainingIgnoreCase(String locationId);

    /**
     * Count stores with a rating greater than or equal to a minimum value.
     */
    Mono<Long> countByRatingGreaterThanEqual(Double minRating);

    /**
     * Check if a store with a given name exists for a specific seller (e.g., for uniqueness).
     */
    Mono<Boolean> existsByNameIgnoreCaseAndSellerId(String name, Long sellerId);
}