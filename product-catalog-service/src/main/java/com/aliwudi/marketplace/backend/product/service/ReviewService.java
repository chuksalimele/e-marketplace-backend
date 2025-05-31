package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.dto.ReviewRequest;
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.exception.InvalidReviewDataException;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.model.Product; // Assuming you have a Product entity
import com.aliwudi.marketplace.backend.product.model.Review;
import com.aliwudi.marketplace.backend.product.repository.ProductRepository; // Assuming ProductRepository
import com.aliwudi.marketplace.backend.product.repository.ReviewRepository; // Assuming ReviewRepository
import com.aliwudi.marketplace.backend.user.repository.UserRepository; // Assuming UserRepository for userId validation

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Collectors;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository; // To validate product existence
    private final UserRepository userRepository;     // To validate user existence (optional, but good practice)

    
    public Mono<Review> submitReview(ReviewRequest reviewRequest) {
        // First, check if the product exists
        Mono<Product> productMono = productRepository.findById(reviewRequest.getProductId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with ID: " + reviewRequest.getProductId())));

        // Optional: Check if the user exists
        Mono<Boolean> userExistsMono = userRepository.existsById(reviewRequest.getUserId());

        // Check if a review already exists for this user and product
        Mono<Boolean> reviewExistsMono = reviewRepository.existsByProduct_IdAndUserId(reviewRequest.getProductId(), reviewRequest.getUserId());

        return Mono.zip(productMono, userExistsMono, reviewExistsMono)
                .flatMap(tuple -> {
                    Product product = tuple.getT1();
                    boolean userExists = tuple.getT2();
                    boolean reviewExists = tuple.getT3();

                    if (!userExists) {
                        return Mono.error(new ResourceNotFoundException("User not found with ID: " + reviewRequest.getUserId()));
                    }
                    if (reviewExists) {
                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_REVIEW_SUBMISSION));
                    }

                    if (reviewRequest.getRating() < 1 || reviewRequest.getRating() > 5) {
                        return Mono.error(new InvalidReviewDataException(ApiResponseMessages.INVALID_REVIEW_RATING));
                    }
                    // You can add more complex validation logic here for comment length, etc.

                    Review review = Review.builder()
                            .productId(reviewRequest.getProductId()) // Store product ID directly
                            .product(product) // Link to the product entity for convenience (if mapped)
                            .userId(reviewRequest.getUserId())
                            .rating(reviewRequest.getRating())
                            .comment(reviewRequest.getComment())
                            .reviewDate(LocalDateTime.now())
                            .build();

                    return reviewRepository.save(review);
                })
                .switchIfEmpty(Mono.error(new InvalidReviewDataException("Failed to submit review due to missing product or user data."))); // Should be caught by ResourceNotFoundException
    }

    
    public Mono<Review> updateReview(Long id, ReviewRequest updateRequest) {
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.REVIEW_NOT_FOUND + id)))
                .flatMap(existingReview -> {
                    if (updateRequest.getRating() != null) {
                        if (updateRequest.getRating() < 1 || updateRequest.getRating() > 5) {
                            return Mono.error(new InvalidReviewDataException(ApiResponseMessages.INVALID_REVIEW_RATING));
                        }
                        existingReview.setRating(updateRequest.getRating());
                    }
                    if (updateRequest.getComment() != null && !updateRequest.getComment().isBlank()) {
                        existingReview.setComment(updateRequest.getComment());
                    }
                    // Update review date only if comment or rating is changed
                    if (updateRequest.getRating() != null || (updateRequest.getComment() != null && !updateRequest.getComment().isBlank())) {
                        existingReview.setReviewDate(LocalDateTime.now());
                    }
                    return reviewRepository.save(existingReview);
                });
    }

    
    public Mono<Void> deleteReview(Long id) {
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.REVIEW_NOT_FOUND + id)))
                .flatMap(reviewRepository::delete);
    }

    
    public Flux<Review> getReviewsByProductId(Long productId, Long offset, Integer limit) {
        // Optional: Check if product exists before fetching reviews if you want to throw 404
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMapMany(product -> reviewRepository.findByProduct_Id(productId, offset, limit));
    }

    
    public Flux<Review> getReviewsByUserId(Long userId, Long offset, Integer limit) {
        // Optional: Check if user exists before fetching reviews if you want to throw 404
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + userId)))
                .flatMapMany(user -> reviewRepository.findByUserId(userId, offset, limit));
    }

    
    public Mono<Double> getAverageRatingForProduct(Long productId) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMap(product -> reviewRepository.findByProduct_Id(productId)
                        .map(Review::getRating)
                        .collect(Collectors.averagingDouble(Integer::doubleValue))
                        .map(avg -> Double.isNaN(avg) ? 0.0 : avg)); // Handle case where no reviews exist
    }

    // New count methods
    
    public Mono<Long> countReviewsByProductId(Long productId) {
        return reviewRepository.countByProduct_Id(productId);
    }

    
    public Mono<Long> countReviewsByUserId(Long userId) {
        return reviewRepository.countByUserId(userId);
    }
}