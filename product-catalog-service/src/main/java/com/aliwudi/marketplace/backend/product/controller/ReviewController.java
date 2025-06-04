package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.common.model.Review;
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.exception.InvalidReviewDataException;
import com.aliwudi.marketplace.backend.product.dto.ReviewRequest;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.service.ReviewService;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Helper method to convert Review entity to ReviewDto DTO.
     * Note: Assumes 'product' field in Review entity might be null if not eagerly fetched.
     * For a robust solution, consider fetching product name separately if always required,
     * or ensuring 'product' is always loaded in the service layer where needed for the DTO.
     */
    private Mono<Review> prepareDto(Review review) {
        if (review == null) {
            return null;
        }
        return review;
    }

    @PostMapping
    public Mono<StandardResponseEntity> submitReview(@Valid @RequestBody ReviewRequest reviewRequest) {
        // Basic input validation at controller level
        if (reviewRequest.getProductId() == null || reviewRequest.getProductId() <= 0 ||
            reviewRequest.getUserId() == null || reviewRequest.getUserId() <= 0 ||
            reviewRequest.getRating() == null || reviewRequest.getRating() < 1 || reviewRequest.getRating() > 5) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_REVIEW_SUBMISSION));
        }

        return reviewService.submitReview(reviewRequest)
                .flatMap(this::prepareDto)
                .map(review -> StandardResponseEntity.created(review, ApiResponseMessages.REVIEW_SUBMITTED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(e.getMessage()))) // Catch specific ResourceNotFound
                .onErrorResume(DuplicateResourceException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.DUPLICATE_REVIEW_SUBMISSION)))
                .onErrorResume(InvalidReviewDataException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SUBMITTING_REVIEW + ": " + e.getMessage())));
    }

    @PutMapping("/{id}")
    public Mono<StandardResponseEntity> updateReview(@PathVariable Long id, @Valid @RequestBody ReviewRequest updateRequest) {
        if (id == null || id <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_REVIEW_ID));
        }
        if (updateRequest.getRating() != null && (updateRequest.getRating() < 1 || updateRequest.getRating() > 5)) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_REVIEW_RATING));
        }

        return reviewService.updateReview(id, updateRequest)
                .flatMap(this::prepareDto)
                .map(review -> StandardResponseEntity.ok(review, ApiResponseMessages.REVIEW_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.REVIEW_NOT_FOUND + id)))
                .onErrorResume(InvalidReviewDataException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_REVIEW + ": " + e.getMessage())));
    }

    @DeleteMapping("/{id}")
    public Mono<StandardResponseEntity> deleteReview(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_REVIEW_ID));
        }

        return reviewService.deleteReview(id)
                .then(Mono.just(StandardResponseEntity.ok(null, ApiResponseMessages.REVIEW_DELETED_SUCCESS)))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.REVIEW_NOT_FOUND + id)))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_REVIEW + ": " + e.getMessage())));
    }

    // --- New/Refactored Endpoints ---

    @GetMapping("/{id}")
    public Mono<StandardResponseEntity> getReviewById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_REVIEW_ID));
        }
        return reviewService.getReviewById(id)
                .flatMap(this::prepareDto)
                .map(review -> StandardResponseEntity.ok(review, ApiResponseMessages.REVIEW_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEW + ": " + e.getMessage())));
    }

    @GetMapping
    public Mono<StandardResponseEntity> getAllReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0 || size <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return reviewService.getAllReviews(page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(reviewList -> StandardResponseEntity.ok(reviewList, ApiResponseMessages.REVIEWS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEWS + ": " + e.getMessage())));
    }

    @GetMapping("/count")
    public Mono<StandardResponseEntity> countAllReviews() {
        return reviewService.countAllReviews()
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.REVIEW_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEW_COUNT + ": " + e.getMessage())));
    }

    @GetMapping("/product/{productId}")
    public Mono<StandardResponseEntity> getReviewsForProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (productId == null || productId <= 0 || page < 0 || size <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return reviewService.getReviewsByProductId(productId, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(reviewList -> StandardResponseEntity.ok(reviewList, ApiResponseMessages.REVIEWS_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEWS_FOR_PRODUCT + ": " + e.getMessage())));
    }

    @GetMapping("/product/{productId}/count")
    public Mono<StandardResponseEntity> countReviewsForProduct(@PathVariable Long productId) {
        if (productId == null || productId <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_ID));
        }
        return reviewService.countReviewsByProductId(productId)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.REVIEW_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEW_COUNT_FOR_PRODUCT + ": " + e.getMessage())));
    }

    @GetMapping("/user/{userId}")
    public Mono<StandardResponseEntity> getReviewsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (userId == null || userId <= 0 || page < 0 || size <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return reviewService.getReviewsByUserId(userId, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(reviewList -> StandardResponseEntity.ok(reviewList, ApiResponseMessages.REVIEWS_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEWS_BY_USER + ": " + e.getMessage())));
    }

    @GetMapping("/user/{userId}/count")
    public Mono<StandardResponseEntity> countReviewsByUser(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_USER_ID));
        }
        return reviewService.countReviewsByUserId(userId)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.REVIEW_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEW_COUNT_BY_USER + ": " + e.getMessage())));
    }

    @GetMapping("/product/{productId}/min-rating/{minRating}")
    public Mono<StandardResponseEntity> getReviewsByProductIdAndMinRating(
            @PathVariable Long productId,
            @PathVariable Integer minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (productId == null || productId <= 0 || minRating == null || minRating < 1 || minRating > 5 || page < 0 || size <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return reviewService.getReviewsByProductIdAndMinRating(productId, minRating, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(reviewList -> StandardResponseEntity.ok(reviewList, ApiResponseMessages.REVIEWS_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(InvalidReviewDataException.class, e -> Mono.just(StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEWS + ": " + e.getMessage())));
    }

    @GetMapping("/product/{productId}/min-rating/{minRating}/count")
    public Mono<StandardResponseEntity> countReviewsByProductIdAndMinRating(
            @PathVariable Long productId,
            @PathVariable Integer minRating) {

        if (productId == null || productId <= 0 || minRating == null || minRating < 1 || minRating > 5) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PARAMETERS));
        }
        return reviewService.countReviewsByProductIdAndMinRating(productId, minRating)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.REVIEW_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(InvalidReviewDataException.class, e -> Mono.just(StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEW_COUNT + ": " + e.getMessage())));
    }

    @GetMapping("/product/{productId}/latest")
    public Mono<StandardResponseEntity> getLatestReviewsByProductId(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (productId == null || productId <= 0 || page < 0 || size <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return reviewService.getLatestReviewsByProductId(productId, page, size)
                .flatMap(this::prepareDto)
                .collectList()
                .map(reviewList -> StandardResponseEntity.ok(reviewList, ApiResponseMessages.REVIEWS_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEWS + ": " + e.getMessage())));
    }

    @GetMapping("/user/{userId}/product/{productId}")
    public Mono<StandardResponseEntity> getReviewByUserIdAndProductId(
            @PathVariable Long userId,
            @PathVariable Long productId) {

        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PARAMETERS));
        }

        return reviewService.getReviewByUserIdAndProductId(userId, productId)
                .flatMap(this::prepareDto)
                .map(review -> StandardResponseEntity.ok(review, ApiResponseMessages.REVIEW_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_REVIEW + ": " + e.getMessage())));
    }

    @GetMapping("/product/{productId}/average-rating")
    public Mono<StandardResponseEntity> getAverageRatingForProduct(@PathVariable Long productId) {
        if (productId == null || productId <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PRODUCT_ID));
        }

        return reviewService.getAverageRatingForProduct(productId)
                .map(averageRating -> {
                    Map<String, Double> data = Map.of("averageRating", averageRating); // averageRating can be 0.0, not null
                    return StandardResponseEntity.ok(data, ApiResponseMessages.AVERAGE_RATING_RETRIEVED_SUCCESS);
                })
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_AVERAGE_RATING + ": " + e.getMessage())));
    }

    @GetMapping("/exists/user/{userId}/product/{productId}")
    public Mono<StandardResponseEntity> checkReviewExists(
            @PathVariable Long userId,
            @PathVariable Long productId) {

        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PARAMETERS));
        }

        return reviewService.existsReviewByUserIdAndProductId(userId, productId)
                .map(exists -> {
                    Map<String, Boolean> data = Map.of("exists", exists);
                    return StandardResponseEntity.ok(data, ApiResponseMessages.REVIEW_EXISTS_CHECK_SUCCESS);
                })
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CHECKING_REVIEW_EXISTENCE + ": " + e.getMessage())));
    }
}
