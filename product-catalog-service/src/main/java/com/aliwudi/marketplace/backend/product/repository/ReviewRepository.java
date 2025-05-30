// src/main/java/com/marketplace/emarketplacebackend/repository/ReviewRepository.java
package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.product.model.Product;
import com.aliwudi.marketplace.backend.product.model.Review;
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // NEW: Import ReactiveCrudRepository
import org.springframework.data.r2dbc.repository.Query; // NEW: Import @Query from Spring Data R2DBC
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux; // NEW: Import Flux for multiple results
import reactor.core.publisher.Mono; // NEW: Import Mono for single results or completion

// Remove old JpaRepository import, Page, and Pageable imports

@Repository
// NEW: Extend ReactiveCrudRepository instead of JpaRepository
public interface ReviewRepository extends ReactiveCrudRepository<Review, Long> {

    // Old: Page<Review> findByProduct(Product product, Pageable pageable);
    // NEW: For pagination, use Flux with offset and limit parameters, which R2DBC translates to SQL OFFSET/LIMIT.
    Flux<Review> findByProduct_Id(Long productId, Long offset, Integer limit);
    Mono<Long> countByProduct_Id(Long productId); // For total count when paginating by product

    // Old: Page<Review> findByUserId(Long userId, Pageable pageable);
    Flux<Review> findByUserId(Long userId, Long offset, Integer limit);
    Mono<Long> countByUserId(Long userId); // For total count when paginating by user

    // Old: Optional<Review> findByUserIdAndProduct(Long userId, Product product);
    // NEW: Returns Mono for zero or one review found.
    Mono<Review> findByUserIdAndProduct(Long userId, Product product);

    // Old: @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    // Old: Double findAverageRatingByProductId(Long productId);
    // NEW: Use @Query from org.springframework.data.r2dbc.repository.Query.
    // NEW: Query directly references the table and column names for R2DBC (e.g., 'review' table, 'product_id' column).
    // NEW: Returns Mono<Double> as the average is a single value.
    @Query("SELECT AVG(r.rating) FROM review r WHERE r.product_id = :productId")
    Mono<Double> findAverageRatingByProductId(Long productId);

    public Flux<Review> findByProduct_Id(Long productId);

    public Mono<Boolean> existsByProduct_IdAndUserId(Long productId, Long userId);
}