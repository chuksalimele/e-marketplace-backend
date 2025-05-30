package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.exception.InvalidReviewDataException; // New custom exception
import com.aliwudi.marketplace.backend.product.dto.ReviewRequest;
import com.aliwudi.marketplace.backend.product.dto.ReviewResponse; // Assuming this DTO exists and is sufficient
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException; // Re-using existing or creating this
import com.aliwudi.marketplace.backend.product.model.Review;
import com.aliwudi.marketplace.backend.product.service.ReviewService;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors; // Needed for stream operations on List

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor // Using Lombok for constructor injection
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Helper method to convert Review entity to ReviewResponse DTO.
     */
    private ReviewResponse mapReviewToReviewResponse(Review review) {
        if (review == null) {
            return null;
        }
        return ReviewResponse.builder()
                .id(review.getId())
                // Assuming product relationship is lazily loaded or not always needed here
                // If product is often null or not fully fetched, you might need to adjust your service
                // or ensure eager fetching if productName is crucial for *every* review response.
                .productId(review.getProduct() != null ? review.getProduct().getId() : null)
                .productName(review.getProduct() != null ? review.getProduct().getName() : null)
                .userId(review.getUserId())
                .rating(review.getRating())
                .comment(review.getComment())
                .reviewDate(review.getReviewDate())
                .build();
    }

    @PostMapping
    public Mono<StandardResponseEntity> submitReview(@Valid @RequestBody ReviewRequest reviewRequest) {
        // Basic input validation at controller level
        if (reviewRequest.getProductId() == null || reviewRequest.getProductId() <= 0 ||
            reviewRequest.getUserId() == null || reviewRequest.getUserId() <= 0 ||
            reviewRequest.getRating() == null || reviewRequest.getRating() < 1 || reviewRequest.getRating() > 5) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_REVIEW_SUBMISSION));
        }

        return reviewService.submitReview(reviewRequest)
                .map(review -> (StandardResponseEntity) StandardResponseEntity.created(
                        mapReviewToReviewResponse(review), ApiResponseMessages.REVIEW_SUBMITTED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + e.getMessage()))) // Assuming this comes from product/user validation
                .onErrorResume(DuplicateResourceException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.conflict(ApiResponseMessages.DUPLICATE_REVIEW_SUBMISSION)))
                .onErrorResume(InvalidReviewDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SUBMITTING_REVIEW + ": " + e.getMessage())));
    }

    @GetMapping("/product/{productId}")
    public Mono<StandardResponseEntity> getReviewsForProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {

        if (productId == null || productId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_ID));
        }
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return reviewService.getReviewsByProductId(productId, offset, limit)
                .map(this::mapReviewToReviewResponse)
                .collectList()
                .map(reviewResponses -> (StandardResponseEntity) StandardResponseEntity.ok(
                        reviewResponses, ApiResponseMessages.REVIEWS_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> // Catches if product itself not found
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEWS_FOR_PRODUCT + ": " + e.getMessage())));
    }

    @GetMapping("/user/{userId}")
    public Mono<StandardResponseEntity> getReviewsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {

        if (userId == null || userId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_USER_ID));
        }
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return reviewService.getReviewsByUserId(userId, offset, limit)
                .map(this::mapReviewToReviewResponse)
                .collectList()
                .map(reviewResponses -> (StandardResponseEntity) StandardResponseEntity.ok(
                        reviewResponses, ApiResponseMessages.REVIEWS_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> // Catches if user itself not found
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + userId)))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEWS_BY_USER + ": " + e.getMessage())));
    }

    @GetMapping("/product/{productId}/average-rating")
    public Mono<StandardResponseEntity> getAverageRating(@PathVariable Long productId) {
        if (productId == null || productId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_ID));
        }

        return reviewService.getAverageRatingForProduct(productId)
                .map(averageRating -> {
                    // Map to a custom DTO or a Map for consistency with StandardResponseEntity
                    Map<String, Double> data = Map.of("averageRating", averageRating != null ? averageRating : 0.0);
                    return (StandardResponseEntity) StandardResponseEntity.ok(data, ApiResponseMessages.AVERAGE_RATING_RETRIEVED_SUCCESS);
                })
                .onErrorResume(ResourceNotFoundException.class, e -> // Product not found
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_AVERAGE_RATING + ": " + e.getMessage())));
    }

    @PutMapping("/{id}")
    public Mono<StandardResponseEntity> updateReview(@PathVariable Long id, @Valid @RequestBody ReviewRequest updateRequest) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_REVIEW_ID));
        }
        if (updateRequest.getRating() != null && (updateRequest.getRating() < 1 || updateRequest.getRating() > 5)) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_REVIEW_RATING));
        }

        return reviewService.updateReview(id, updateRequest)
                .map(updatedReview -> (StandardResponseEntity) StandardResponseEntity.ok(
                        mapReviewToReviewResponse(updatedReview), ApiResponseMessages.REVIEW_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.REVIEW_NOT_FOUND + id)))
                .onErrorResume(InvalidReviewDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_REVIEW + ": " + e.getMessage())));
    }

    @DeleteMapping("/{id}")
    public Mono<StandardResponseEntity> deleteReview(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_REVIEW_ID));
        }

        return reviewService.deleteReview(id)
                .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.REVIEW_DELETED_SUCCESS))) // Use then() to provide a success message after Void
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.REVIEW_NOT_FOUND + id)))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_REVIEW + ": " + e.getMessage())));
    }
}