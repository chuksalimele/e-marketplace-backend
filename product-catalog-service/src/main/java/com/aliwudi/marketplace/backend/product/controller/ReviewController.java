package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.dto.ReviewRequest;
import com.aliwudi.marketplace.backend.product.dto.ReviewResponse;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.model.Review;
import com.aliwudi.marketplace.backend.product.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types
import reactor.core.publisher.Flux; // NEW: Import Flux for reactive collections

import java.util.List;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;
// Remove java.util.stream.Collectors as reactive operations handle collection

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @Autowired
    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // Helper method to convert Review entity to ReviewResponse DTO
    // This method will now accept a Mono<Review> and return a Mono<ReviewResponse>
    // or be called directly within a map operation on a Flux.
    private ReviewResponse convertToDto(Review review) {
        ReviewResponse dto = new ReviewResponse();
        dto.setId(review.getId());
        // Null check for product relationship, as it might not be eagerly fetched or present
        if (review.getProduct() != null) {
            dto.setProductId(review.getProduct().getId());
            dto.setProductName(review.getProduct().getName());
        }
        dto.setUserId(review.getUserId());
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        dto.setReviewDate(review.getReviewDate());
        return dto;
    }

    @PostMapping
    public Mono<ResponseEntity<ReviewResponse>> submitReview(@Valid @RequestBody ReviewRequest reviewRequest) {
        return reviewService.submitReview(reviewRequest)
                .map(this::convertToDto) // Convert Review entity to DTO
                .map(dto -> new ResponseEntity<>(dto, HttpStatus.CREATED)) // Wrap DTO in ResponseEntity
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.NOT_FOUND))) // Handle not found
                .onErrorResume(DuplicateResourceException.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.CONFLICT))) // Handle duplicate
                .onErrorResume(Exception.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error
    }

    // Get all reviews for a specific product
    @GetMapping("/product/{productId}")
    public Mono<ResponseEntity<List<ReviewResponse>>> getReviewsForProduct(@PathVariable Long productId,    
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {
        return reviewService.getReviewsByProductId(productId, offset, limit)
                .map(this::convertToDto) // Convert each Review entity to DTO
                .collectList() // Collect all DTOs into a List
                .map(reviews -> new ResponseEntity<>(reviews, HttpStatus.OK)) // Wrap List in ResponseEntity
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.NOT_FOUND))) // Handle not found
                .onErrorResume(Exception.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error
    }

    // Get all reviews submitted by a specific user
    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<List<ReviewResponse>>> getReviewsByUser(@PathVariable Long userId,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {
        return reviewService.getReviewsByUserId(userId, offset, limit)
                .map(this::convertToDto) // Convert each Review entity to DTO
                .collectList() // Collect all DTOs into a List
                .map(reviews -> new ResponseEntity<>(reviews, HttpStatus.OK)) // Wrap List in ResponseEntity
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.NOT_FOUND))) // Handle not found (if service throws it for user)
                .onErrorResume(Exception.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error
    }

    // Get average rating for a product
    @GetMapping("/product/{productId}/average-rating")
    public Mono<ResponseEntity<Map<String, Double>>> getAverageRating(@PathVariable Long productId) {
        return reviewService.getAverageRatingForProduct(productId)
                .map(averageRating ->
                    new ResponseEntity<>(Map.of("averageRating", averageRating != null ? averageRating : 0.0), HttpStatus.OK)
                )
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.NOT_FOUND))) // Handle not found
                .onErrorResume(Exception.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error
    }

    // Optional: Update a review
    @PutMapping("/{id}")
    public Mono<ResponseEntity<ReviewResponse>> updateReview(@PathVariable Long id, @Valid @RequestBody ReviewRequest updateRequest) {
        return reviewService.updateReview(id, updateRequest)
                .map(this::convertToDto) // Convert Review entity to DTO
                .map(dto -> new ResponseEntity<>(dto, HttpStatus.OK)) // Wrap DTO in ResponseEntity
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.NOT_FOUND))) // Handle not found
                .onErrorResume(Exception.class, e ->
                        Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error
    }

    // Optional: Delete a review
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // Indicate success with 204 No Content
    public Mono<Void> deleteReview(@PathVariable Long id) {
        return reviewService.deleteReview(id)
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()))) // Re-throw as WebFlux compatible exception
                .onErrorResume(Exception.class, e ->
                        Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting review: " + e.getMessage())));
    }
}