// src/main/java/com/marketplace/emarketplacebackend/dto/ReviewResponse.java
package com.aliwudi.marketplace.backend.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private Long productId;
    private String productName; // Include product name for context
    private Long userId;
    private String username; // Include username for context
    private Integer rating;
    private String comment;
    private LocalDateTime reviewDate;
}