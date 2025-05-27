// src/main/java/com/marketplace/emarketplacebackend/service/ReviewService.java
package com.marketplace.emarketplacebackend.service;

import com.marketplace.emarketplacebackend.model.Product;
import com.marketplace.emarketplacebackend.model.Review;
import com.marketplace.emarketplacebackend.model.User;
import com.marketplace.emarketplacebackend.repository.ProductRepository;
import com.marketplace.emarketplacebackend.repository.ReviewRepository;
import com.marketplace.emarketplacebackend.repository.UserRepository;
import com.marketplace.emarketplacebackend.dto.ReviewRequest;
import com.marketplace.emarketplacebackend.exception.ResourceNotFoundException;
import com.marketplace.emarketplacebackend.exception.DuplicateResourceException; // For preventing multiple reviews

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Autowired
    public ReviewService(ReviewRepository reviewRepository, UserRepository userRepository, ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Review submitReview(ReviewRequest reviewRequest) {
        User user = userRepository.findById(reviewRequest.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + reviewRequest.getUserId()));

        Product product = productRepository.findById(reviewRequest.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + reviewRequest.getProductId()));

        // Check if the user has already reviewed this product (based on unique constraint)
        if (reviewRepository.findByUserAndProduct(user, product).isPresent()) {
            throw new DuplicateResourceException("User has already submitted a review for this product.");
        }

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(reviewRequest.getRating());
        review.setComment(reviewRequest.getComment());
        // reviewDate is set by @PrePersist in Review entity

        return reviewRepository.save(review);
    }

    public List<Review> getReviewsByProductId(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
        return reviewRepository.findByProduct(product);
    }

    public List<Review> getReviewsByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        return reviewRepository.findByUser(user);
    }

    public Optional<Review> getReviewById(Long id) {
        return reviewRepository.findById(id);
    }

    // Optional: Update a review (e.g., user can edit their own review)
    @Transactional
    public Review updateReview(Long reviewId, ReviewRequest updateRequest) {
        Review existingReview = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewId));

        // For security, you might add a check here that the authenticated user
        // is the owner of the review or an ADMIN.
        // if (!existingReview.getUser().getId().equals(updateRequest.getUserIdFromAuthContext())) {
        //    throw new AccessDeniedException("You are not authorized to update this review.");
        // }

        if (updateRequest.getRating() != null) {
            existingReview.setRating(updateRequest.getRating());
        }
        if (updateRequest.getComment() != null) {
            existingReview.setComment(updateRequest.getComment());
        }
        // reviewDate will be updated by @PreUpdate if you add it to the entity

        return reviewRepository.save(existingReview);
    }

    // Optional: Delete a review (e.g., user can delete their own review or admin can delete any)
    @Transactional
    public void deleteReview(Long reviewId) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new ResourceNotFoundException("Review not found with ID: " + reviewId);
        }
        reviewRepository.deleteById(reviewId);
    }

    // Method to get average rating for a product
    public Double getAverageRatingForProduct(Long productId) {
        // Ensure product exists before querying
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with ID: " + productId);
        }
        return reviewRepository.findAverageRatingByProductId(productId);
    }
}