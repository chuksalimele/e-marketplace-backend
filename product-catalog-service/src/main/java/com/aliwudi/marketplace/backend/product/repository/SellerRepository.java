package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.product.model.Seller;
import org.springframework.data.domain.Pageable; // For pagination
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SellerRepository extends R2dbcRepository<Seller, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<Seller> findAllBy(Pageable pageable);

    // --- Seller Filtering and Search with Pagination ---

    /**
     * Search sellers by name (case-insensitive, contains) with pagination.
     */
    Flux<Seller> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Find a seller by their unique email address (no pagination, as it's unique).
     */
    Mono<Seller> findByEmail(String email);


    // --- Count Queries (for pagination metadata) ---

    /**
     * Count all sellers.
     */
    Mono<Long> count();

    /**
     * Count sellers by name (case-insensitive, contains).
     */
    Mono<Long> countByNameContainingIgnoreCase(String name);

    /**
     * Check if a seller with a given email already exists.
     */
    Mono<Boolean> existsByEmail(String email);
}