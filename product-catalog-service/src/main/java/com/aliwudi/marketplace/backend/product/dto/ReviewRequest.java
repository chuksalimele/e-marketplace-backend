// src/main/java/com/marketplace/emarketplacebackend/dto/ReviewRequest.java
package com.aliwudi.marketplace.backend.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    private Long id;
    
    @NotNull(message = "Product ID is required")
    private Long productId;

    // In a real application, userId would come from authenticated context, not request body
    // For backend testing purposes, we'll keep it here for now.
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot be more than 5")
    private Integer rating;

    @Size(max = 1000, message = "Comment cannot exceed 1000 characters")
    private String comment;
}