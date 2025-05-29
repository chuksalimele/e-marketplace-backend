package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.model.Product;
import com.aliwudi.marketplace.backend.product.model.Review;
import com.aliwudi.marketplace.backend.product.repository.ProductRepository;
import com.aliwudi.marketplace.backend.product.repository.ReviewRepository;
import com.aliwudi.marketplace.backend.product.dto.ReviewRequest;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types
import reactor.core.publisher.Flux; // NEW: Import Flux for reactive collections

// Remove List and Optional imports as Mono/Flux handle these

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    @Autowired
    public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Mono<Review> submitReview(ReviewRequest reviewRequest) {
        // Find the Product reactively
        Mono<Product> productMono = productRepository.findById(reviewRequest.getProductId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with ID: " + reviewRequest.getProductId())));

        return productMono.flatMap(product ->
            // Check if the user has already reviewed this product
            reviewRepository.findByUserIdAndProduct(reviewRequest.getUserId(), product)
                    .flatMap(existingReview -> Mono.<Review>error(new DuplicateResourceException("User has already submitted a review for this product.")))
                    .switchIfEmpty(Mono.defer(() -> { // Only proceed if no duplicate is found
                        Review review = new Review();
                        review.setUserId(reviewRequest.getUserId());
                        review.setProduct(product);
                        review.setRating(reviewRequest.getRating());
                        review.setComment(reviewRequest.getComment());
                        // reviewDate is set by @PrePersist in Review entity
                        return reviewRepository.save(review);
                    }))
        );
    }

    public Flux<Review> getReviewsByProductId(Long productId, Long offset, Integer limit) {
        // Find the Product reactively, then fetch reviews
        return reviewRepository.findByProduct_Id(productId, offset, limit); 
    }

    public Flux<Review> getReviewsByUserId(Long userId, Long offset, Integer limit) {
        // If userRepository interaction is needed and reactive:
        // return userRepository.findById(userId)
        //         .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found with ID: " + userId)))
        //         .flatMapMany(user -> reviewRepository.findByUserId(userId));

        // Assuming findByUserId in ReviewRepository is direct and returns Flux<Review>
        return reviewRepository.findByUserId(userId, offset, limit);
    }

    public Mono<Review> getReviewById(Long id) {
        return reviewRepository.findById(id); // findById now returns Mono<Review>
    }

    @Transactional
    public Mono<Review> updateReview(Long reviewId, ReviewRequest updateRequest) {
        return reviewRepository.findById(reviewId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Review not found with ID: " + reviewId)))
                .flatMap(existingReview -> {
                    // For security, you might add a check here that the authenticated user
                    // is the owner of the review or an ADMIN.
                    // This would typically involve using Reactor Context or a security principal.
                    // Mono<String> currentUserIdMono = Mono.subscriberContext()
                    //     .map(context -> context.get("userId")); // Example of getting user ID from context

                    // return currentUserIdMono.flatMap(currentUserId -> {
                    //     if (!existingReview.getUserId().equals(currentUserId)) {
                    //         return Mono.error(new AccessDeniedException("You are not authorized to update this review."));
                    //     }

                        if (updateRequest.getRating() != null) {
                            existingReview.setRating(updateRequest.getRating());
                        }
                        if (updateRequest.getComment() != null) {
                            existingReview.setComment(updateRequest.getComment());
                        }
                        return reviewRepository.save(existingReview);
                    // });
                });
    }

    @Transactional
    public Mono<Void> deleteReview(Long reviewId) {
        return reviewRepository.existsById(reviewId) // existsById returns Mono<Boolean>
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResourceNotFoundException("Review not found with ID: " + reviewId));
                    }
                    return reviewRepository.deleteById(reviewId); // deleteById should return Mono<Void>
                });
    }

    // Method to get average rating for a product
    public Mono<Double> getAverageRatingForProduct(Long productId) {
        // Ensure product exists before querying
        return productRepository.existsById(productId) // existsById returns Mono<Boolean>
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResourceNotFoundException("Product not found with ID: " + productId));
                    }
                    // Assuming findAverageRatingByProductId returns Mono<Double>
                    return reviewRepository.findAverageRatingByProductId(productId);
                });
    }
}