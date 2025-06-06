package com.aliwudi.marketplace.backend.common.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
@Table("reviews")
public class Review {
    @Id
    private Long id;
    private Long userId;
    @Transient
    private User user;
    
    private Long productId; // required for db
    @Transient
    private Product product; // skip for db but required for response dto   
    private Integer rating; // Rating out of 5 (e.g., 1 to 5)
    private String comment;
    
    @CreatedDate // Automatically populated with creation timestamp
    private LocalDateTime createdAt;

    @LastModifiedDate // Automatically populated with last modification timestamp
    private LocalDateTime updatedAt;

    // Optional: Add a field for admin moderation status if needed (e.g., PENDING, APPROVED, REJECTED)
    // private ReviewStatus status;
}