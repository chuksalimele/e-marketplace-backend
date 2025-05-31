package com.aliwudi.marketplace.backend.product.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor // For convenience with Lombok, but often manually populate in service
@Builder
public class Review {

    @Id
    private Long id;
    private Long userId; // The user who left his/her review
    private Long productId; 
    private Integer rating; // Rating out of 5 (e.g., 1 to 5)
    private String comment;
    private LocalDateTime reviewTime;

    // Optional: Add a field for admin moderation status if needed (e.g., PENDING, APPROVED, REJECTED)
    // @Enumerated(EnumType.STRING)
    // private ReviewStatus status;

}