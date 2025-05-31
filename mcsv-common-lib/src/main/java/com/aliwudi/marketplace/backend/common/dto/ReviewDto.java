package com.aliwudi.marketplace.backend.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewDto {
    private Long id;
    private Long userId;
    private ProductDto product; // The product being reviewed
    private Integer rating; // Rating out of 5 (e.g., 1 to 5)
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Optional: Add a field for admin moderation status if needed (e.g., PENDING, APPROVED, REJECTED)
    // private ReviewStatus status;
}