package com.aliwudi.marketplace.backend.common.dto;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor // For convenience with Lombok, but often manually populate in service
public class ReviewDto {
    private Long id;
    private Long userId;
    private ProductDto product; // The product being reviewed
    private Integer rating; // Rating out of 5 (e.g., 1 to 5)
    private String comment;
    private LocalDateTime reviewDate;

    // Optional: Add a field for admin moderation status if needed (e.g., PENDING, APPROVED, REJECTED)
    // private ReviewStatus status;
}