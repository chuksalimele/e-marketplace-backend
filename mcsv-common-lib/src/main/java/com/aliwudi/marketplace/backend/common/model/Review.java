package com.aliwudi.marketplace.backend.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("reviews")
public class Review {
    @Id
    private Long id;
    private Long userId;
    private Long productId; // required for db
    private Product product; // skip for db but required for response dto   
    private Integer rating; // Rating out of 5 (e.g., 1 to 5)
    private String comment;
    private LocalDateTime reviewTime;

    // Optional: Add a field for admin moderation status if needed (e.g., PENDING, APPROVED, REJECTED)
    // private ReviewStatus status;
}