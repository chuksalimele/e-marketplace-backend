package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.common.intersevice.UserIntegrationService;
import com.aliwudi.marketplace.backend.product.dto.ReviewRequest;
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.exception.InvalidReviewDataException;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.model.Product;
import com.aliwudi.marketplace.backend.common.model.Review;
import com.aliwudi.marketplace.backend.product.repository.ProductRepository;
import com.aliwudi.marketplace.backend.product.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final ProductRepository productRepository;
    private final UserIntegrationService userIntegrationService;

    /**
     * Submits a new review for a product.
     * Validates product and user existence, checks for duplicate reviews, and rating range.
     *
     * @param reviewRequest The DTO containing review submission data.
     * @return A Mono emitting the submitted Review.
     */
    public Mono<Review> submitReview(ReviewRequest reviewRequest) {
        // First, check if the product exists
        Mono<Product> productMono = productRepository.findById(reviewRequest.getProductId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + reviewRequest.getProductId())));

        // Optional: Check if the user exists
        Mono<Boolean> userExistsMono = userIntegrationService.userExistsById(reviewRequest.getUserId())
                .filter(Boolean::booleanValue) // Only proceed if user exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + reviewRequest.getUserId())));

        // Check if a review already exists for this user and product
        Mono<Boolean> reviewExistsMono = reviewRepository.existsByUserIdAndProductId(reviewRequest.getUserId(), reviewRequest.getProductId())
                .filter(exists -> !exists) // Only proceed if review does NOT exist
                .switchIfEmpty(Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_REVIEW_SUBMISSION)));

        // Validate rating
        if (reviewRequest.getRating() == null || reviewRequest.getRating() < 1 || reviewRequest.getRating() > 5) {
            return Mono.error(new InvalidReviewDataException(ApiResponseMessages.INVALID_REVIEW_RATING));
        }

        return Mono.zip(productMono, userExistsMono, reviewExistsMono)
                .flatMap(tuple -> {
                    Product product = tuple.getT1(); // Get the product from the tuple

                    Review review = Review.builder()
                            .productId(reviewRequest.getProductId())
                            .productId(product.getId()) // Link to the product entity for convenience (if mapped)
                            .userId(reviewRequest.getUserId())
                            .rating(reviewRequest.getRating())
                            .comment(reviewRequest.getComment())
                            .reviewTime(LocalDateTime.now())
                            .build();

                    return reviewRepository.save(review);
                });
    }

    /**
     * Updates an existing review.
     * Finds the review by ID, updates its fields based on the request, and saves it.
     * Validates rating range.
     *
     * @param id The ID of the review to update.
     * @param updateRequest The DTO containing review update data.
     * @return A Mono emitting the updated Review.
     */
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
                        existingReview.setReviewTime(LocalDateTime.now());
                    }
                    return reviewRepository.save(existingReview);
                });
    }

    /**
     * Deletes a review by its ID.
     *
     * @param id The ID of the review to delete.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteReview(Long id) {
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.REVIEW_NOT_FOUND + id)))
                .flatMap(reviewRepository::delete);
    }

    /**
     * Retrieves a single review by its ID.
     *
     * @param id The ID of the review to retrieve.
     * @return A Mono emitting the Review if found, or an error if not.
     */
    public Mono<Review> getReviewById(Long id) {
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.REVIEW_NOT_FOUND + id)));
    }

    // --- All Reviews ---

    /**
     * Retrieves all reviews with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting all reviews.
     */
    public Flux<Review> getAllReviews(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reviewRepository.findAllBy(pageable);
    }

    /**
     * Counts all reviews.
     *
     * @return A Mono emitting the total count of reviews.
     */
    public Mono<Long> countAllReviews() {
        return reviewRepository.count();
    }

    // --- Reviews by Product ---

    /**
     * Finds all reviews for a specific product with pagination.
     *
     * @param productId The ID of the product.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting reviews for the specified product.
     */
    public Flux<Review> getReviewsByProductId(Long productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMapMany(product -> reviewRepository.findByProductId(productId, pageable));
    }

    /**
     * Counts all reviews for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the total count of reviews for the product.
     */
    public Mono<Long> countReviewsByProductId(Long productId) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMap(product -> reviewRepository.countByProductId(productId));
    }

    // --- Reviews by User ---

    /**
     * Finds all reviews left by a specific user with pagination.
     *
     * @param userId The ID of the user.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting reviews by the specified user.
     */
    public Flux<Review> getReviewsByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userIntegrationService.getUserById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + userId)))
                .flatMapMany(user -> reviewRepository.findByUserId(userId, pageable));
    }

    /**
     * Counts all reviews left by a specific user.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the total count of reviews by the user.
     */
    public Mono<Long> countReviewsByUserId(Long userId) {
        return userIntegrationService.getUserById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + userId)))
                .flatMap(user -> reviewRepository.countByUserId(userId));
    }

    // --- Reviews by Product and Minimum Rating ---

    /**
     * Finds reviews for a product with a rating greater than or equal to a minimum value, with pagination.
     *
     * @param productId The ID of the product.
     * @param minRating The minimum rating (inclusive).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting filtered reviews.
     */
    public Flux<Review> getReviewsByProductIdAndMinRating(Long productId, Integer minRating, int page, int size) {
        if (minRating == null || minRating < 1 || minRating > 5) {
            return Flux.error(new InvalidReviewDataException(ApiResponseMessages.INVALID_REVIEW_RATING));
        }
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMapMany(product -> reviewRepository.findByProductIdAndRatingGreaterThanEqual(productId, minRating, pageable));
    }

    /**
     * Counts reviews for a product with a rating greater than or equal to a minimum value.
     *
     * @param productId The ID of the product.
     * @param minRating The minimum rating (inclusive).
     * @return A Mono emitting the count of filtered reviews.
     */
    public Mono<Long> countReviewsByProductIdAndMinRating(Long productId, Integer minRating) {
        if (minRating == null || minRating < 1 || minRating > 5) {
            return Mono.error(new InvalidReviewDataException(ApiResponseMessages.INVALID_REVIEW_RATING));
        }
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMap(product -> reviewRepository.countByProductIdAndRatingGreaterThanEqual(productId, minRating));
    }

    // --- Latest Reviews for a Product ---

    /**
     * Finds the latest reviews for a product, ordered by review time descending, with pagination.
     *
     * @param productId The ID of the product.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting latest reviews for the product.
     */
    public Flux<Review> getLatestReviewsByProductId(Long productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMapMany(product -> reviewRepository.findByProductIdOrderByReviewTimeDesc(productId, pageable));
    }

    // No direct count for ordered queries needed, countByProductId already covers total count.

    // --- Single Review by User and Product ---

    /**
     * Finds a review by a specific user for a specific product.
     *
     * @param userId The ID of the user.
     * @param productId The ID of the product.
     * @return A Mono emitting the Review if found, or an error if not.
     */
    public Mono<Review> getReviewByUserIdAndProductId(Long userId, Long productId) {
        return reviewRepository.findByUserIdAndProductId(userId, productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        ApiResponseMessages.REVIEW_NOT_FOUND + " for user " + userId + " and product " + productId)));
    }

    // --- Average Rating ---

    /**
     * Gets the average rating for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the average rating (Double), or 0.0 if no reviews exist.
     */
    public Mono<Double> getAverageRatingForProduct(Long productId) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMap(product -> reviewRepository.findAverageRatingByProductId(productId))
                .map(avg -> avg != null ? avg : 0.0); // Handle case where no reviews exist (AVG returns null)
    }

    // --- Existence Check ---

    /**
     * Checks if a user has already reviewed a specific product.
     *
     * @param userId The ID of the user.
     * @param productId The ID of the product.
     * @return A Mono emitting true if a review exists, false otherwise.
     */
    public Mono<Boolean> existsReviewByUserIdAndProductId(Long userId, Long productId) {
        return reviewRepository.existsByUserIdAndProductId(userId, productId);
    }
}
