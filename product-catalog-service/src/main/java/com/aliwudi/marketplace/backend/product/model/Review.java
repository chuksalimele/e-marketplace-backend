// src/main/java/com/marketplace/emarketplacebackend/model/Review.java
package com.marketplace.emarketplacebackend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "product_id"}) // A user can review a product only once
})
@Data
@NoArgsConstructor
@AllArgsConstructor // For convenience with Lombok, but often manually populate in service
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // The user who left the review

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product; // The product being reviewed

    @Column(nullable = false)
    private Integer rating; // Rating out of 5 (e.g., 1 to 5)

    @Column(columnDefinition = "TEXT") // Use TEXT for potentially longer comments
    private String comment;

    @Column(nullable = false)
    private LocalDateTime reviewDate;

    // Optional: Add a field for admin moderation status if needed (e.g., PENDING, APPROVED, REJECTED)
    // @Enumerated(EnumType.STRING)
    // private ReviewStatus status;

    @PrePersist
    protected void onCreate() {
        reviewDate = LocalDateTime.now();
    }
}