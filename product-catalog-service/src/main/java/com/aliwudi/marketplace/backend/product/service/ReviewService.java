package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.common.interservice.UserIntegrationService;
import com.aliwudi.marketplace.backend.common.model.User; // Import User model for prepareDto
import com.aliwudi.marketplace.backend.product.dto.ReviewRequest;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.InvalidReviewDataException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException; // Corrected package
import com.aliwudi.marketplace.backend.common.model.Product;
import com.aliwudi.marketplace.backend.common.model.Review;
import com.aliwudi.marketplace.backend.product.repository.ProductRepository;
import com.aliwudi.marketplace.backend.product.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added for logging
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort; // Added for sorting
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List; // For prepareDto List.of

import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@Service
@RequiredArgsConstructor
@Slf4j // Enables Lombok's logging
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductService productService; // Use ProductService for fetching products
    private final UserIntegrationService userIntegrationService;

    // IMPORTANT: This prepareDto method is moved from the controller
    // and kept *exactly* as provided by you. It is now a private helper method
    // within the service to enrich the entities before they are returned.
    /**
     * Helper method to convert Review entity to ReviewDto DTO.
     * Note: Assumes 'product' field in Review entity might be null if not eagerly fetched.
     * For a robust solution, consider fetching product name separately if always required,
     * or ensuring 'product' is always loaded in the service layer where needed for the DTO.
     */
    private Mono<Review> prepareDto(Review review) {
        if (review == null) {
            return Mono.empty();
        }
        // Use Flux.just().concatWith(Mono.defer()...) for conditional additions
        // Or, more simply, use a List of Monos for zip
        List<Mono<?>> enrichmentMonos = new java.util.ArrayList<>();

        // Fetch Product if not already set
        if (review.getProduct() == null && review.getProductId() != null) {
            enrichmentMonos.add(productService.getProductById(review.getProductId())
                .doOnNext(review::setProduct)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch product {} for review {}: {}", review.getProductId(), review.getId(), e.getMessage());
                    review.setProduct(null); // Set to null if fetching fails
                    return Mono.empty(); // Continue with other enrichments
                })
            );
        }

        // Fetch User if not already set (assuming Review also has a User object to set)
        // This part needs to be adjusted based on your 'Review' model.
        // If your Review model has a 'User user' field that can be set, then this block is relevant.
        // Assuming your Review model has a 'User user' field and 'Long userId' field
        if (review.getUser() == null && review.getUserId() != null) {
             enrichmentMonos.add(userIntegrationService.getUserById(review.getUserId())
                 .doOnNext(review::setUser)
                 .onErrorResume(e -> {
                     log.warn("Failed to fetch user {} for review {}: {}", review.getUserId(), review.getId(), e.getMessage());
                     review.setUser(null); // Set to null if fetching fails
                     return Mono.empty();
                 })
             );
        }

        if (enrichmentMonos.isEmpty()) {
            return Mono.just(review);
        }

        return Mono.zip(enrichmentMonos, (Object[] array) -> review)
                   .defaultIfEmpty(review); // Return review even if zipping produces no results (e.g., all enrichment monos were empty/failed)
    }

    /**
     * Submits a new review for a product.
     * Validates product and user existence, checks for duplicate reviews, and rating range.
     *
     * @param reviewRequest The DTO containing review submission data.
     * @return A Mono emitting the submitted Review (enriched).
     * @throws ResourceNotFoundException if a product or user is not found.
     * @throws DuplicateResourceException if a review already exists for this user and product.
     * @throws InvalidReviewDataException if the rating is invalid.
     */
    public Mono<Review> submitReview(ReviewRequest reviewRequest) {
        log.info("Attempting to submit review for product {} by user {}", reviewRequest.getProductId(), reviewRequest.getUserId());

        // Validate rating first, as it's a direct input property
        if (reviewRequest.getRating() == null || reviewRequest.getRating() < 1 || reviewRequest.getRating() > 5) {
            log.warn("Invalid rating provided: {}", reviewRequest.getRating());
            return Mono.error(new InvalidReviewDataException(ApiResponseMessages.INVALID_REVIEW_RATING));
        }

        // Combine checks for product existence, user existence, and duplicate review
        Mono<Void> checks = Mono.when(
            productService.getProductById(reviewRequest.getProductId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + reviewRequest.getProductId())))
                .then(), // Just interested in existence, not the Product object here

            userIntegrationService.userExistsById(reviewRequest.getUserId())
                .filter(Boolean::booleanValue) // Only proceed if user exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + reviewRequest.getUserId())))
                .then(), // Just interested in existence

            reviewRepository.existsByUserIdAndProductId(reviewRequest.getUserId(), reviewRequest.getProductId())
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Duplicate review submission for user {} and product {}", reviewRequest.getUserId(), reviewRequest.getProductId());
                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_REVIEW_SUBMISSION));
                    }
                    return Mono.empty(); // No duplicate, continue
                })
        );

        return checks.thenReturn(Review.builder()
                        .productId(reviewRequest.getProductId())
                        .userId(reviewRequest.getUserId())
                        .rating(reviewRequest.getRating())
                        .comment(reviewRequest.getComment())
                        .createdAt(LocalDateTime.now())
                        .build())
                .flatMap(reviewRepository::save)
                .flatMap(this::prepareDto) // Enrich the created review
                .doOnSuccess(review -> log.info("Review submitted successfully with ID: {}", review.getId()))
                .doOnError(e -> log.error("Error submitting review: {}", e.getMessage(), e));
    }

    /**
     * Updates an existing review.
     * Finds the review by ID, updates its fields based on the request, and saves it.
     * Validates rating range.
     *
     * @param id The ID of the review to update.
     * @param updateRequest The DTO containing review update data.
     * @return A Mono emitting the updated Review (enriched).
     * @throws ResourceNotFoundException if the review is not found.
     * @throws InvalidReviewDataException if rating is invalid.
     */
    public Mono<Review> updateReview(Long id, ReviewRequest updateRequest) {
        log.info("Attempting to update review with ID: {}", id);
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.REVIEW_NOT_FOUND + id)))
                .flatMap(existingReview -> {
                    if (updateRequest.getRating() != null) {
                        if (updateRequest.getRating() < 1 || updateRequest.getRating() > 5) {
                            log.warn("Invalid rating provided for update of review {}: {}", id, updateRequest.getRating());
                            return Mono.error(new InvalidReviewDataException(ApiResponseMessages.INVALID_REVIEW_RATING));
                        }
                        existingReview.setRating(updateRequest.getRating());
                    }
                    if (updateRequest.getComment() != null && !updateRequest.getComment().isBlank()) {
                        existingReview.setComment(updateRequest.getComment());
                    }
                    // Update review date only if comment or rating is changed
                    if (updateRequest.getRating() != null || (updateRequest.getComment() != null && !updateRequest.getComment().isBlank())) {
                        existingReview.setCreatedAt(LocalDateTime.now());
                    }
                    existingReview.setUpdatedAt(LocalDateTime.now()); // Assuming 'updatedAt' field exists in Review model
                    return reviewRepository.save(existingReview);
                })
                .flatMap(this::prepareDto) // Enrich the updated review
                .doOnSuccess(review -> log.info("Review updated successfully with ID: {}", review.getId()))
                .doOnError(e -> log.error("Error updating review {}: {}", id, e.getMessage(), e));
    }

    /**
     * Deletes a review by its ID.
     *
     * @param id The ID of the review to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the review is not found.
     */
    public Mono<Void> deleteReview(Long id) {
        log.info("Attempting to delete review with ID: {}", id);
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.REVIEW_NOT_FOUND + id)))
                .flatMap(reviewRepository::delete)
                .doOnSuccess(v -> log.info("Review deleted successfully with ID: {}", id))
                .doOnError(e -> log.error("Error deleting review {}: {}", id, e.getMessage(), e));
    }

    /**
     * Retrieves a single review by its ID, enriching it.
     *
     * @param id The ID of the review to retrieve.
     * @return A Mono emitting the Review if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the review is not found.
     */
    public Mono<Review> getReviewById(Long id) {
        log.info("Retrieving review by ID: {}", id);
        return reviewRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.REVIEW_NOT_FOUND + id)))
                .flatMap(this::prepareDto) // Enrich the review
                .doOnSuccess(review -> log.info("Review retrieved successfully: {}", review.getId()))
                .doOnError(e -> log.error("Error retrieving review {}: {}", id, e.getMessage(), e));
    }

    // --- All Reviews ---

    /**
     * Retrieves all reviews with pagination, enriching each.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting all reviews (enriched).
     */
    public Flux<Review> getAllReviews(int page, int size) {
        log.info("Retrieving all reviews with page {} and size {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        return reviewRepository.findAllBy(pageable)
                .flatMap(this::prepareDto) // Enrich each review
                .doOnComplete(() -> log.info("Finished retrieving all reviews for page {} with size {}.", page, size))
                .doOnError(e -> log.error("Error retrieving all reviews: {}", e.getMessage(), e));
    }

    /**
     * Counts all reviews.
     *
     * @return A Mono emitting the total count of reviews.
     */
    public Mono<Long> countAllReviews() {
        log.info("Counting all reviews.");
        return reviewRepository.count()
                .doOnSuccess(count -> log.info("Total review count: {}", count))
                .doOnError(e -> log.error("Error counting all reviews: {}", e.getMessage(), e));
    }

    // --- Reviews by Product ---

    /**
     * Finds all reviews for a specific product with pagination, enriching each.
     *
     * @param productId The ID of the product.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting reviews for the specified product (enriched).
     * @throws ResourceNotFoundException if the product is not found.
     */
    public Flux<Review> getReviewsByProductId(Long productId, int page, int size) {
        log.info("Retrieving reviews for product ID {} with page {} and size {}", productId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return productService.getProductById(productId) // Ensure product exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMapMany(product -> reviewRepository.findByProductId(productId, pageable))
                .flatMap(this::prepareDto) // Enrich each review
                .doOnComplete(() -> log.info("Finished retrieving reviews for product ID {} for page {} with size {}.", productId, page, size))
                .doOnError(e -> log.error("Error retrieving reviews for product {}: {}", productId, e.getMessage(), e));
    }

    /**
     * Counts all reviews for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the total count of reviews for the product.
     * @throws ResourceNotFoundException if the product is not found.
     */
    public Mono<Long> countReviewsByProductId(Long productId) {
        log.info("Counting reviews for product ID {}", productId);
        return productService.getProductById(productId) // Ensure product exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMap(product -> reviewRepository.countByProductId(productId))
                .doOnSuccess(count -> log.info("Total review count for product {}: {}", productId, count))
                .doOnError(e -> log.error("Error counting reviews for product {}: {}", productId, e.getMessage(), e));
    }

    // --- Reviews by User ---

    /**
     * Finds all reviews left by a specific user with pagination, enriching each.
     *
     * @param userId The ID of the user.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting reviews by the specified user (enriched).
     * @throws ResourceNotFoundException if the user is not found.
     */
    public Flux<Review> getReviewsByUserId(Long userId, int page, int size) {
        log.info("Retrieving reviews for user ID {} with page {} and size {}", userId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return userIntegrationService.getUserById(userId) // Ensure user exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + userId)))
                .flatMapMany(user -> reviewRepository.findByUserId(userId, pageable))
                .flatMap(this::prepareDto) // Enrich each review
                .doOnComplete(() -> log.info("Finished retrieving reviews for user ID {} for page {} with size {}.", userId, page, size))
                .doOnError(e -> log.error("Error retrieving reviews for user {}: {}", userId, e.getMessage(), e));
    }

    /**
     * Counts all reviews left by a specific user.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the total count of reviews by the user.
     * @throws ResourceNotFoundException if the user is not found.
     */
    public Mono<Long> countReviewsByUserId(Long userId) {
        log.info("Counting reviews for user ID {}", userId);
        return userIntegrationService.getUserById(userId) // Ensure user exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + userId)))
                .flatMap(user -> reviewRepository.countByUserId(userId))
                .doOnSuccess(count -> log.info("Total review count for user {}: {}", userId, count))
                .doOnError(e -> log.error("Error counting reviews for user {}: {}", userId, e.getMessage(), e));
    }

    // --- Reviews by Product and Minimum Rating ---

    /**
     * Finds reviews for a product with a rating greater than or equal to a minimum value, with pagination, enriching each.
     *
     * @param productId The ID of the product.
     * @param minRating The minimum rating (inclusive).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting filtered reviews (enriched).
     * @throws InvalidReviewDataException if the minRating is invalid.
     * @throws ResourceNotFoundException if the product is not found.
     */
    public Flux<Review> getReviewsByProductIdAndMinRating(Long productId, Integer minRating, int page, int size) {
        log.info("Retrieving reviews for product ID {} with min rating {} and page {} and size {}", productId, minRating, page, size);
        if (minRating == null || minRating < 1 || minRating > 5) {
            log.warn("Invalid minRating provided: {}", minRating);
            return Flux.error(new InvalidReviewDataException(ApiResponseMessages.INVALID_REVIEW_RATING));
        }
        Pageable pageable = PageRequest.of(page, size);
        return productService.getProductById(productId) // Ensure product exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMapMany(product -> reviewRepository.findByProductIdAndRatingGreaterThanEqual(productId, minRating, pageable))
                .flatMap(this::prepareDto) // Enrich each review
                .doOnComplete(() -> log.info("Finished retrieving reviews for product ID {} with min rating {} for page {} with size {}.", productId, minRating, page, size))
                .doOnError(e -> log.error("Error retrieving reviews for product {} with min rating {}: {}", productId, minRating, e.getMessage(), e));
    }

    /**
     * Counts reviews for a product with a rating greater than or equal to a minimum value.
     *
     * @param productId The ID of the product.
     * @param minRating The minimum rating (inclusive).
     * @return A Mono emitting the count of filtered reviews.
     * @throws InvalidReviewDataException if the minRating is invalid.
     * @throws ResourceNotFoundException if the product is not found.
     */
    public Mono<Long> countReviewsByProductIdAndMinRating(Long productId, Integer minRating) {
        log.info("Counting reviews for product ID {} with min rating {}", productId, minRating);
        if (minRating == null || minRating < 1 || minRating > 5) {
            log.warn("Invalid minRating provided for count: {}", minRating);
            return Mono.error(new InvalidReviewDataException(ApiResponseMessages.INVALID_REVIEW_RATING));
        }
        return productService.getProductById(productId) // Ensure product exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMap(product -> reviewRepository.countByProductIdAndRatingGreaterThanEqual(productId, minRating))
                .doOnSuccess(count -> log.info("Total review count for product {} with min rating {}: {}", productId, minRating, count))
                .doOnError(e -> log.error("Error counting reviews for product {} with min rating {}: {}", productId, minRating, e.getMessage(), e));
    }

    // --- Latest Reviews for a Product ---

    /**
     * Finds the latest reviews for a product, ordered by review time descending, with pagination, enriching each.
     *
     * @param productId The ID of the product.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting latest reviews for the product (enriched).
     * @throws ResourceNotFoundException if the product is not found.
     */
    public Flux<Review> getLatestReviewsByProductId(Long productId, int page, int size) {
        log.info("Retrieving latest reviews for product ID {} with page {} and size {}", productId, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("reviewTime").descending()); // Explicitly sort by reviewTime descending
        return productService.getProductById(productId) // Ensure product exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMapMany(product -> reviewRepository.findByProductIdOrderByReviewTimeDesc(productId, pageable))
                .flatMap(this::prepareDto) // Enrich each review
                .doOnComplete(() -> log.info("Finished retrieving latest reviews for product ID {} for page {} with size {}.", productId, page, size))
                .doOnError(e -> log.error("Error retrieving latest reviews for product {}: {}", productId, e.getMessage(), e));
    }

    // No direct count for ordered queries needed, countByProductId already covers total count.

    // --- Single Review by User and Product ---

    /**
     * Finds a review by a specific user for a specific product, enriching it.
     *
     * @param userId The ID of the user.
     * @param productId The ID of the product.
     * @return A Mono emitting the Review if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the review is not found.
     */
    public Mono<Review> getReviewByUserIdAndProductId(Long userId, Long productId) {
        log.info("Retrieving review for user {} and product {}", userId, productId);
        return reviewRepository.findByUserIdAndProductId(userId, productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        ApiResponseMessages.REVIEW_NOT_FOUND + " for user " + userId + " and product " + productId)))
                .flatMap(this::prepareDto) // Enrich the review
                .doOnSuccess(review -> log.info("Review for user {} and product {} retrieved successfully.", userId, productId))
                .doOnError(e -> log.error("Error retrieving review for user {} and product {}: {}", userId, productId, e.getMessage(), e));
    }

    // --- Average Rating ---

    /**
     * Gets the average rating for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the average rating (Double), or 0.0 if no reviews exist.
     * @throws ResourceNotFoundException if the product is not found.
     */
    public Mono<Double> getAverageRatingForProduct(Long productId) {
        log.info("Calculating average rating for product ID {}", productId);
        return productService.getProductById(productId) // Ensure product exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMap(product -> reviewRepository.findAverageRatingByProductId(productId))
                .map(avg -> {
                    double average = (avg != null) ? avg : 0.0;
                    log.info("Average rating for product {}: {}", productId, average);
                    return average;
                })
                .doOnError(e -> log.error("Error getting average rating for product {}: {}", productId, e.getMessage(), e));
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
        log.info("Checking if review exists for user {} and product {}", userId, productId);
        return reviewRepository.existsByUserIdAndProductId(userId, productId)
                .doOnSuccess(exists -> log.info("Review for user {} and product {} exists: {}", userId, productId, exists))
                .doOnError(e -> log.error("Error checking review existence for user {} and product {}: {}", userId, productId, e.getMessage(), e));
    }
}
