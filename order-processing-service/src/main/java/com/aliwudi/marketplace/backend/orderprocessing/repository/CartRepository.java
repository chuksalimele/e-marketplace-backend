package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.common.model.Cart;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CartRepository extends R2dbcRepository<Cart, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<Cart> findAllBy(Pageable pageable);

    // --- Cart Specific Queries ---

    /**
     * Find a cart by its associated user ID. Assumes one cart per user.
     */
    Mono<Cart> findByUserId(Long userId);

    // --- Count Queries ---

    /**
     * Count all carts.
     */
    Mono<Long> count();

    /**
     * Check if a cart exists for a given user ID.
     */
    Mono<Boolean> existsByUserId(Long userId);

    /**
     * Delete a cart by user ID.
     */
    Mono<Void> deleteByUserId(Long userId);
}