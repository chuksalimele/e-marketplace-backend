package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.product.model.Review;
import org.springframework.data.domain.Pageable; // For pagination
import org.springframework.data.r2dbc.repository.Query; // For custom queries
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param; // For @Param in custom queries
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReviewRepository extends R2dbcRepository<Review, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<Review> findAllBy(Pageable pageable);

    // --- Review Filtering with Pagination ---

    /**
     * Find all reviews for a specific product with pagination.
     */
    Flux<Review> findByProductId(Long productId, Pageable pageable);

    /**
     * Find all reviews left by a specific user with pagination.
     */
    Flux<Review> findByUserId(Long userId, Pageable pageable);

    /**
     * Find reviews for a product with a rating greater than or equal to a minimum value, with pagination.
     */
    Flux<Review> findByProductIdAndRatingGreaterThanEqual(Long productId, Integer minRating, Pageable pageable);

    /**
     * Find the latest reviews for a product, ordered by review time descending, with pagination.
     */
    Flux<Review> findByProductIdOrderByReviewTimeDesc(Long productId, Pageable pageable);

    /**
     * Find reviews by a specific user for a specific product (usually unique, no pagination needed).
     */
    Mono<Review> findByUserIdAndProductId(Long userId, Long productId);


    // --- Count Queries (for pagination metadata) ---

    /**
     * Count all reviews.
     */
    Mono<Long> count();

    /**
     * Count all reviews for a specific product.
     */
    Mono<Long> countByProductId(Long productId);

    /**
     * Count all reviews left by a specific user.
     */
    Mono<Long> countByUserId(Long userId);

    /**
     * Count reviews for a product with a rating greater than or equal to a minimum value.
     */
    Mono<Long> countByProductIdAndRatingGreaterThanEqual(Long productId, Integer minRating);

    /**
     * Get the average rating for a specific product.
     * Note: For aggregate functions like AVG, you'll typically use a @Query.
     */
    @Query("SELECT AVG(r.rating) FROM reviews r WHERE r.product_id = :productId")
    Mono<Double> findAverageRatingByProductId(@Param("productId") Long productId);

    /**
     * Check if a user has already reviewed a specific product.
     */
    Mono<Boolean> existsByUserIdAndProductId(Long userId, Long productId);
}