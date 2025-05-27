package com.aliwudi.marketplace.backend.lgtmed.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "media_assets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String assetName; // Original file name

    @Column(nullable = false, unique = true)
    private String uniqueFileName; // Stored file name / key in storage

    @Column(nullable = false)
    private String fileType; // e.g., image/jpeg, image/png

    @Column(nullable = false)
    private String url; // Publicly accessible URL for the asset

    private String entityId; // e.g., productId, userId, categoryId - foreign key to link asset to entity
    private String entityType; // e.g., "PRODUCT", "USER", "CATEGORY" - helps categorize assets

    @Column(nullable = false)
    private LocalDateTime uploadDate;
}