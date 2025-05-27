// src/main/java/com/marketplace/emarketplacebackend/repository/ReviewRepository.java
package com.marketplace.emarketplacebackend.repository;

import com.marketplace.emarketplacebackend.model.Product;
import com.marketplace.emarketplacebackend.model.Review;
import com.marketplace.emarketplacebackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProduct(Product product);
    List<Review> findByUser(User user);

    // Optional: Find a specific review by user and product (useful for unique constraint check)
    Optional<Review> findByUserAndProduct(User user, Product product);

    // Custom query to calculate the average rating for a product
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(Long productId);
}