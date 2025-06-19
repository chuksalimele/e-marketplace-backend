package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.common.model.Review;
import com.aliwudi.marketplace.backend.product.dto.ReviewRequest;
import com.aliwudi.marketplace.backend.product.service.ReviewService;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.InvalidReviewDataException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException; // Corrected package

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; // For @ResponseStatus
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.Map; // Keep for Map.of

// Static import for API path constants and roles
import static com.aliwudi.marketplace.backend.common.constants.ApiConstants.*;

@RestController
@RequestMapping(REVIEW_CONTROLLER_BASE) // MODIFIED
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Endpoint to submit a new review for a product.
     *
     * @param reviewRequest The DTO containing review submission data.
     * @return A Mono emitting the submitted Review.
     * @throws IllegalArgumentException if input validation fails.
     * @throws ResourceNotFoundException if a product or user is not found.
     * @throws DuplicateResourceException if a review already exists for this user and product.
     * @throws InvalidReviewDataException if the rating is invalid.
     */
    @PostMapping(REVIEW_SUBMIT) // MODIFIED
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created
    public Mono<Review> submitReview(@Valid @RequestBody ReviewRequest reviewRequest) {
        // Basic input validation at controller level
        if (reviewRequest.getProductId() == null || reviewRequest.getProductId() <= 0 ||
            reviewRequest.getUserId() == null || reviewRequest.getUserId() <= 0 ||
            reviewRequest.getRating() == null) { // Rating range check is in service
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_REVIEW_SUBMISSION);
        }
        return reviewService.submitReview(reviewRequest);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to update an existing review.
     *
     * @param id The ID of the review to update.
     * @param updateRequest The DTO containing review update data.
     * @return A Mono emitting the updated Review.
     * @throws IllegalArgumentException if review ID is invalid or rating is out of range.
     * @throws ResourceNotFoundException if the review is not found.
     * @throws InvalidReviewDataException if updated review data is invalid.
     */
    @PutMapping(REVIEW_UPDATE) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Review> updateReview(@PathVariable Long id, @Valid @RequestBody ReviewRequest updateRequest) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_REVIEW_ID);
        }
        // Rating range validation now primarily in service, but a quick check here too doesn't hurt.
        if (updateRequest.getRating() != null && (updateRequest.getRating() < 1 || updateRequest.getRating() > 5)) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_REVIEW_RATING);
        }
        return reviewService.updateReview(id, updateRequest);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a review by its ID.
     *
     * @param id The ID of the review to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if review ID is invalid.
     * @throws ResourceNotFoundException if the review is not found.
     */
    @DeleteMapping(REVIEW_DELETE) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    public Mono<Void> deleteReview(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_REVIEW_ID);
        }
        return reviewService.deleteReview(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a single review by its ID.
     *
     * @param id The ID of the review to retrieve.
     * @return A Mono emitting the Review.
     * @throws IllegalArgumentException if review ID is invalid.
     * @throws ResourceNotFoundException if the review is not found.
     */
    @GetMapping(REVIEW_GET_BY_ID) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Review> getReviewById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_REVIEW_ID);
        }
        return reviewService.getReviewById(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all reviews with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting all reviews.
     * @throws IllegalArgumentException if pagination parameters are invalid.
     */
    @GetMapping(REVIEW_GET_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Review> getAllReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return reviewService.getAllReviews(page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all reviews.
     *
     * @return A Mono emitting the total count of reviews.
     */
    @GetMapping(REVIEW_COUNT_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countAllReviews() {
        return reviewService.countAllReviews();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all reviews for a specific product with pagination.
     *
     * @param productId The ID of the product.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting reviews for the specified product.
     * @throws IllegalArgumentException if product ID or pagination parameters are invalid.
     * @throws ResourceNotFoundException if the product is not found.
     */
    @GetMapping(REVIEW_GET_BY_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Review> getReviewsForProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (productId == null || productId <= 0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return reviewService.getReviewsByProductId(productId, page, size);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all reviews for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the total count of reviews for the product.
     * @throws IllegalArgumentException if product ID is invalid.
     * @throws ResourceNotFoundException if the product is not found.
     */
    @GetMapping(REVIEW_COUNT_BY_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countReviewsForProduct(@PathVariable Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PRODUCT_ID);
        }
        return reviewService.countReviewsByProductId(productId);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all reviews left by a specific user with pagination.
     *
     * @param userId The ID of the user.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting reviews by the specified user.
     * @throws IllegalArgumentException if user ID or pagination parameters are invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @GetMapping(REVIEW_GET_BY_USER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Review> getReviewsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (userId == null || userId <= 0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return reviewService.getReviewsByUserId(userId, page, size);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all reviews left by a specific user.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the total count of reviews by the user.
     * @throws IllegalArgumentException if user ID is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @GetMapping(REVIEW_COUNT_BY_USER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countReviewsByUser(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID);
        }
        return reviewService.countReviewsByUserId(userId);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find reviews for a product with a rating greater than or equal to a minimum value, with pagination.
     *
     * @param productId The ID of the product.
     * @param minRating The minimum rating (inclusive).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting filtered reviews.
     * @throws IllegalArgumentException if product ID, minRating, or pagination parameters are invalid.
     * @throws ResourceNotFoundException if the product is not found.
     * @throws InvalidReviewDataException if the minRating is out of range.
     */
    @GetMapping(REVIEW_GET_BY_PRODUCT_AND_MIN_RATING) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Review> getReviewsByProductIdAndMinRating(
            @PathVariable Long productId,
            @PathVariable Integer minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (productId == null || productId <= 0 || minRating == null || minRating < 1 || minRating > 5 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return reviewService.getReviewsByProductIdAndMinRating(productId, minRating, page, size);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count reviews for a product with a rating greater than or equal to a minimum value.
     *
     * @param productId The ID of the product.
     * @param minRating The minimum rating (inclusive).
     * @return A Mono emitting the count of filtered reviews.
     * @throws IllegalArgumentException if product ID or minRating are invalid.
     * @throws ResourceNotFoundException if the product is not found.
     * @throws InvalidReviewDataException if the minRating is out of range.
     */
    @GetMapping(REVIEW_COUNT_BY_PRODUCT_AND_MIN_RATING) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countReviewsByProductIdAndMinRating(
            @PathVariable Long productId,
            @PathVariable Integer minRating) {
        if (productId == null || productId <= 0 || minRating == null || minRating < 1 || minRating > 5) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PARAMETERS);
        }
        return reviewService.countReviewsByProductIdAndMinRating(productId, minRating);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find the latest reviews for a product, ordered by review time descending, with pagination.
     *
     * @param productId The ID of the product.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting latest reviews for the product.
     * @throws IllegalArgumentException if product ID or pagination parameters are invalid.
     * @throws ResourceNotFoundException if the product is not found.
     */
    @GetMapping(REVIEW_GET_LATEST_BY_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Review> getLatestReviewsByProductId(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (productId == null || productId <= 0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return reviewService.getLatestReviewsByProductId(productId, page, size);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find a review by a specific user for a specific product.
     *
     * @param userId The ID of the user.
     * @param productId The ID of the product.
     * @return A Mono emitting the Review.
     * @throws IllegalArgumentException if user ID or product ID are invalid.
     * @throws ResourceNotFoundException if the review is not found.
     */
    @GetMapping(REVIEW_GET_BY_USER_AND_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Review> getReviewByUserIdAndProductId(
            @PathVariable Long userId,
            @PathVariable Long productId) {
        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PARAMETERS);
        }
        return reviewService.getReviewByUserIdAndProductId(userId, productId);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to get the average rating for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the average rating (Double).
     * @throws IllegalArgumentException if product ID is invalid.
     * @throws ResourceNotFoundException if the product is not found.
     */
    @GetMapping(REVIEW_GET_AVERAGE_RATING_FOR_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Double>> getAverageRatingForProduct(@PathVariable Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PRODUCT_ID);
        }
        return reviewService.getAverageRatingForProduct(productId)
                .map(averageRating -> Map.of("averageRating", averageRating));
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to check if a user has already reviewed a specific product.
     *
     * @param userId The ID of the user.
     * @param productId The ID of the product.
     * @return A Mono emitting true if a review exists, false otherwise (Boolean).
     * @throws IllegalArgumentException if user ID or product ID are invalid.
     */
    @GetMapping(REVIEW_CHECK_EXISTS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> checkReviewExists(
            @PathVariable Long userId,
            @PathVariable Long productId) {
        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PARAMETERS);
        }
        return reviewService.existsReviewByUserIdAndProductId(userId, productId);
        // Errors are handled by GlobalExceptionHandler.
    }
}