// src/main/java/com/marketplace/emarketplacebackend/repository/ReviewRepository.java
package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.product.model.Product;
import com.aliwudi.marketplace.backend.product.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProduct(Product product);
    List<Review> findByUserId(Long userId);

    // Optional: Find a specific review by user and product (useful for unique constraint check)
    Optional<Review> findByUserIdAndProduct(Long userId, Product product);

    // Custom query to calculate the average rating for a product
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(Long productId);
}