// src/main/java/com/marketplace/emarketplacebackend/controller/ReviewController.java
package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.dto.ReviewRequest;
import com.aliwudi.marketplace.backend.product.dto.ReviewResponse;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.model.Review;
import com.aliwudi.marketplace.backend.product.service.ReviewService;
import jakarta.validation.Valid; // For @Valid annotation
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @Autowired
    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // Helper method to convert Review entity to ReviewResponse DTO
    private ReviewResponse convertToDto(Review review) {
        ReviewResponse dto = new ReviewResponse();
        dto.setId(review.getId());
        dto.setProductId(review.getProduct().getId());
        dto.setProductName(review.getProduct().getName());
        dto.setUserId(review.getUser().getId());
        dto.setUsername(review.getUser().getUsername()); // Assuming username is public
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        dto.setReviewDate(review.getReviewDate());
        return dto;
    }

    @PostMapping
    public ResponseEntity<?> submitReview(@Valid @RequestBody ReviewRequest reviewRequest) {
        try {
            Review newReview = reviewService.submitReview(reviewRequest);
            return new ResponseEntity<>(convertToDto(newReview), HttpStatus.CREATED);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (DuplicateResourceException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);
        } catch (Exception e) {
            return new ResponseEntity<>("Error submitting review: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Get all reviews for a specific product
    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getReviewsForProduct(@PathVariable Long productId) {
        try {
            List<ReviewResponse> reviews = reviewService.getReviewsByProductId(productId).stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
            return new ResponseEntity<>(reviews, HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching reviews for product: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Get all reviews submitted by a specific user
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getReviewsByUser(@PathVariable Long userId) {
        try {
            List<ReviewResponse> reviews = reviewService.getReviewsByUserId(userId).stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
            return new ResponseEntity<>(reviews, HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching reviews by user: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Get average rating for a product
    @GetMapping("/product/{productId}/average-rating")
    public ResponseEntity<?> getAverageRating(@PathVariable Long productId) {
        try {
            Double averageRating = reviewService.getAverageRatingForProduct(productId);
            // Return average as a map for clear JSON response
            return new ResponseEntity<>(averageRating != null ? Map.of("averageRating", averageRating) : Map.of("averageRating", 0.0), HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching average rating: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Optional: Update a review (e.g., by the user who created it, or admin)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateReview(@PathVariable Long id, @Valid @RequestBody ReviewRequest updateRequest) {
        try {
            // Important: In a real app, you'd add security checks here to ensure
            // the user modifying the review is authorized (owner or admin).
            Review updatedReview = reviewService.updateReview(id, updateRequest);
            return new ResponseEntity<>(convertToDto(updatedReview), HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>("Error updating review: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Optional: Delete a review (e.g., by the user who created it, or admin)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable Long id) {
        try {
            // Important: Add security checks here for authorization
            reviewService.deleteReview(id);
            return new ResponseEntity<>("Review deleted successfully", HttpStatus.NO_CONTENT);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>("Error deleting review: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}